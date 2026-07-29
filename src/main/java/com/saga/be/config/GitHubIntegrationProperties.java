package com.saga.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations.github")
public record GitHubIntegrationProperties(
        String appId,
        String clientId,
        String clientSecret,
        String privateKey,
        String webhookSecret,
        String appSlug,
        String setupUrl,
        String apiBaseUrl,
        String webBaseUrl,
        String personalCallbackUrl,
        String projectCallbackUrl
) {
}
