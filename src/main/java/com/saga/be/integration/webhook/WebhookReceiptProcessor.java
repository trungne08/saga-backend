package com.saga.be.integration.webhook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.WebhookReceipt;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import com.saga.be.integration.sync.GitRepoStateService;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class WebhookReceiptProcessor {

    private static final int MAX_ATTEMPTS = 5;
    private static final long PROCESSING_LEASE_MINUTES = 5;
    private static final Logger log = LoggerFactory.getLogger(WebhookReceiptProcessor.class);

    private final WebhookReceiptRepository receiptRepository;
    private final WebhookReceiptClaimService claimService;
    private final WebhookReceiptStateService stateService;
    private final GitHubInstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final IntegrationSecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final AutomaticSyncDispatcher dispatcher;
    private final IntegrationAvailability availability;
    private final GitRepoStateService gitRepoStateService;

    public WebhookReceiptProcessor(
            WebhookReceiptRepository receiptRepository,
            WebhookReceiptClaimService claimService,
            WebhookReceiptStateService stateService,
            GitHubInstallationRepository installationRepository,
            GitRepoRepository gitRepoRepository,
            IntegrationSecretCipher cipher,
            ObjectMapper objectMapper,
            AutomaticSyncDispatcher dispatcher,
            IntegrationAvailability availability,
            GitRepoStateService gitRepoStateService
    ) {
        this.receiptRepository = receiptRepository;
        this.claimService = claimService;
        this.stateService = stateService;
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.availability = availability;
        this.gitRepoStateService = gitRepoStateService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReceipt(WebhookReceiptCreated event) {
        if (!availability.jiraEnabled() && !availability.gitHubEnabled()) {
            return;
        }
        process(event.receiptId());
    }

    @Scheduled(fixedDelayString = "60000")
    public void retryIncomplete() {
        if (!availability.jiraEnabled() && !availability.gitHubEnabled()) {
            return;
        }
        for (UUID receiptId : receiptRepository
                .findTop100IdsByReceiptStatusInOrderByCreatedAtAsc(List.of(
                        WebhookReceiptStatus.RECEIVED,
                        WebhookReceiptStatus.FAILED,
                        WebhookReceiptStatus.PROCESSING
                ))) {
            try {
                claimService.recoverStaleProcessing(receiptId, LocalDateTime.now().minusMinutes(PROCESSING_LEASE_MINUTES));
                process(receiptId);
            } catch (RuntimeException exception) {
                logFailure(receiptId, null, null, "SCHEDULE_RETRY", exception);
            }
        }
    }

    public void process(UUID receiptId) {
        WebhookReceiptClaim receipt = claimService.claim(receiptId).orElse(null);
        if (receipt == null || (receipt.provider() == IntegrationProvider.JIRA
                    && !availability.jiraEnabled())
            || (receipt.provider() == IntegrationProvider.GITHUB
                    && !availability.gitHubEnabled())
        ) {
            return;
        }
        try {
            String plaintext = cipher.decrypt(
                    receipt.payloadCiphertext(),
                    "webhook:"
                            + receipt.provider()
                            + ":"
                            + receipt.deliveryId()
            );
            JsonNode payload = objectMapper.readTree(plaintext);
            if (receipt.provider() == IntegrationProvider.JIRA) {
                if (receipt.targetId() != null) {
                    dispatcher.reconcileJira(receipt.targetId());
                }
            } else {
                routeGitHub(receipt, payload);
            }
            stateService.complete(receiptId);
        } catch (Exception exception) {
            stateService.fail(receiptId, "WEBHOOK_PROCESSING_FAILED");
            logFailure(receiptId, receipt.provider(), receipt.deliveryId(), "PROCESS", exception);
        }
    }

    private void routeGitHub(WebhookReceiptClaim receipt, JsonNode payload) {
        if ("installation".equals(receipt.eventType())) {
            updateInstallation(payload);
        } else if ("installation_repositories".equals(receipt.eventType())) {
            updateRepositoryAccess(payload);
        }
        if (receipt.targetId() != null) {
            dispatcher.reconcileGitHub(receipt.targetId());
        }
    }

    private void updateInstallation(JsonNode payload) {
        long installationId = payload.path("installation").path("id").asLong();
        GitHubInstallation installation = installationRepository
                .findByInstallationId(installationId)
                .orElse(null);
        if (installation == null) {
            return;
        }
        String action = payload.path("action").asText();
        if ("deleted".equals(action)) {
            installation.setInstallationStatus(GitHubInstallationStatus.DELETED);
        } else if ("suspend".equals(action)) {
            installation.setInstallationStatus(
                    GitHubInstallationStatus.SUSPENDED
            );
        } else if ("unsuspend".equals(action)) {
            installation.setInstallationStatus(GitHubInstallationStatus.ACTIVE);
        }
        installationRepository.saveAndFlush(installation);
        if (installation.getInstallationStatus()
                != GitHubInstallationStatus.ACTIVE) {
            markAllRepositoriesDegraded(installationId);
        }
    }

    private void updateRepositoryAccess(JsonNode payload) {
        long installationId = payload.path("installation").path("id").asLong();
        JsonNode removed = payload.path("repositories_removed");
        if (!removed.isArray()) {
            return;
        }
        removed.forEach(repository -> gitRepoRepository
                .findByRepositoryId(repository.path("id").asLong())
                .ifPresent(linked -> gitRepoStateService.markDegraded(
                        linked.getId()
                )));
        for (GitRepo linked : gitRepoRepository
                .findByInstallationInstallationId(installationId)) {
            if (linked.getConnectionStatus() != IntegrationStatus.DISCONNECTED) {
                dispatcher.reconcileGitHub(linked.getId());
            }
        }
    }

    private void markAllRepositoriesDegraded(long installationId) {
        for (GitRepo repository : gitRepoRepository
                .findByInstallationInstallationId(installationId)) {
            if (repository.getConnectionStatus()
                    != IntegrationStatus.DISCONNECTED) {
                gitRepoStateService.markDegraded(repository.getId());
            }
        }
    }

    private void logFailure(UUID receiptId, IntegrationProvider provider, String deliveryId,
            String stage, Exception exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        log.error("Webhook receipt failed: receiptId={}, provider={}, deliveryId={}, stage={}, exceptionClass={}, rootCauseClass={}",
                receiptId, provider, deliveryId == null ? null : Integer.toHexString(deliveryId.hashCode()), stage,
                exception.getClass().getName(), root.getClass().getName(), exception);
    }
}
