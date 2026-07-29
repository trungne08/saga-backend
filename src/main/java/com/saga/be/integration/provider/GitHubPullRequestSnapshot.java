package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record GitHubPullRequestSnapshot(
        long id,
        String nodeId,
        int number,
        String title,
        String state,
        boolean draft,
        Long authorId,
        LocalDateTime mergedAt,
        LocalDateTime updatedAt,
        int reviewComments,
        int comments
) {
}
