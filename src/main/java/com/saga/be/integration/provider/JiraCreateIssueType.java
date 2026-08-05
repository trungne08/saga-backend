package com.saga.be.integration.provider;

public record JiraCreateIssueType(
        String id,
        String name,
        boolean subtask,
        String description
) {
}
