package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record JiraIssueSnapshot(
        String id,
        String key,
        String title,
        String issueType,
        String status,
        String priority,
        Integer storyPoints,
        String assigneeAccountId,
        String reporterAccountId,
        LocalDateTime dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime resolvedAt,
        String resolution,
        String sprintId,
        String sprintName
) {
}
