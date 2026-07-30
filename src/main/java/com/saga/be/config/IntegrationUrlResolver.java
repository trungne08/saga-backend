package com.saga.be.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntegrationUrlResolver {

    private final JiraIntegrationProperties jiraProperties;
    private final GitHubIntegrationProperties gitHubProperties;
    private final String localWebhookBaseUrl;

    public IntegrationUrlResolver(
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties,
            @Value("${app.integrations.local-webhook-base-url:}")
            String localWebhookBaseUrl
    ) {
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
        this.localWebhookBaseUrl = localWebhookBaseUrl;
    }

    public String jiraWebhookPublicUrl() {
        return explicitOrLocalTunnel(
                jiraProperties.webhookPublicUrl(),
                "/api/webhooks/jira"
        );
    }

    public String gitHubWebhookPublicUrl() {
        return explicitOrLocalTunnel(
                gitHubProperties.webhookPublicUrl(),
                "/api/webhooks/github"
        );
    }

    private String explicitOrLocalTunnel(String configured, String path) {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if (localWebhookBaseUrl == null || localWebhookBaseUrl.isBlank()) {
            return "";
        }
        return localWebhookBaseUrl.trim().replaceFirst("/+$", "") + path;
    }
}
