package com.saga.be.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saga.integration.jira")
public record JiraTimeZoneProperties(String timeZone) {

    public JiraTimeZoneProperties {
        timeZone = timeZone == null || timeZone.isBlank() ? "UTC" : timeZone.trim();
    }
}
