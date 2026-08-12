package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.WebhookReceipt;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import com.saga.be.service.AuthenticationAuditService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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

    @Test
    void authenticatedJiraDeleteIsPersistedWithBoardAndEventBeforeAsyncProcessing() {
        JiraBoard board = new JiraBoard();
        UUID boardId = UUID.randomUUID();
        board.setId(boardId);
        when(jiraAuthenticator.authenticate("Bearer valid", "connection-token"))
                .thenReturn(board);
        when(receiptRepository.findByProviderAndDeliveryId(any(), any()))
                .thenReturn(Optional.empty());
        when(cipher.encrypt(any(), any())).thenReturn("ciphertext");
        when(receiptRepository.saveAndFlush(any(WebhookReceipt.class)))
                .thenAnswer(invocation -> {
                    WebhookReceipt receipt = invocation.getArgument(0);
                    receipt.setId(UUID.randomUUID());
                    return receipt;
                });
        WebhookIngestionService realParserService = new WebhookIngestionService(
                gitHubVerifier,
                jiraAuthenticator,
                JsonMapper.builder().build(),
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher,
                auditService,
                availability
        );
        byte[] payload = "{\"webhookEvent\":\"jira:issue_deleted\","
                .concat("\"issue\":{\"id\":\"10001\",\"key\":\"SAGA-1\"}}")
                .getBytes();

        assertEquals("ACCEPTED", realParserService.receiveJira(
                payload,
                "Bearer valid",
                "connection-token",
                "webhook-id",
                "127.0.0.1"
        ).status());

        ArgumentCaptor<WebhookReceipt> receipt = ArgumentCaptor.forClass(WebhookReceipt.class);
        verify(receiptRepository).saveAndFlush(receipt.capture());
        assertEquals(boardId, receipt.getValue().getTargetId());
        assertEquals("jira:issue_deleted", receipt.getValue().getEventType());
        verify(eventPublisher).publishEvent(any(WebhookReceiptCreated.class));
    }

    @Test
    void duplicateAuthenticatedJiraDeliveryDoesNotPublishWorkAgain() {
        JiraBoard board = new JiraBoard();
        board.setId(UUID.randomUUID());
        when(jiraAuthenticator.authenticate("Bearer valid", "connection-token"))
                .thenReturn(board);
        when(receiptRepository.findByProviderAndDeliveryId(any(), any()))
                .thenReturn(Optional.of(new WebhookReceipt()));
        WebhookIngestionService realParserService = new WebhookIngestionService(
                gitHubVerifier,
                jiraAuthenticator,
                JsonMapper.builder().build(),
                gitRepoRepository,
                receiptRepository,
                cipher,
                eventPublisher,
                auditService,
                availability
        );

        assertEquals("DUPLICATE", realParserService.receiveJira(
                "{\"webhookEvent\":\"jira:issue_deleted\",\"issue\":{\"id\":\"10001\"}}".getBytes(),
                "Bearer valid",
                "connection-token",
                "webhook-id",
                "127.0.0.1"
        ).status());

        verify(receiptRepository, never()).saveAndFlush(any());
        verifyNoInteractions(cipher, eventPublisher);
    }
}
