package com.saga.be.integration.provider;

public record GitHubChangedFileSnapshot(
        String path,
        String changeType,
        Integer additions,
        Integer deletions,
        String patch
) {
}
