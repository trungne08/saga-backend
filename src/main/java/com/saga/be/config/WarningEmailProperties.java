package com.saga.be.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.warning-email")
public class WarningEmailProperties {

    private boolean processingEnabled = true;
    private long retryDelayMs = 60000;
    private long processingTimeoutMs = 300000;
    private int maxAttempts = 5;

    public boolean isProcessingEnabled() {
        return processingEnabled;
    }

    public void setProcessingEnabled(boolean processingEnabled) {
        this.processingEnabled = processingEnabled;
    }

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

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration processingTimeout() {
        if (processingTimeoutMs <= 0) {
            throw new IllegalStateException("WARNING_EMAIL_PROCESSING_TIMEOUT_MS must be greater than zero");
        }
        return Duration.ofMillis(processingTimeoutMs);
    }
}
