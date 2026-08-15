package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.integration")
public record IntegrationCallbackProperties(
        String callbackRedirectUri,
        Duration callbackResultTtl
) {
}
