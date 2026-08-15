package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ActiveProfiles("test")
@Import({GitHubSyncJobService.class, SyncJobFinalizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GitHubSyncJobServicePersistenceTest {

    @Autowired
    private GitHubSyncJobService gitHubSyncJobService;

    @Autowired
    private GitRepoRepository gitRepoRepository;

    @Autowired
    private SyncJobLogRepository jobRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GitHubInstallationRepository installationRepository;

    @Autowired
    private TransactionTemplate transactions;

    @AfterEach
    void cleanUp() {
        transactions.executeWithoutResult(status -> {
            jobRepository.deleteAll();
            gitRepoRepository.deleteAll();
            installationRepository.deleteAll();
            projectRepository.deleteAll();
        });
    }

    @Test
    @Timeout(10)
    void competingReconciliationsOnOneRepositoryCreateOneActiveJob() throws Exception {
        UUID repositoryId = createRepository();
        List<Boolean> claimed = runConcurrentClaims(
                repositoryId,
                SyncJobType.RECONCILIATION,
                repositoryId,
                SyncJobType.RECONCILIATION
        );

        assertEquals(1, claimed.stream().filter(Boolean::booleanValue).count());
        List<SyncJobLog> active = transactions.execute(status -> jobRepository
                .findActiveByTargetIdOrderByStartedAtDesc(repositoryId, List.of(
                        SyncJobStatus.IN_PROGRESS
                )));
        assertEquals(1, active.size());
    }

    @Test
    @Timeout(10)
    void differentRepositoriesCanBeClaimedInParallel() throws Exception {
        UUID firstRepositoryId = createRepository();
        UUID secondRepositoryId = createRepository();
        List<Boolean> claimed = runConcurrentClaims(
                firstRepositoryId,
                SyncJobType.RECONCILIATION,
                secondRepositoryId,
                SyncJobType.RECONCILIATION
        );

        assertTrue(claimed.get(0));
        assertTrue(claimed.get(1));
    }

    @Test
    @Timeout(10)
    void initialBackfillAndReconciliationDoNotClaimTheSameRepositoryTogether()
            throws Exception {
        UUID repositoryId = createRepository();
        List<Boolean> claimed = runConcurrentClaims(
                repositoryId,
                SyncJobType.INITIAL_BACKFILL,
                repositoryId,
                SyncJobType.RECONCILIATION
        );

        assertEquals(1, claimed.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void staleGitHubJobIsRecoveredButNewAndTerminalJobsAreUntouched() {
        UUID repositoryId = createRepository();
        SyncJobLog stale = saveJob(
                repositoryId,
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
                        .minusMinutes(16),
                SyncJobStatus.IN_PROGRESS
        );
        SyncJobLog fresh = saveJob(
                repositoryId,
                LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC),
                SyncJobStatus.IN_PROGRESS
        );

        gitHubSyncJobService.recoverStaleJobs();

        SyncJobLog recovered = jobRepository.findById(stale.getId()).orElseThrow();
        SyncJobLog active = jobRepository.findById(fresh.getId()).orElseThrow();
        assertEquals(SyncJobStatus.FAILED, recovered.getStatus());
        assertEquals("STALE_SYNC_JOB_RECOVERED", recovered.getErrorCategory());
        assertTrue(recovered.getCompletedAt() != null);
        assertEquals(SyncJobStatus.IN_PROGRESS, active.getStatus());

        LocalDateTime completedAt = recovered.getCompletedAt();
        gitHubSyncJobService.recoverStaleJobs();
        assertEquals(completedAt, jobRepository.findById(stale.getId())
                .orElseThrow().getCompletedAt());
        assertEquals(
                SyncJobStatus.IN_PROGRESS,
                jobRepository.findById(fresh.getId()).orElseThrow().getStatus()
        );
    }

    private List<Boolean> runConcurrentClaims(
            UUID firstRepositoryId,
            SyncJobType firstType,
            UUID secondRepositoryId,
            SyncJobType secondType
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> claimAfterBarrier(
                    ready,
                    start,
                    firstRepositoryId,
                    firstType
            ));
            Future<Boolean> second = executor.submit(() -> claimAfterBarrier(
                    ready,
                    start,
                    secondRepositoryId,
                    secondType
            ));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    first.get(5, TimeUnit.SECONDS),
                    second.get(5, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean claimAfterBarrier(
            CountDownLatch ready,
            CountDownLatch start,
            UUID repositoryId,
            SyncJobType type
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return gitHubSyncJobService.claim(repositoryId, type).isPresent();
    }

    private UUID createRepository() {
        return transactions.execute(status -> {
            Project project = projectRepository.saveAndFlush(Project.builder()
                    .name("GitHub sync " + UUID.randomUUID())
                    .build());
            GitHubInstallation installation = installationRepository.saveAndFlush(
                    GitHubInstallation.builder()
                            .installationId(Math.abs(
                                    UUID.randomUUID().getMostSignificantBits()
                            ))
                            .installedByCognitoSub("sync-test")
                            .installationStatus(GitHubInstallationStatus.ACTIVE)
                            .build()
            );
            return gitRepoRepository.saveAndFlush(GitRepo.builder()
                    .project(project)
                    .installation(installation)
                    .provider("GITHUB")
                    .repositoryId(Math.abs(UUID.randomUUID().getLeastSignificantBits()))
                    .ownerLogin("saga")
                    .name("backend")
                    .fullName("saga/backend-" + UUID.randomUUID())
                    .connectionStatus(IntegrationStatus.BACKFILLING)
                    .build()).getId();
        });
    }

    private SyncJobLog saveJob(
            UUID repositoryId,
            LocalDateTime startedAt,
            SyncJobStatus status
    ) {
        return transactions.execute(transaction -> jobRepository.saveAndFlush(
                SyncJobLog.builder()
                        .targetSystem("GITHUB")
                        .targetId(repositoryId)
                        .jobType(SyncJobType.RECONCILIATION)
                        .status(status)
                        .startedAt(startedAt)
                        .build()
        ));
    }
}
