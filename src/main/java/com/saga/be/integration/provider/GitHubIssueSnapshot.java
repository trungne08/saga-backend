package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record GitHubIssueSnapshot(
        long id,
        String nodeId,
        int number,
        String title,
        String state,
        Long authorId,
        Long assigneeId,
        boolean pullRequest,
        LocalDateTime updatedAt,
        LocalDateTime closedAt
) {
}
