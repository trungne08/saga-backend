package com.saga.be.dto.response;

import com.saga.be.entity.enums.IntegrationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProjectIntegrationsResponse(
        UUID projectId,
        JiraProjectIntegration jira,
        List<GitHubRepositoryResponse> githubRepositories
) {
    public record JiraProjectIntegration(
            String siteUrl,
            String projectKey,
            IntegrationStatus status,
            LocalDateTime webhookExpiresAt,
            LocalDateTime lastSyncedAt
    ) {
    }
}
