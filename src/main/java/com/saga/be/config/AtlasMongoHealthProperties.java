package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mongodb.health")
public record AtlasMongoHealthProperties(Duration timeout) {

    public AtlasMongoHealthProperties {
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.mongodb.health.timeout must be positive");
        }
    }
}
