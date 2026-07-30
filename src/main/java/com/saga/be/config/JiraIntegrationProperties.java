package com.saga.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.jira")
public record JiraIntegrationProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String authorizationUrl,
        String tokenUrl,
        String apiBaseUrl,
        String callbackUrl,
        String webhookPublicUrl,
        String scopes
) {
}
