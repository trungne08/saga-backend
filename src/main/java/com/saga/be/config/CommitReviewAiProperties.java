package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent-ai.commit-review")
public record CommitReviewAiProperties(
        boolean executionEnabled,
        Duration pollDelay,
        Duration initialDelay
) {
}
