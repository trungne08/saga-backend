package com.saga.be.dto.response;

import com.saga.be.integration.provider.GitHubInstallationInfo;
import com.saga.be.integration.provider.GitHubRepositoryInfo;
import java.util.List;
import java.util.UUID;

public record GitHubInstallationResponse(
        UUID projectId,
        long installationId,
        String accountLogin,
        String accountType,
        List<GitHubRepositoryResponse> repositories
) {
    public static GitHubInstallationResponse from(
            UUID projectId,
            GitHubInstallationInfo installation,
            List<GitHubRepositoryInfo> repositories
    ) {
        return new GitHubInstallationResponse(
                projectId,
                installation.installationId(),
                installation.accountLogin(),
                installation.accountType(),
                repositories.stream().map(GitHubRepositoryResponse::from).toList()
        );
    }
}
