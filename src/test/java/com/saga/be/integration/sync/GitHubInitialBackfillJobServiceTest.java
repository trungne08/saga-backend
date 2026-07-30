package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitHubInitialBackfillJobServiceTest {

    private GitRepoRepository gitRepoRepository;
    private SyncJobLogRepository jobRepository;
    private GitHubInitialBackfillJobService service;
    private UUID repositoryId;
    private GitRepo repository;

    @BeforeEach
    void setUp() {
        gitRepoRepository = mock(GitRepoRepository.class);
        jobRepository = mock(SyncJobLogRepository.class);
        service = new GitHubInitialBackfillJobService(
                gitRepoRepository,
                jobRepository
        );
        repositoryId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(UUID.randomUUID());
        repository = GitRepo.builder()
                .project(project)
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build();
        repository.setId(repositoryId);
        when(gitRepoRepository.findForInitialBackfillClaimById(repositoryId))
                .thenReturn(Optional.of(repository));
        when(jobRepository.saveAndFlush(any(SyncJobLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void stuckBackfillingRepositoryWithoutJobIsClaimed() {
        when(jobRepository
                .findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
                        repositoryId,
                        SyncJobType.INITIAL_BACKFILL
                )).thenReturn(Optional.empty());

        Optional<SyncJobLog> claimed = service.claim(repositoryId);

        assertTrue(claimed.isPresent());
        SyncJobLog job = claimed.orElseThrow();
        assertEquals(SyncJobStatus.IN_PROGRESS, job.getStatus());
        assertEquals(SyncJobType.INITIAL_BACKFILL, job.getJobType());
        assertEquals(repositoryId, job.getTargetId());
    }

    @Test
    void runningInitialBackfillIsNotClaimedAgain() {
        SyncJobLog running = SyncJobLog.builder()
                .status(SyncJobStatus.IN_PROGRESS)
                .build();
        when(jobRepository
                .findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
                        repositoryId,
                        SyncJobType.INITIAL_BACKFILL
                )).thenReturn(Optional.of(running));

        assertTrue(service.claim(repositoryId).isEmpty());

        verify(jobRepository, never()).saveAndFlush(any(SyncJobLog.class));
        verify(gitRepoRepository, never()).saveAndFlush(repository);
    }

    @Test
    void failedInitialBackfillCanBeClaimedForControlledRetry() {
        repository.setConnectionStatus(IntegrationStatus.DEGRADED);
        SyncJobLog failed = SyncJobLog.builder()
                .status(SyncJobStatus.FAILED)
                .completedAt(LocalDateTime.now())
                .build();
        when(jobRepository
                .findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
                        repositoryId,
                        SyncJobType.INITIAL_BACKFILL
                )).thenReturn(Optional.of(failed));

        assertTrue(service.claim(repositoryId).isPresent());

        assertEquals(
                IntegrationStatus.BACKFILLING,
                repository.getConnectionStatus()
        );
        ArgumentCaptor<SyncJobLog> job =
                ArgumentCaptor.forClass(SyncJobLog.class);
        verify(jobRepository).saveAndFlush(job.capture());
        assertEquals(SyncJobStatus.IN_PROGRESS, job.getValue().getStatus());
    }

    @Test
    void activePreviouslySyncedRepositoryIsNotClaimed() {
        repository.setConnectionStatus(IntegrationStatus.ACTIVE);
        repository.setLastSyncedAt(LocalDateTime.now());

        assertTrue(service.claim(repositoryId).isEmpty());

        verify(jobRepository, never())
                .findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
                        any(),
                        any()
                );
        verify(jobRepository, never()).saveAndFlush(any(SyncJobLog.class));
    }
}
