package com.saga.be.integration.provider;

public record GitHubRepositoryInfo(
        long id,
        String owner,
        String name,
        String fullName,
        String htmlUrl,
        String defaultBranch
) {
}
