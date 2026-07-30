package com.saga.be.integration.sync;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncJobFinalizationService {

    private final SyncJobLogRepository jobRepository;

    public SyncJobFinalizationService(SyncJobLogRepository jobRepository) {
        this.jobRepository = jobRepository;
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
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.saveAndFlush(job);
        });
    }

    private boolean isTerminal(SyncJobStatus status) {
        return status == SyncJobStatus.COMPLETED
                || status == SyncJobStatus.PARTIAL_FAILURE
                || status == SyncJobStatus.FAILED;
    }
}
