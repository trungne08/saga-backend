package com.saga.be.integration.provider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        String sprintName,
        Instant updatedAtUtc
) {

    public JiraIssueSnapshot(
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
        this(
                id, key, title, issueType, status, priority, storyPoints,
                assigneeAccountId, reporterAccountId, dueDate, createdAt,
                updatedAt, resolvedAt, resolution, sprintId, sprintName,
                updatedAt == null ? null : updatedAt.toInstant(ZoneOffset.UTC)
        );
    }
}
