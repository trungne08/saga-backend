package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncJobFinalizationService {

    private final SyncJobLogRepository jobRepository;
    private final Clock clock;

    @Autowired
    public SyncJobFinalizationService(SyncJobLogRepository jobRepository) {
        this(jobRepository, Clock.systemUTC());
    }

    SyncJobFinalizationService(
            SyncJobLogRepository jobRepository,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(
            UUID jobId,
            SyncJobStatus status,
            int processed,
            int failed,
            LocalDateTime cursorAfter,
            String safeErrorCategory
    ) {
        finalizeJob(
                jobId,
                status,
                processed,
                failed,
                cursorAfter,
                safeErrorCategory,
                null
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeJob(
            UUID jobId,
            SyncJobStatus status,
            int processed,
            int failed,
            LocalDateTime cursorAfter,
            String safeErrorCategory,
            String failureStage
    ) {
        if (!isTerminal(status)) {
            throw new IllegalArgumentException("Sync job status must be terminal");
        }
        jobRepository.findById(jobId).ifPresent(job -> {
            if (isTerminal(job.getStatus())) {
                return;
            }
            job.setStatus(status);
            job.setItemsProcessed(processed);
            job.setItemsFailed(failed);
            job.setCursorAfter(cursorAfter);
            job.setErrorMessage(safeErrorCategory);
            job.setErrorCategory(safeErrorCategory);
            job.setFailureStage(failureStage);
            job.setCompletedAt(utcNow());
            jobRepository.saveAndFlush(job);
        });
    }

    private boolean isTerminal(SyncJobStatus status) {
        return status == SyncJobStatus.COMPLETED
                || status == SyncJobStatus.PARTIAL_FAILURE
                || status == SyncJobStatus.FAILED;
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
