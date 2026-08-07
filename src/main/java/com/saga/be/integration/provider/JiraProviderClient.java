package com.saga.be.integration.provider;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JiraProviderClient {

    URI authorizationUri(String state, String callbackUrl);

    JiraOAuthToken exchangeCode(String code, String callbackUrl);

    JiraOAuthToken refresh(String refreshToken);

    List<JiraAccessibleResource> accessibleResources(String accessToken);

    JiraUserIdentity currentUser(String accessToken, String cloudId);

    List<JiraProjectInfo> projects(String accessToken, String cloudId);

    List<JiraAgileBoardInfo> discoverAgileBoards(
            String accessToken, String cloudId, String jiraProjectId
    );

    List<JiraBoardFeature> getBoardFeatures(
            String accessToken, String cloudId, String boardId
    );

    List<JiraCreateIssueType> getCreateIssueTypes(
            String accessToken,
            String cloudId,
            String projectIdOrKey
    );

    List<JiraCreateField> getCreateFields(
            String accessToken,
            String cloudId,
            String projectIdOrKey,
            String issueTypeId
    );

    JiraIssueSnapshot getIssue(
            String accessToken,
            String cloudId,
            String issueIdOrKey
    );

    /**
     * Loads an issue together with the board-configured estimation field. The
     * default keeps provider fakes compatible; the Jira implementation adds
     * the field only after discovering its id from the board configuration.
     */
    default JiraIssueSnapshot getIssue(
            String accessToken,
            String cloudId,
            String issueIdOrKey,
            String estimationFieldId
    ) {
        return getIssue(accessToken, cloudId, issueIdOrKey);
    }

    List<JiraCreateField> getEditMetadata(
            String accessToken, String cloudId, String issueIdOrKey
    );

    JiraIssueReference createIssue(
            String accessToken, String cloudId, java.util.Map<String, Object> fields
    );

    void updateIssue(
            String accessToken, String cloudId, String issueIdOrKey, java.util.Map<String, Object> fields
    );

    void deleteIssue(String accessToken, String cloudId, String issueIdOrKey);

    List<JiraTransition> getTransitions(String accessToken, String cloudId, String issueIdOrKey);

    void transitionIssue(
            String accessToken, String cloudId, String issueIdOrKey, String transitionId
    );

    void assignIssue(
            String accessToken, String cloudId, String issueIdOrKey, String accountId
    );

    JiraSprintSnapshot getSprint(String accessToken, String cloudId, String sprintId);

    JiraSprintSnapshot createSprint(
            String accessToken,
            String cloudId,
            String boardId,
            String name,
            String goal,
            String startDate,
            String endDate
    );

    JiraSprintSnapshot updateSprint(
            String accessToken,
            String cloudId,
            String sprintId,
            java.util.Map<String, Object> changes
    );

    void deleteSprint(String accessToken, String cloudId, String sprintId);

    void moveIssuesToSprint(
            String accessToken,
            String cloudId,
            String sprintId,
            List<String> issueIdsOrKeys
    );

    void moveIssuesToBacklog(
            String accessToken,
            String cloudId,
            String boardId,
            List<String> issueIdsOrKeys
    );

    void estimateIssue(
            String accessToken,
            String cloudId,
            String boardId,
            String issueIdOrKey,
            Integer value
    );

    boolean supportsEstimation(String accessToken, String cloudId, String boardId);

    /** Returns the configured estimation field id, or {@code null} when absent. */
    default String estimationFieldId(String accessToken, String cloudId, String boardId) {
        return null;
    }

    /** Returns the Jira Software Sprint custom-field id, or {@code null} when absent. */
    default String sprintFieldId(String accessToken, String cloudId) {
        return null;
    }

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
            Instant lowerBoundUtc,
            Instant capturedUpperBoundUtc,
            String nextPageToken
    );

    default JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            Instant lowerBoundUtc,
            Instant capturedUpperBoundUtc,
            String nextPageToken,
            String sprintFieldId
    ) {
        return searchIssues(
                accessToken,
                cloudId,
                projectKey,
                lowerBoundUtc,
                capturedUpperBoundUtc,
                nextPageToken
        );
    }
}
