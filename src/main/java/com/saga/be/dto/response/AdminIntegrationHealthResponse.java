package com.saga.be.dto.response;

import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Deterministic local integration state; it never represents provider live health. */
public record AdminIntegrationHealthResponse(
        JiraLocalState jira,
        GitHubLocalState gitHub
) {
    public record JiraLocalState(
            boolean enabled,
            long linkedProjectCount,
            List<ConnectionStatusCount> connectionStatuses,
            long storedWebhookIdCount,
            LocalDateTime latestLastSyncedAt,
            List<WebhookReceiptStatusCount> webhookReceiptStatuses
    ) {
    }

    public record GitHubLocalState(
            boolean enabled,
            long linkedProjectCount,
            List<ConnectionStatusCount> connectionStatuses,
            List<InstallationStatusCount> installationStatuses,
            LocalDateTime latestLastSyncedAt,
            List<WebhookReceiptStatusCount> webhookReceiptStatuses
    ) {
    }

    public record ConnectionStatusCount(IntegrationStatus status, long count) {
    }

    public record InstallationStatusCount(GitHubInstallationStatus status, long count) {
    }

    public record WebhookReceiptStatusCount(WebhookReceiptStatus status, long count) {
    }
}
