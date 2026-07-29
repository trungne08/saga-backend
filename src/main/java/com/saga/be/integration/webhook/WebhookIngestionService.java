package com.saga.be.integration.webhook;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saga.be.dto.response.WebhookAcceptedResponse;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import com.saga.be.service.AuthenticationAuditService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookIngestionService {

    private static final Set<String> GITHUB_EVENTS = Set.of(
            "installation",
            "installation_repositories",
            "push",
            "issues",
            "issue_comment",
            "pull_request",
            "pull_request_review",
            "pull_request_review_comment"
    );

    private final GitHubWebhookSignatureVerifier gitHubVerifier;
    private final JiraWebhookAuthenticator jiraAuthenticator;
    private final ObjectMapper objectMapper;
    private final GitRepoRepository gitRepoRepository;
    private final WebhookReceiptRepository receiptRepository;
    private final IntegrationSecretCipher cipher;
    private final ApplicationEventPublisher eventPublisher;
    private final AuthenticationAuditService auditService;

    public WebhookIngestionService(
            GitHubWebhookSignatureVerifier gitHubVerifier,
            JiraWebhookAuthenticator jiraAuthenticator,
            ObjectMapper objectMapper,
            GitRepoRepository gitRepoRepository,
            WebhookReceiptRepository receiptRepository,
            IntegrationSecretCipher cipher,
            ApplicationEventPublisher eventPublisher,
            AuthenticationAuditService auditService
    ) {
        this.gitHubVerifier = gitHubVerifier;
        this.jiraAuthenticator = jiraAuthenticator;
        this.objectMapper = objectMapper;
        this.gitRepoRepository = gitRepoRepository;
        this.receiptRepository = receiptRepository;
        this.cipher = cipher;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    @Transactional
    public WebhookAcceptedResponse receiveGitHub(
            byte[] payload,
            String signature,
            String delivery,
            String event,
            String remoteAddress
    ) {
        try {
            gitHubVerifier.verify(payload, signature);
            requireHeader(delivery, "GITHUB_DELIVERY_INVALID");
            requireHeader(event, "GITHUB_EVENT_INVALID");
            if (!GITHUB_EVENTS.contains(event)) {
                throw IntegrationException.invalid(
                        "GITHUB_EVENT_UNSUPPORTED",
                        "The GitHub webhook event is not supported"
                );
            }
            JsonNode root = parse(payload);
            UUID targetId = repositoryTarget(root);
            return persist(
                    IntegrationProvider.GITHUB,
                    delivery,
                    event,
                    targetId,
                    payload
            );
        } catch (IntegrationException exception) {
            auditService.recordIntegrationEvent(
                    "WEBHOOK",
                    "GITHUB_WEBHOOK_REJECTED",
                    "WEBHOOK",
                    null,
                    exception.getCode(),
                    remoteAddress
            );
            throw exception;
        }
    }

    @Transactional
    public WebhookAcceptedResponse receiveJira(
            byte[] payload,
            String authorizationHeader,
            String token,
            String webhookIdentifier,
            String remoteAddress
    ) {
        try {
            JiraBoard board = jiraAuthenticator.authenticate(
                    authorizationHeader,
                    token
            );
            JsonNode root = parse(payload);
            String event = safeJiraEvent(root);
            String delivery = sha256(
                    (webhookIdentifier == null ? "" : webhookIdentifier)
                            .getBytes(StandardCharsets.UTF_8),
                    payload
            );
            return persist(
                    IntegrationProvider.JIRA,
                    delivery,
                    event,
                    board.getId(),
                    payload
            );
        } catch (IntegrationException exception) {
            auditService.recordIntegrationEvent(
                    "WEBHOOK",
                    "JIRA_WEBHOOK_REJECTED",
                    "WEBHOOK",
                    null,
                    exception.getCode(),
                    remoteAddress
            );
            throw exception;
        }
    }

    private WebhookAcceptedResponse persist(
            IntegrationProvider provider,
            String delivery,
            String event,
            UUID targetId,
            byte[] payload
    ) {
        if (receiptRepository.findByProviderAndDeliveryId(provider, delivery)
                .isPresent()) {
            return new WebhookAcceptedResponse("DUPLICATE");
        }
        WebhookReceipt receipt = WebhookReceipt.builder()
                .provider(provider)
                .deliveryId(delivery)
                .eventType(event)
                .targetId(targetId)
                .payloadCiphertext(cipher.encrypt(
                        new String(payload, StandardCharsets.UTF_8),
                        "webhook:" + provider + ":" + delivery
                ))
                .receiptStatus(WebhookReceiptStatus.RECEIVED)
                .attemptCount(0)
                .build();
        try {
            receipt = receiptRepository.saveAndFlush(receipt);
        } catch (DataIntegrityViolationException exception) {
            return new WebhookAcceptedResponse("DUPLICATE");
        }
        eventPublisher.publishEvent(new WebhookReceiptCreated(receipt.getId()));
        return new WebhookAcceptedResponse("ACCEPTED");
    }

    private UUID repositoryTarget(JsonNode root) {
        JsonNode repository = root.path("repository");
        if (!repository.isObject() || !repository.path("id").canConvertToLong()) {
            return null;
        }
        GitRepo linked = gitRepoRepository
                .findByRepositoryId(repository.path("id").asLong())
                .orElse(null);
        return linked == null ? null : linked.getId();
    }

    private JsonNode parse(byte[] payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException();
            }
            return root;
        } catch (Exception exception) {
            throw IntegrationException.invalid(
                    "WEBHOOK_PAYLOAD_INVALID",
                    "The webhook payload is invalid"
            );
        }
    }

    private String safeJiraEvent(JsonNode root) {
        String event = root.path("webhookEvent").asText();
        if (
            event.isBlank()
            || event.length() > 128
            || !event.matches("[A-Za-z0-9:_-]+")
        ) {
            throw IntegrationException.invalid(
                    "JIRA_EVENT_INVALID",
                    "The Jira webhook event is invalid"
            );
        }
        return event;
    }

    private void requireHeader(String value, String code) {
        if (
            value == null
            || value.isBlank()
            || value.length() > 128
            || !value.matches("[A-Za-z0-9_.:-]+")
        ) {
            throw IntegrationException.invalid(
                    code,
                    "A required webhook header is invalid"
            );
        }
    }

    private String sha256(byte[] prefix, byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(prefix);
            digest.update((byte) 0);
            digest.update(payload);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
