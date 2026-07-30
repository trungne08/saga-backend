package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import com.saga.be.service.AuthenticationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

class WebhookIngestionServiceTest {

    private GitHubWebhookSignatureVerifier gitHubVerifier;
    private JiraWebhookAuthenticator jiraAuthenticator;
    private ObjectMapper objectMapper;
    private GitRepoRepository gitRepoRepository;
    private WebhookReceiptRepository receiptRepository;
    private IntegrationSecretCipher cipher;
    private ApplicationEventPublisher eventPublisher;
    private IntegrationAvailability availability;
    private AuthenticationAuditService auditService;
    private WebhookIngestionService service;

    @BeforeEach
    void setUp() {
        gitHubVerifier = mock(GitHubWebhookSignatureVerifier.class);
        jiraAuthenticator = mock(JiraWebhookAuthenticator.class);
        objectMapper = mock(ObjectMapper.class);
        gitRepoRepository = mock(GitRepoRepository.class);
        receiptRepository = mock(WebhookReceiptRepository.class);
        cipher = mock(IntegrationSecretCipher.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        availability = mock(IntegrationAvailability.class);
        auditService = mock(AuthenticationAuditService.class);
        when(availability.jiraEnabled()).thenReturn(true);
        when(availability.gitHubEnabled()).thenReturn(true);
        service = new WebhookIngestionService(
                gitHubVerifier,
                jiraAuthenticator,
                objectMapper,
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher,
                auditService,
                availability
        );
    }

    @Test
    void githubPingAfterValidSignatureReturnsWithoutParsingPersistenceOrSyncWork() {
        byte[] rawPayload = "{\"zen\":\"Keep it logically awesome.\"}".getBytes();

        var response = service.receiveGitHub(
                rawPayload,
                "sha256=valid",
                "ping-delivery",
                "ping",
                "127.0.0.1"
        );

        assertEquals("PING", response.status());
        verify(gitHubVerifier).verify(rawPayload, "sha256=valid");
        verifyNoInteractions(
                objectMapper,
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher,
                auditService
        );
    }

    @Test
    void githubPingWithRepeatedDeliveryRemainsSuccessfulWithoutDurableWork() {
        byte[] rawPayload = "{}".getBytes();

        assertEquals("PING", service.receiveGitHub(
                rawPayload, "sha256=valid", "same-delivery", "ping", "127.0.0.1").status());
        assertEquals("PING", service.receiveGitHub(
                rawPayload, "sha256=valid", "same-delivery", "ping", "127.0.0.1").status());

        verifyNoInteractions(receiptRepository, eventPublisher, cipher, gitRepoRepository, objectMapper);
    }

    @Test
    void githubPingRejectsBadSignatureBeforeAnyFurtherWork() {
        byte[] rawPayload = "{}".getBytes();
        doThrow(IntegrationException.forbidden("invalid"))
                .when(gitHubVerifier)
                .verify(rawPayload, "sha256=invalid");

        assertThrows(
                IntegrationException.class,
                () -> service.receiveGitHub(
                        rawPayload,
                        "sha256=invalid",
                        "ping-delivery",
                        "ping",
                        "127.0.0.1"
                )
        );

        verifyNoInteractions(
                objectMapper,
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher
        );
    }

    @Test
    void githubStillRejectsUnknownNonPingEvents() {
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.receiveGitHub(
                        "{}".getBytes(),
                        "sha256=valid",
                        "delivery",
                        "fork",
                        "127.0.0.1"
                )
        );

        assertEquals("GITHUB_EVENT_UNSUPPORTED", exception.getCode());
        verifyNoInteractions(objectMapper, gitRepoRepository, receiptRepository, cipher, eventPublisher);
    }

    @Test
    void githubRejectsBadSignatureBeforeParsingOrDurablePersistence() {
        byte[] rawPayload = "{\"repository\":{\"id\":1}}".getBytes();
        doThrow(IntegrationException.forbidden("invalid"))
                .when(gitHubVerifier)
                .verify(rawPayload, "sha256=invalid");

        assertThrows(
                IntegrationException.class,
                () -> service.receiveGitHub(
                        rawPayload,
                        "sha256=invalid",
                        "delivery",
                        "push",
                        "127.0.0.1"
                )
        );

        verifyNoInteractions(
                objectMapper,
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher
        );
    }

    @Test
    void jiraRejectsBadAuthorizationBeforeParsingOrPersistence() {
        byte[] rawPayload = "{\"webhookEvent\":\"jira:issue_updated\"}"
                .getBytes();
        doThrow(IntegrationException.forbidden("invalid"))
                .when(jiraAuthenticator)
                .authenticate("Bearer invalid", "connection-token");

        assertThrows(
                IntegrationException.class,
                () -> service.receiveJira(
                        rawPayload,
                        "Bearer invalid",
                        "connection-token",
                        "webhook-id",
                        "127.0.0.1"
                )
        );

        verifyNoInteractions(
                objectMapper,
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher
        );
    }
}
