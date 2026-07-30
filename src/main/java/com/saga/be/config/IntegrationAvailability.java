package com.saga.be.config;

import com.saga.be.exception.IntegrationException;
import org.springframework.stereotype.Component;

@Component
public class IntegrationAvailability {

    private final JiraIntegrationProperties jiraProperties;
    private final GitHubIntegrationProperties gitHubProperties;

    public IntegrationAvailability(
            JiraIntegrationProperties jiraProperties,
            GitHubIntegrationProperties gitHubProperties
    ) {
        this.jiraProperties = jiraProperties;
        this.gitHubProperties = gitHubProperties;
    }

    public boolean jiraEnabled() {
        return jiraProperties.enabled();
    }

    public boolean gitHubEnabled() {
        return gitHubProperties.enabled();
    }

    public void requireJira() {
        if (!jiraEnabled()) {
            throw IntegrationException.notConfigured("Jira");
        }
    }

    public void requireGitHub() {
        if (!gitHubEnabled()) {
            throw IntegrationException.notConfigured("GitHub");
        }
    }
}
