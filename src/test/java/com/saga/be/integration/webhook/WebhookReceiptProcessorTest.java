package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import com.saga.be.integration.sync.GitRepoStateService;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.WebhookReceiptRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class WebhookReceiptProcessorTest {

    private WebhookReceiptRepository receiptRepository;
    private IntegrationSecretCipher cipher;
    private AutomaticSyncDispatcher dispatcher;
    private IntegrationAvailability availability;
    private WebhookReceiptClaimService claimService;
    private WebhookReceiptStateService stateService;
    private WebhookReceiptProcessor processor;
    private JiraWebhookTaskDeleteService jiraTaskDeleteService;

    @BeforeEach
    void setUp() {
        receiptRepository = mock(WebhookReceiptRepository.class);
        cipher = mock(IntegrationSecretCipher.class);
        dispatcher = mock(AutomaticSyncDispatcher.class);
        availability = mock(IntegrationAvailability.class);
        claimService = mock(WebhookReceiptClaimService.class);
        stateService = mock(WebhookReceiptStateService.class);
        jiraTaskDeleteService = mock(JiraWebhookTaskDeleteService.class);
        when(availability.jiraEnabled()).thenReturn(true);
        when(availability.gitHubEnabled()).thenReturn(true);
        processor = new WebhookReceiptProcessor(
                receiptRepository,
                claimService,
                stateService,
                mock(GitHubInstallationRepository.class),
                mock(GitRepoRepository.class),
                cipher,
                JsonMapper.builder().build(),
                dispatcher,
                availability,
                mock(GitRepoStateService.class),
                jiraTaskDeleteService
        );
    }

    @Test
    void createdIssueWebhookUsesCanonicalReconciliation() {
        UUID receiptId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        when(claimService.claim(receiptId)).thenReturn(Optional.of(
                new WebhookReceiptClaim(
                        receiptId,
                        IntegrationProvider.JIRA,
                        boardId,
                        "created-delivery",
                        "jira:issue_created",
                        "ciphertext"
                )
        ));
        when(cipher.decrypt("ciphertext", "webhook:JIRA:created-delivery"))
                .thenReturn("{\"issue\":{\"id\":\"10001\"}}");

        processor.process(receiptId);

        verify(dispatcher).reconcileJira(boardId);
        verify(stateService).complete(receiptId);
        verifyNoInteractions(jiraTaskDeleteService);
    }

    @Test
    void deletedIssueWebhookTombstonesWithoutTryingToSearchForTheMissingIssue() {
        UUID receiptId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        when(claimService.claim(receiptId)).thenReturn(Optional.of(
                new WebhookReceiptClaim(
                        receiptId,
                        IntegrationProvider.JIRA,
                        boardId,
                        "deleted-delivery",
                        "jira:issue_deleted",
                        "ciphertext"
                )
        ));
        when(cipher.decrypt("ciphertext", "webhook:JIRA:deleted-delivery"))
                .thenReturn("{\"issue\":{\"id\":\"10001\",\"key\":\"SAGA-1\"}}");
        when(jiraTaskDeleteService.tombstone(boardId, "10001", "SAGA-1"))
                .thenReturn(JiraWebhookTaskDeleteService.DeleteResult.TOMBSTONED);

        processor.process(receiptId);

        verify(jiraTaskDeleteService).tombstone(boardId, "10001", "SAGA-1");
        verify(dispatcher, never()).reconcileJira(any());
        verify(stateService).complete(receiptId);
    }

    @Test
    void staleProcessingReceiptIsRecoveredAfterWorkerRestart() {
        UUID receiptId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        WebhookReceipt receipt = jiraReceipt(receiptId, boardId);
        receipt.setReceiptStatus(WebhookReceiptStatus.PROCESSING);
        receipt.setAttemptCount(1);
        receipt.setUpdatedAt(LocalDateTime.now().minusMinutes(6));
        when(receiptRepository.findTop100IdsByReceiptStatusInOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(receiptId));
        when(claimService.recoverStaleProcessing(any(), any())).thenReturn(true);
        when(claimService.claim(receiptId)).thenReturn(Optional.of(new WebhookReceiptClaim(receiptId, IntegrationProvider.JIRA, boardId, "delivery", "jira:issue_updated", "ciphertext")));
        when(cipher.decrypt("ciphertext", "webhook:JIRA:delivery"))
                .thenReturn("{}");

        processor.retryIncomplete();

        verify(stateService).complete(receiptId);
        verify(dispatcher).reconcileJira(boardId);
    }

    @Test
    void activeProcessingLeaseIsNotClaimedAgain() {
        WebhookReceipt receipt = jiraReceipt(
                UUID.randomUUID(),
                UUID.randomUUID()
        );
        receipt.setReceiptStatus(WebhookReceiptStatus.PROCESSING);
        receipt.setUpdatedAt(LocalDateTime.now());
        when(receiptRepository.findTop100IdsByReceiptStatusInOrderByCreatedAtAsc(any()))
                .thenReturn(List.of(receipt.getId()));
        when(claimService.recoverStaleProcessing(any(), any())).thenReturn(false);

        processor.retryIncomplete();

        verify(claimService).claim(receipt.getId());
        assertEquals(
                WebhookReceiptStatus.PROCESSING,
                receipt.getReceiptStatus()
        );
    }

    private WebhookReceipt jiraReceipt(UUID receiptId, UUID boardId) {
        WebhookReceipt receipt = WebhookReceipt.builder()
                .provider(IntegrationProvider.JIRA)
                .deliveryId("delivery")
                .eventType("jira:issue_updated")
                .targetId(boardId)
                .payloadCiphertext("ciphertext")
                .receiptStatus(WebhookReceiptStatus.RECEIVED)
                .attemptCount(0)
                .build();
        receipt.setId(receiptId);
        receipt.setCreatedAt(LocalDateTime.now().minusMinutes(10));
        return receipt;
    }
}
