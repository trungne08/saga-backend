package com.saga.be.dto.response;

import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
            WebhookReceiptSummary latestWebhookReceipt,
            WebhookMaintenanceResult latestWebhookMaintenance,
            List<WebhookReceiptStatusCount> webhookReceiptStatuses
    ) {
    }

    public record GitHubLocalState(
            boolean enabled,
            long linkedProjectCount,
            List<ConnectionStatusCount> connectionStatuses,
            List<InstallationStatusCount> installationStatuses,
            LocalDateTime latestLastSyncedAt,
            WebhookReceiptSummary latestWebhookReceipt,
            List<WebhookReceiptStatusCount> webhookReceiptStatuses
    ) {
    }

    public record ConnectionStatusCount(IntegrationStatus status, long count) {
    }

    public record InstallationStatusCount(GitHubInstallationStatus status, long count) {
    }

    public record WebhookReceiptStatusCount(WebhookReceiptStatus status, long count) {
    }

    public record WebhookReceiptSummary(
            UUID receiptId,
            String eventType,
            WebhookReceiptStatus status,
            LocalDateTime receivedAt,
            LocalDateTime processedAt,
            String safeErrorCode
    ) {
    }

    public record WebhookMaintenanceResult(
            UUID jiraBoardId,
            SyncJobStatus status,
            LocalDateTime occurredAt,
            String safeErrorCode
    ) {
    }
}
