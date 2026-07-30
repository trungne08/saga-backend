package com.saga.be.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class IntegrationPublicUrlValidatorTest {

    @Test
    void acceptsOnlyUrlsDerivedFromThePublicOrigin() {
        IntegrationPublicUrlValidator validator = validator(
                "https://saga.example",
                "https://saga.example/api/integrations/jira/callback"
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsAProviderCallbackOnAnotherOriginOrPath() {
        IntegrationPublicUrlValidator validator = validator(
                "https://saga.example",
                "https://wrong.example/api/integrations/jira/callback"
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void localProfileAcceptsLocalhostCallbacksAndHttpsTunnelWebhooks() {
        String base = "http://localhost:8080";
        JiraIntegrationProperties jira = new JiraIntegrationProperties(
                true,
                "jira-client",
                "jira-secret",
                "https://auth.atlassian.com/authorize",
                "https://auth.atlassian.com/oauth/token",
                "https://api.atlassian.com",
                base + "/api/integrations/jira/callback",
                "",
                "read:me"
        );
        GitHubIntegrationProperties github = github(true, base, "");
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> new IntegrationPublicUrlValidator(
                base,
                jira,
                github,
                new IntegrationUrlResolver(
                        jira,
                        github,
                        "https://tunnel.example/"
                ),
                environment
        ).validate());
    }

    @Test
    void productionRejectsNonHttpsPublicBaseEvenWhenProvidersAreDisabled() {
        JiraIntegrationProperties jira = new JiraIntegrationProperties(
                false, "", "", "", "", "", "", "", ""
        );
        GitHubIntegrationProperties github = github(false, "", "");

        assertThrows(IllegalStateException.class, () -> new IntegrationPublicUrlValidator(
                "http://localhost:8080",
                jira,
                github,
                new IntegrationUrlResolver(jira, github, ""),
                new MockEnvironment()
        ).validate());
    }

    @Test
    void missingJiraCredentialsFailOnlyWhenJiraIsEnabled() {
        JiraIntegrationProperties disabled = new JiraIntegrationProperties(
                false, "", "", "", "", "", "", "", ""
        );
        GitHubIntegrationProperties github = github(false, "", "");

        assertDoesNotThrow(() -> new IntegrationPublicUrlValidator(
                "https://saga.example",
                disabled,
                github,
                new IntegrationUrlResolver(disabled, github, ""),
                new MockEnvironment()
        ).validate());

        JiraIntegrationProperties enabled = new JiraIntegrationProperties(
                true, "", "", "", "", "", "", "", ""
        );
        assertThrows(IllegalStateException.class, () -> new IntegrationPublicUrlValidator(
                "https://saga.example",
                enabled,
                github,
                new IntegrationUrlResolver(enabled, github, ""),
                new MockEnvironment()
        ).validate());
    }

    @Test
    void missingGitHubCredentialsFailOnlyWhenGitHubIsEnabled() {
        JiraIntegrationProperties jira = new JiraIntegrationProperties(
                false, "", "", "", "", "", "", "", ""
        );
        GitHubIntegrationProperties disabled = emptyGitHub(false);

        assertDoesNotThrow(() -> new IntegrationPublicUrlValidator(
                "https://saga.example",
                jira,
                disabled,
                new IntegrationUrlResolver(jira, disabled, ""),
                new MockEnvironment()
        ).validate());

        GitHubIntegrationProperties enabled = emptyGitHub(true);
        assertThrows(IllegalStateException.class, () -> new IntegrationPublicUrlValidator(
                "https://saga.example",
                jira,
                enabled,
                new IntegrationUrlResolver(jira, enabled, ""),
                new MockEnvironment()
        ).validate());
    }

    private IntegrationPublicUrlValidator validator(
            String publicBaseUrl,
            String jiraCallbackUrl
    ) {
        JiraIntegrationProperties jira = new JiraIntegrationProperties(
                true,
                "jira-client",
                "jira-secret",
                "https://auth.atlassian.com/authorize",
                "https://auth.atlassian.com/oauth/token",
                "https://api.atlassian.com",
                jiraCallbackUrl,
                publicBaseUrl + "/api/webhooks/jira",
                "read:me"
        );
        GitHubIntegrationProperties github = github(true, publicBaseUrl, publicBaseUrl);
        IntegrationUrlResolver resolver = new IntegrationUrlResolver(
                jira,
                github,
                ""
        );
        return new IntegrationPublicUrlValidator(
                publicBaseUrl,
                jira,
                github,
                resolver,
                new MockEnvironment()
        );
    }

    private GitHubIntegrationProperties github(
            boolean enabled,
            String callbackBase,
            String webhookBase
    ) {
        return new GitHubIntegrationProperties(
                enabled,
                "123",
                "github-client",
                "github-secret",
                "private-key",
                "webhook-secret",
                "saga-app",
                callbackBase.isBlank() ? "" : callbackBase
                        + "/api/integrations/github/setup",
                "https://api.github.com",
                "https://github.com",
                callbackBase.isBlank() ? "" : callbackBase
                        + "/api/me/integrations/github/callback",
                callbackBase.isBlank() ? "" : callbackBase
                        + "/api/integrations/github/project/callback",
                webhookBase.isBlank() ? "" : webhookBase
                        + "/api/webhooks/github"
        );
    }

    private GitHubIntegrationProperties emptyGitHub(boolean enabled) {
        return new GitHubIntegrationProperties(
                enabled, "", "", "", "", "", "", "", "", "", "", "", ""
        );
    }
}
