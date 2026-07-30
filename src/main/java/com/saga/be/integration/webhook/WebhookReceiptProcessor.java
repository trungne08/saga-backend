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
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class WebhookReceiptProcessor {

    private static final int MAX_ATTEMPTS = 5;
    private static final long PROCESSING_LEASE_MINUTES = 5;

    private final WebhookReceiptRepository receiptRepository;
    private final GitHubInstallationRepository installationRepository;
    private final GitRepoRepository gitRepoRepository;
    private final IntegrationSecretCipher cipher;
    private final ObjectMapper objectMapper;
    private final AutomaticSyncDispatcher dispatcher;
    private final IntegrationAvailability availability;

    public WebhookReceiptProcessor(
            WebhookReceiptRepository receiptRepository,
            GitHubInstallationRepository installationRepository,
            GitRepoRepository gitRepoRepository,
            IntegrationSecretCipher cipher,
            ObjectMapper objectMapper,
            AutomaticSyncDispatcher dispatcher,
            IntegrationAvailability availability
    ) {
        this.receiptRepository = receiptRepository;
        this.installationRepository = installationRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
        this.availability = availability;
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
        for (WebhookReceipt receipt : receiptRepository
                .findTop100ByReceiptStatusInOrderByCreatedAtAsc(List.of(
                        WebhookReceiptStatus.RECEIVED,
                        WebhookReceiptStatus.FAILED,
                        WebhookReceiptStatus.PROCESSING
                ))) {
            try {
                if (receipt.getReceiptStatus()
                        == WebhookReceiptStatus.PROCESSING) {
                    if (!processingLeaseExpired(receipt)) {
                        continue;
                    }
                    receipt.setReceiptStatus(WebhookReceiptStatus.FAILED);
                    receipt.setErrorCategory("WORKER_INTERRUPTED");
                    receiptRepository.saveAndFlush(receipt);
                }
                if (receipt.getAttemptCount() < MAX_ATTEMPTS) {
                    process(receipt.getId());
                }
            } catch (RuntimeException ignored) {
                // Another node may have claimed the optimistic-lock version.
                // The durable row remains eligible for a later retry.
            }
        }
    }

    public void process(UUID receiptId) {
        WebhookReceipt receipt = receiptRepository.findById(receiptId)
                .orElse(null);
        if (
            receipt == null
            || receipt.getReceiptStatus() == WebhookReceiptStatus.COMPLETED
            || receipt.getReceiptStatus() == WebhookReceiptStatus.PROCESSING
            || (receipt.getProvider() == IntegrationProvider.JIRA
                    && !availability.jiraEnabled())
            || (receipt.getProvider() == IntegrationProvider.GITHUB
                    && !availability.gitHubEnabled())
        ) {
            return;
        }
        receipt.setReceiptStatus(WebhookReceiptStatus.PROCESSING);
        receipt.setAttemptCount(receipt.getAttemptCount() + 1);
        receiptRepository.saveAndFlush(receipt);
        try {
            String plaintext = cipher.decrypt(
                    receipt.getPayloadCiphertext(),
                    "webhook:"
                            + receipt.getProvider()
                            + ":"
                            + receipt.getDeliveryId()
            );
            JsonNode payload = objectMapper.readTree(plaintext);
            if (receipt.getProvider() == IntegrationProvider.JIRA) {
                if (receipt.getTargetId() != null) {
                    dispatcher.reconcileJira(receipt.getTargetId());
                }
            } else {
                routeGitHub(receipt, payload);
            }
            receipt.setPayloadCiphertext("");
            receipt.setReceiptStatus(WebhookReceiptStatus.COMPLETED);
            receipt.setProcessedAt(LocalDateTime.now());
            receipt.setErrorCategory(null);
            receiptRepository.saveAndFlush(receipt);
        } catch (Exception exception) {
            receipt.setReceiptStatus(WebhookReceiptStatus.FAILED);
            receipt.setErrorCategory("WEBHOOK_PROCESSING_FAILED");
            receiptRepository.saveAndFlush(receipt);
        }
    }

    private void routeGitHub(WebhookReceipt receipt, JsonNode payload) {
        if ("installation".equals(receipt.getEventType())) {
            updateInstallation(payload);
        } else if ("installation_repositories".equals(receipt.getEventType())) {
            updateRepositoryAccess(payload);
        }
        if (receipt.getTargetId() != null) {
            dispatcher.reconcileGitHub(receipt.getTargetId());
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
                .ifPresent(linked -> {
                    linked.setConnectionStatus(IntegrationStatus.DEGRADED);
                    gitRepoRepository.saveAndFlush(linked);
                }));
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
                repository.setConnectionStatus(IntegrationStatus.DEGRADED);
                gitRepoRepository.saveAndFlush(repository);
            }
        }
    }

    private boolean processingLeaseExpired(WebhookReceipt receipt) {
        LocalDateTime lastActivity = receipt.getUpdatedAt() == null
                ? receipt.getCreatedAt()
                : receipt.getUpdatedAt();
        return lastActivity == null
                || lastActivity.isBefore(
                        LocalDateTime.now().minusMinutes(
                                PROCESSING_LEASE_MINUTES
                        )
                );
    }
}
