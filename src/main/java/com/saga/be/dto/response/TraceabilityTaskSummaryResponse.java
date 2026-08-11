package com.saga.be.dto.response;

import com.saga.be.entity.Task;
import com.saga.be.entity.enums.TaskStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TraceabilityTaskSummaryResponse(
        UUID id,
        String externalKey,
        String title,
        TaskStatus status,
        GitHubIssueSummaryResponse.StudentReference assignee,
        LocalDateTime externalUpdatedAt
) {

    public static TraceabilityTaskSummaryResponse from(Task task) {
        return new TraceabilityTaskSummaryResponse(
                task.getId(),
                task.getExternalKey(),
                task.getTitle(),
                task.getStatus(),
                GitHubIssueSummaryResponse.StudentReference.from(task.getAssignee()),
                task.getExternalUpdatedAt()
        );
    }
}
