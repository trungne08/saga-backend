package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.JiraBoardRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ActiveProfiles("test")
@Import({JiraSyncJobService.class, SyncJobFinalizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JiraSyncJobServicePersistenceTest {

    @Autowired
    private JiraSyncJobService jiraSyncJobService;

    @Autowired
    private SyncJobFinalizationService finalizationService;

    @Autowired
    private JiraBoardRepository boardRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private SyncJobLogRepository jobRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jobRepository.deleteAll();
            boardRepository.deleteAll();
            projectRepository.deleteAll();
        });
    }

    @Test
    void finalizationCommitsWhenTheCallingTransactionRollsBack() {
        UUID jobId = transactionTemplate.execute(status -> jobRepository
                .saveAndFlush(runningJob(UUID.randomUUID())).getId());

        assertThrows(IllegalStateException.class, () ->
                transactionTemplate.executeWithoutResult(status -> {
                    finalizationService.finalizeJob(
                            jobId,
                            SyncJobStatus.FAILED,
                            0,
                            0,
                            null,
                            "UNEXPECTED_SYNC_FAILURE"
                    );
                    throw new IllegalStateException("rollback sync work");
                })
        );

        SyncJobLog job = jobRepository.findById(jobId).orElseThrow();
        assertEquals(SyncJobStatus.FAILED, job.getStatus());
        assertEquals("UNEXPECTED_SYNC_FAILURE", job.getErrorMessage());
        assertTrue(job.getCompletedAt() != null);
    }

    @Test
    void staleInProgressJobIsRecoveredAndInitialBackfillCanRetry() {
        JiraBoard board = createBoard();
        SyncJobLog stale = runningJob(board.getId());
        stale.setStartedAt(utcNow().minusMinutes(16));
        SyncJobLog persistedStale = transactionTemplate.execute(status ->
                jobRepository.saveAndFlush(stale));

        SyncJobLog retry = jiraSyncJobService.claim(
                board.getId(),
                SyncJobType.INITIAL_BACKFILL
        ).orElseThrow();

        SyncJobLog recovered = jobRepository.findById(persistedStale.getId())
                .orElseThrow();
        assertEquals(SyncJobStatus.FAILED, recovered.getStatus());
        assertEquals("STALE_SYNC_JOB_RECOVERED", recovered.getErrorMessage());
        assertEquals(SyncJobStatus.IN_PROGRESS, retry.getStatus());
        assertFalse(persistedStale.getId().equals(retry.getId()));
    }

    @Test
    void stalePendingJobIsRecovered() {
        SyncJobLog stale = runningJob(UUID.randomUUID());
        stale.setStatus(SyncJobStatus.PENDING);
        stale.setStartedAt(utcNow().minusMinutes(16));
        SyncJobLog persistedStale = transactionTemplate.execute(status ->
                jobRepository.saveAndFlush(stale));

        jiraSyncJobService.recoverStaleJobs();

        SyncJobLog recovered = jobRepository.findById(persistedStale.getId())
                .orElseThrow();
        assertEquals(SyncJobStatus.FAILED, recovered.getStatus());
        assertEquals("STALE_SYNC_JOB_RECOVERED", recovered.getErrorMessage());
    }

    @Test
    void concurrentInitialRetriesCreateOnlyOneRunningJob() throws Exception {
        JiraBoard board = createBoard();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return jiraSyncJobService.claim(
                        board.getId(),
                        SyncJobType.INITIAL_BACKFILL
                ).isPresent();
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return jiraSyncJobService.claim(
                        board.getId(),
                        SyncJobType.INITIAL_BACKFILL
                ).isPresent();
            });
            start.countDown();

            assertEquals(1, List.of(first.get(), second.get()).stream()
                    .filter(Boolean::booleanValue)
                    .count());
            assertEquals(1, jobRepository.findByStatusIn(List.of(
                    SyncJobStatus.IN_PROGRESS
            )).size());
        } finally {
            executor.shutdownNow();
        }
    }

    private JiraBoard createBoard() {
        return transactionTemplate.execute(status -> {
            Project project = projectRepository.saveAndFlush(Project.builder()
                    .name("Jira sync project " + UUID.randomUUID())
                    .build());
            return boardRepository.saveAndFlush(JiraBoard.builder()
                    .project(project)
                    .cloudId("cloud-" + UUID.randomUUID())
                    .jiraProjectId("project-" + UUID.randomUUID())
                    .projectKey("SAGA")
                    .connectionStatus(IntegrationStatus.BACKFILLING)
                    .build());
        });
    }

    private SyncJobLog runningJob(UUID boardId) {
        return SyncJobLog.builder()
                .targetSystem("JIRA")
                .targetId(boardId)
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(utcNow())
                .build();
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }
}
