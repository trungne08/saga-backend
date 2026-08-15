package com.saga.be.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.student-invitation")
public class StudentInvitationProperties {

    private String loginUrl;
    private long retryDelayMs = 60000;
    private long processingTimeoutMs = 300000;

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
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

    public Duration processingTimeout() {
        if (processingTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "STUDENT_INVITATION_PROCESSING_TIMEOUT_MS must be greater than zero"
            );
        }
        return Duration.ofMillis(processingTimeoutMs);
    }

    public URI loginUri() {
        try {
            URI value = URI.create(loginUrl == null ? "" : loginUrl.trim());
            if (!value.isAbsolute()
                    || value.getHost() == null
                    || value.getUserInfo() != null
                    || !("http".equalsIgnoreCase(value.getScheme())
                    || "https".equalsIgnoreCase(value.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "STUDENT_INVITATION_LOGIN_URL must be an absolute HTTP(S) URI"
            );
        }
    }
}
