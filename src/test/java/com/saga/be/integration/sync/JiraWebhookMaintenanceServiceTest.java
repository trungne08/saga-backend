package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationUrlResolver;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWebhookRegistration;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JiraWebhookMaintenanceServiceTest {

    private JiraBoardRepository boardRepository;
    private JiraCredentialService credentialService;
    private JiraProviderClient jiraClient;
    private SyncJobLogRepository jobRepository;
    private JiraWebhookMaintenanceService service;
    private JiraBoard board;

    @BeforeEach
    void setUp() {
        boardRepository = mock(JiraBoardRepository.class);
        credentialService = mock(JiraCredentialService.class);
        jiraClient = mock(JiraProviderClient.class);
        jobRepository = mock(SyncJobLogRepository.class);
        IntegrationUrlResolver urlResolver = mock(IntegrationUrlResolver.class);
        when(urlResolver.jiraWebhookPublicUrl())
                .thenReturn("https://saga.example/api/webhooks/jira");
        service = new JiraWebhookMaintenanceService(
                boardRepository,
                credentialService,
                jiraClient,
                urlResolver,
                jobRepository
        );
        board = JiraBoard.builder()
                .cloudId("cloud")
                .projectKey("SAGA")
                .connectionStatus(IntegrationStatus.ACTIVE)
                .webhookId("123")
                .build();
        board.setId(UUID.randomUUID());
        when(boardRepository.findById(board.getId())).thenReturn(Optional.of(board));
        when(credentialService.validAccessToken(board)).thenReturn("ACCESS_TOKEN_SECRET");
        when(jobRepository.saveAndFlush(any(SyncJobLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void successfulMaintenancePersistsSafeCompletedDiagnostic() {
        when(jiraClient.refreshWebhook(
                eq("ACCESS_TOKEN_SECRET"),
                eq("cloud"),
                eq("SAGA"),
                any(URI.class),
                eq("123")
        )).thenReturn(new JiraWebhookRegistration("123", false));

        service.refresh(board.getId());

        ArgumentCaptor<SyncJobLog> job = ArgumentCaptor.forClass(SyncJobLog.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertEquals("JIRA", job.getValue().getTargetSystem());
        assertEquals(board.getId(), job.getValue().getTargetId());
        assertEquals(SyncJobType.OTHER, job.getValue().getJobType());
        assertEquals(SyncJobStatus.COMPLETED, job.getValue().getStatus());
        assertEquals("WEBHOOK_MAINTENANCE", job.getValue().getFailureStage());
        assertNull(job.getValue().getErrorCategory());
        assertNull(job.getValue().getErrorMessage());
    }

    @Test
    void failedMaintenancePersistsOnlySafeErrorCategory() {
        when(jiraClient.refreshWebhook(
                eq("ACCESS_TOKEN_SECRET"),
                eq("cloud"),
                eq("SAGA"),
                any(URI.class),
                eq("123")
        )).thenThrow(IntegrationException.unavailable("JIRA_WEBHOOK_PROVIDER_UNAVAILABLE"));

        service.refresh(board.getId());

        ArgumentCaptor<SyncJobLog> job = ArgumentCaptor.forClass(SyncJobLog.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertEquals(SyncJobStatus.FAILED, job.getValue().getStatus());
        assertEquals("JIRA_WEBHOOK_PROVIDER_UNAVAILABLE", job.getValue().getErrorCategory());
        assertEquals("WEBHOOK_MAINTENANCE", job.getValue().getFailureStage());
        assertNull(job.getValue().getErrorMessage());
        assertEquals(IntegrationStatus.DEGRADED, board.getConnectionStatus());
        assertEquals(1, board.getConsecutiveFailures());
    }
}
