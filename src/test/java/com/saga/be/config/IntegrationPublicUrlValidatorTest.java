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

    private IntegrationPublicUrlValidator validator(
            String publicBaseUrl,
            String jiraCallbackUrl
    ) {
        JiraIntegrationProperties jira = new JiraIntegrationProperties(
                null,
                null,
                null,
                null,
                null,
                jiraCallbackUrl,
                publicBaseUrl + "/api/webhooks/jira",
                null
        );
        GitHubIntegrationProperties github = new GitHubIntegrationProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                publicBaseUrl + "/api/integrations/github/setup",
                null,
                null,
                publicBaseUrl
                        + "/api/me/integrations/github/callback",
                publicBaseUrl
                        + "/api/integrations/github/project/callback"
        );
        return new IntegrationPublicUrlValidator(
                publicBaseUrl,
                jira,
                github,
                new MockEnvironment()
        );
    }
}
