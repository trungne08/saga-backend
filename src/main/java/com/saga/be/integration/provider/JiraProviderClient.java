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

    void deleteWebhook(String accessToken, String cloudId, String webhookId);

    JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            LocalDateTime updatedAfter,
            String nextPageToken
    );
}
