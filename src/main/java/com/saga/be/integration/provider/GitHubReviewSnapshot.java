package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record GitHubReviewSnapshot(
        long id,
        int pullNumber,
        Long reviewerId,
        String state,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt
) {
}
