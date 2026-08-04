package com.saga.be.dto.response;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public record SyncStatusResponse(
        UUID projectId,
        List<Job> recentJobs
) {
    public record Job(
            UUID id,
            String targetSystem,
            SyncJobType type,
            SyncJobStatus status,
            Instant startedAt,
            Instant completedAt,
            Integer itemsProcessed,
            Integer itemsFailed,
            String errorCategory,
            String failureStage
    ) {
        public static Job from(SyncJobLog log) {
            return new Job(
                    log.getId(),
                    log.getTargetSystem(),
                    log.getJobType(),
                    log.getStatus(),
                    log.getStartedAt() == null
                            ? null
                            : log.getStartedAt().toInstant(ZoneOffset.UTC),
                    log.getCompletedAt() == null
                            ? null
                            : log.getCompletedAt().toInstant(ZoneOffset.UTC),
                    log.getItemsProcessed(),
                    log.getItemsFailed(),
                    log.getErrorCategory() == null
                            ? log.getErrorMessage()
                            : log.getErrorCategory(),
                    log.getFailureStage()
            );
        }
    }
}
