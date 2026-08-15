package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent-ai")
public record AgentAiProperties(
        String baseUrl,
        String serviceToken,
        Duration connectTimeout,
        Duration readTimeout,
        Duration delegationTtl
) {
}

