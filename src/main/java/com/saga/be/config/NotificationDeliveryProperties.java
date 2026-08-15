package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification.delivery")
public class NotificationDeliveryProperties {

    private long retryDelayMs = 60000;
    private long processingTimeoutMs = 300000;

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public long getProcessingTimeoutMs() {
        return processingTimeoutMs;
    }

    public void setProcessingTimeoutMs(long processingTimeoutMs) {
        this.processingTimeoutMs = processingTimeoutMs;
    }

    public Duration processingTimeout() {
        if (processingTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "NOTIFICATION_DELIVERY_PROCESSING_TIMEOUT_MS must be greater than zero"
            );
        }
        return Duration.ofMillis(processingTimeoutMs);
    }
}
