package com.saga.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internal-ai")
public record InternalAiProperties(String serviceToken) {
}
