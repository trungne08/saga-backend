package com.saga.be.integration.provider;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JiraProviderClient {

    URI authorizationUri(String state, String callbackUrl);

    JiraOAuthToken exchangeCode(String code, String callbackUrl);

    JiraOAuthToken refresh(String refreshToken);

    List<JiraAccessibleResource> accessibleResources(String accessToken);

    JiraUserIdentity currentUser(String accessToken, String cloudId);

    List<JiraProjectInfo> projects(String accessToken, String cloudId);

    String registerWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri
    );

    /**
     * Reuses a matching dynamic webhook where possible, and replaces only the
     * webhook identified by {@code existingWebhookId} when its configuration
     * no longer matches the current board.
     */
    JiraWebhookRegistration ensureWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri,
            String existingWebhookId
    );

    List<JiraWebhook> listWebhooks(String accessToken, String cloudId);

    void deleteWebhook(String accessToken, String cloudId, String webhookId);

    JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            LocalDateTime lowerBoundForJql,
            LocalDateTime upperBoundExclusiveForJql,
            String nextPageToken
    );
}
