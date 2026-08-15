package com.saga.be.integration.provider;

import java.time.LocalDateTime;
import java.util.List;

public record GitHubCommitDetailSnapshot(
        String sha,
        String message,
        LocalDateTime committedAt,
        Integer additions,
        Integer deletions,
        List<GitHubChangedFileSnapshot> changedFiles
) {
    public GitHubCommitDetailSnapshot {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
    }
}
