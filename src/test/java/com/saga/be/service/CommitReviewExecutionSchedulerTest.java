package com.saga.be.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.saga.be.config.CommitReviewAiProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CommitReviewExecutionSchedulerTest {

    @Test
    void tickDelegatesToOrchestratorDrain() {
        CommitReviewOrchestrator orchestrator = mock(CommitReviewOrchestrator.class);
        CommitReviewExecutionScheduler scheduler = new CommitReviewExecutionScheduler(
                orchestrator, properties(true)
        );

        scheduler.drainAndPoll();

        verify(orchestrator).drainPendingAndPoll();
    }

    @Test
    void disabledExecutionDoesNotDrain() {
        CommitReviewOrchestrator orchestrator = mock(CommitReviewOrchestrator.class);
        CommitReviewExecutionScheduler scheduler = new CommitReviewExecutionScheduler(
                orchestrator, properties(false)
        );

        scheduler.drainAndPoll();

        verifyNoInteractions(orchestrator);
    }

    @Test
    void tickFailureIsSwallowedAndDoesNotDisableScheduler() {
        CommitReviewOrchestrator orchestrator = mock(CommitReviewOrchestrator.class);
        doThrow(new IllegalStateException("Connection is read-only"))
                .when(orchestrator).drainPendingAndPoll();
        CommitReviewExecutionScheduler scheduler = new CommitReviewExecutionScheduler(
                orchestrator, properties(true)
        );

        scheduler.drainAndPoll();
        scheduler.drainAndPoll();

        verify(orchestrator, times(2)).drainPendingAndPoll();
        verify(orchestrator, never()).startQueued(org.mockito.ArgumentMatchers.any());
    }

    private static CommitReviewAiProperties properties(boolean enabled) {
        return new CommitReviewAiProperties(enabled, Duration.ofSeconds(15), Duration.ofSeconds(15));
    }
}
