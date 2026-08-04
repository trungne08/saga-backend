package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GitHubSyncJobServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:13:49Z"),
            ZoneOffset.UTC
    );

    @Test
    void reconciliationClaimWritesStartedAtUsingTheFixedUtcClock() {
        GitRepoRepository repository = mock(GitRepoRepository.class);
        SyncJobLogRepository jobs = mock(SyncJobLogRepository.class);
        UUID repositoryId = UUID.randomUUID();
        GitRepo gitRepo = GitRepo.builder()
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build();
        gitRepo.setId(repositoryId);
        when(repository.findForInitialBackfillClaimById(repositoryId))
                .thenReturn(Optional.of(gitRepo));
        when(jobs.findActiveByTargetIdOrderByStartedAtDesc(
                eq(repositoryId),
                any()
        )).thenReturn(List.of());
        when(jobs.saveAndFlush(any(SyncJobLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        GitHubSyncJobService service = new GitHubSyncJobService(
                repository,
                jobs,
                mock(SyncJobFinalizationService.class),
                Duration.ofMinutes(15),
                FIXED_CLOCK
        );

        SyncJobLog job = service.claim(repositoryId, SyncJobType.RECONCILIATION)
                .orElseThrow();

        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                job.getStartedAt()
        );
        assertTrue(job.getJobType() == SyncJobType.RECONCILIATION);
    }
}
