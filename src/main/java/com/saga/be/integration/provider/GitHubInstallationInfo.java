package com.saga.be.integration.provider;

public record GitHubInstallationInfo(
        long installationId,
        String accountLogin,
        String accountType,
        boolean suspended
) {
}
