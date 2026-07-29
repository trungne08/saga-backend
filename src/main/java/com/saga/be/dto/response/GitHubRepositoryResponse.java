package com.saga.be.dto.response;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.integration.provider.GitHubRepositoryInfo;
import java.time.LocalDateTime;

public record GitHubRepositoryResponse(
        long repositoryId,
        String fullName,
        String defaultBranch,
        IntegrationStatus status,
        LocalDateTime lastSyncedAt
) {
    public static GitHubRepositoryResponse from(GitHubRepositoryInfo repository) {
        return new GitHubRepositoryResponse(
                repository.id(),
                repository.fullName(),
                repository.defaultBranch(),
                IntegrationStatus.CONNECTING,
                null
        );
    }

    public static GitHubRepositoryResponse from(GitRepo repository) {
        return new GitHubRepositoryResponse(
                repository.getRepositoryId(),
                repository.getFullName(),
                repository.getDefaultBranch(),
                repository.getConnectionStatus(),
                repository.getLastSyncedAt()
        );
    }
}
