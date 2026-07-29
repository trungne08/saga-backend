package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record GitHubCommitSnapshot(
        String sha,
        Long authorId,
        String message,
        LocalDateTime committedAt,
        Integer additions,
        Integer deletions,
        Integer filesChanged,
        LocalDateTime updatedAt
) {
}
