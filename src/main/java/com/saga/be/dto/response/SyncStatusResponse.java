package com.saga.be.dto.response;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import java.time.LocalDateTime;
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
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Integer itemsProcessed,
            Integer itemsFailed
    ) {
        public static Job from(SyncJobLog log) {
            return new Job(
                    log.getId(),
                    log.getTargetSystem(),
                    log.getJobType(),
                    log.getStatus(),
                    log.getStartedAt(),
                    log.getCompletedAt(),
                    log.getItemsProcessed(),
                    log.getItemsFailed()
            );
        }
    }
}
