package com.saga.be.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.CommitReviewJobResponses;
import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.CommitReviewIntentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class CommitReviewOrchestratorTest {

    @Test
    void startQueuedClaimsThenCallsAiOutsidePersistenceAndOnlyOnce() {
        CommitReviewIntentService intents = mock(CommitReviewIntentService.class);
        CommitReviewIntentRepository repository = mock(CommitReviewIntentRepository.class);
        CommitReviewAiClient client = mock(CommitReviewAiClient.class);
        CommitReviewOrchestrator orchestrator = new CommitReviewOrchestrator(
                intents, repository, client,
                mock(CommitReviewResultPersistenceService.class),
                mock(CommitReviewWarningPublisher.class),
                mock(CommitReviewHistoricalDiscoveryService.class)
        );
        UUID intentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(client.isConfigured()).thenReturn(true);
        when(intents.claimPendingForStart(intentId))
                .thenReturn(Optional.of(intentId))
                .thenReturn(Optional.empty());
        when(repository.findWithReviewTargetById(intentId)).thenReturn(Optional.of(intent(intentId)));
        when(client.start(any(), eq(42L), eq("abcdef0123456789abcdef0123456789abcdef01"),
                eq(CommitReviewPolicyVersion.LIVE_TASK_AWARE_V1)))
                .thenReturn(new CommitReviewJobResponses.Start(
                        jobId, "PENDING", "commit-review-live-task-aware-v1", "HIGH"
                ));

        orchestrator.startQueued(intentId);
        orchestrator.startQueued(intentId);

        verify(intents, times(2)).claimPendingForStart(intentId);
        verify(client, times(1)).start(any(), eq(42L), any(), eq(CommitReviewPolicyVersion.LIVE_TASK_AWARE_V1));
        verify(intents).markStarted(intentId, jobId, "commit-review-live-task-aware-v1", "PENDING");
        verify(client, times(1)).runBounded();
    }

    @Test
    void boundedExecutionFailureIsSwallowedAndPollingContinues() {
        CommitReviewIntentService intents = mock(CommitReviewIntentService.class);
        CommitReviewAiClient client = mock(CommitReviewAiClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(intents.nextWorkAvoidingHistoricalStarvation()).thenReturn(List.of());
        when(intents.nextInFlight(anyInt())).thenReturn(List.of());
        doThrow(new IntegrationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI_AGENT_UNAVAILABLE",
                "The AI Agent service is unavailable"
        )).when(client).runBounded();
        CommitReviewOrchestrator orchestrator = new CommitReviewOrchestrator(
                intents, mock(CommitReviewIntentRepository.class), client,
                mock(CommitReviewResultPersistenceService.class),
                mock(CommitReviewWarningPublisher.class),
                mock(CommitReviewHistoricalDiscoveryService.class)
        );

        orchestrator.drainPendingAndPoll();

        verify(client).runBounded();
        verify(intents).nextInFlight(anyInt());
    }

    @Test
    void unconfiguredClientDoesNotClaimOrCallNetwork() {
        CommitReviewIntentService intents = mock(CommitReviewIntentService.class);
        CommitReviewAiClient client = mock(CommitReviewAiClient.class);
        when(client.isConfigured()).thenReturn(false);
        CommitReviewOrchestrator orchestrator = new CommitReviewOrchestrator(
                intents, mock(CommitReviewIntentRepository.class), client,
                mock(CommitReviewResultPersistenceService.class),
                mock(CommitReviewWarningPublisher.class),
                mock(CommitReviewHistoricalDiscoveryService.class)
        );

        orchestrator.startQueued(UUID.randomUUID());

        verify(intents, never()).claimPendingForStart(any());
        verify(client, never()).start(any(), anyLong(), any(), any());
    }

    @Test
    void alreadyClaimedIntentDoesNotStartASecondJob() {
        CommitReviewIntentService intents = mock(CommitReviewIntentService.class);
        CommitReviewAiClient client = mock(CommitReviewAiClient.class);
        when(client.isConfigured()).thenReturn(true);
        when(intents.claimPendingForStart(any())).thenReturn(Optional.empty());
        CommitReviewOrchestrator orchestrator = new CommitReviewOrchestrator(
                intents, mock(CommitReviewIntentRepository.class), client,
                mock(CommitReviewResultPersistenceService.class),
                mock(CommitReviewWarningPublisher.class),
                mock(CommitReviewHistoricalDiscoveryService.class)
        );

        orchestrator.startQueued(UUID.randomUUID());

        verify(client, never()).start(any(), anyLong(), any(), any());
        verify(client, never()).runBounded();
    }

    private CommitReviewIntent intent(UUID id) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        GitRepo repo = new GitRepo();
        repo.setProject(project);
        repo.setRepositoryId(42L);
        CommitReviewIntent intent = new CommitReviewIntent();
        ReflectionTestUtils.setField(intent, "id", id);
        intent.setRepo(repo);
        intent.setShaHash("abcdef0123456789abcdef0123456789abcdef01");
        intent.setReviewMode(CommitReviewMode.LIVE_TASK_AWARE);
        return intent;
    }
}
