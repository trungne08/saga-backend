package com.saga.be.integration.provider;

import com.saga.be.entity.value.TaskComponentSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
        Instant updatedAtUtc,
        List<String> labels,
        String description,
        List<TaskComponentSnapshot> components,
        boolean storyPointsAuthoritative
) {

    public JiraIssueSnapshot {
        labels = labels == null ? List.of() : List.copyOf(labels);
        components = components == null ? List.of() : List.copyOf(components);
    }

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
            String sprintName,
            Instant updatedAtUtc,
            List<String> labels,
            String description,
            List<TaskComponentSnapshot> components
    ) {
        this(
                id, key, title, issueType, status, priority, storyPoints,
                assigneeAccountId, reporterAccountId, dueDate, createdAt,
                updatedAt, resolvedAt, resolution, sprintId, sprintName,
                updatedAtUtc, labels, description, components,
                storyPoints != null
        );
    }

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
            String sprintName,
            Instant updatedAtUtc
    ) {
        this(
                id, key, title, issueType, status, priority, storyPoints,
                assigneeAccountId, reporterAccountId, dueDate, createdAt,
                updatedAt, resolvedAt, resolution, sprintId, sprintName,
                updatedAtUtc, List.of(), null, List.of(), storyPoints != null
        );
    }

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
            String sprintName,
            Instant updatedAtUtc,
            List<String> labels
    ) {
        this(
                id, key, title, issueType, status, priority, storyPoints,
                assigneeAccountId, reporterAccountId, dueDate, createdAt,
                updatedAt, resolvedAt, resolution, sprintId, sprintName,
                updatedAtUtc, labels, null, List.of(), storyPoints != null
        );
    }

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
                updatedAt == null ? null : updatedAt.toInstant(ZoneOffset.UTC),
                List.of(), null, List.of(), storyPoints != null
        );
    }
}
