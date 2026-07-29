package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integrations")
public record IntegrationProperties(
        String tokenEncryptionKey,
        String tokenEncryptionKeyId,
        String tokenEncryptionPreviousKeys,
        Duration oauthStateTtl,
        Duration httpConnectTimeout,
        Duration httpReadTimeout,
        boolean reconciliationEnabled,
        Duration overlapWindow
) {
}
