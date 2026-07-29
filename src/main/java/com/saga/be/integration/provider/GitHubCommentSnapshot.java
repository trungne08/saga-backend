package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record GitHubCommentSnapshot(
        long id,
        int targetNumber,
        Long authorId,
        String body,
        boolean pullRequestComment,
        LocalDateTime updatedAt
) {
}
