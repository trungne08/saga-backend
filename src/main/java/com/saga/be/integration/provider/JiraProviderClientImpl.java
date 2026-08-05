package com.saga.be.integration.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saga.be.entity.value.TaskComponentSnapshot;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.sync.JiraSyncWindow;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(
        prefix = "app.integrations.jira",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class JiraProviderClientImpl implements JiraProviderClient {

    private static final int MAX_GET_ATTEMPTS = 3;
    private static final DateTimeFormatter JQL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    // Jira Cloud commonly returns offsets as +0700 rather than ISO's +07:00.
    private static final DateTimeFormatter JIRA_OFFSET_DATE_TIME =
            new DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .appendOffset("+HHMM", "Z")
                    .toFormatter();
    private static final String JIRA_SPRINT_FIELD_SCHEMA =
            "com.pyxis.greenhopper.jira:gh-sprint";
    private static final Logger log = LoggerFactory.getLogger(
            JiraProviderClientImpl.class
    );
    private static final List<String> WEBHOOK_EVENTS = List.of(
            "jira:issue_created",
            "jira:issue_updated",
            "jira:issue_deleted",
            "comment_created",
            "comment_updated",
            "comment_deleted",
            "sprint_created",
            "sprint_updated",
            "sprint_deleted",
            "sprint_started",
            "sprint_closed"
    );

    private final JiraIntegrationProperties properties;
    private final JiraTimeZoneProperties timeZoneProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public JiraProviderClientImpl(
            JiraIntegrationProperties properties,
            JiraTimeZoneProperties timeZoneProperties,
            IntegrationProperties integrationProperties,
            ObjectMapper objectMapper
    ) {
        this(
                properties,
                timeZoneProperties,
                objectMapper,
                RestClient.builder()
                        .requestFactory(requestFactory(integrationProperties))
                        .defaultHeader(
                                HttpHeaders.ACCEPT,
                                MediaType.APPLICATION_JSON_VALUE
                        )
                        .build()
        );
    }

    JiraProviderClientImpl(
            JiraIntegrationProperties properties,
            JiraTimeZoneProperties timeZoneProperties,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this.properties = properties;
        this.timeZoneProperties = timeZoneProperties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    JiraProviderClientImpl(
            JiraIntegrationProperties properties,
            ObjectMapper objectMapper,
            RestClient restClient
    ) {
        this(properties, new JiraTimeZoneProperties("UTC"), objectMapper, restClient);
    }

    @Override
    public URI authorizationUri(String state, String callbackUrl) {
        requireConfigured(properties.clientId(), "JIRA_CLIENT_ID");
        return UriComponentsBuilder.fromUriString(properties.authorizationUrl())
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", properties.clientId())
                .queryParam("scope", properties.scopes())
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .build()
                .encode()
                .toUri();
    }

    @Override
    public JiraOAuthToken exchangeCode(String code, String callbackUrl) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("client_id", requireConfigured(
                properties.clientId(),
                "JIRA_CLIENT_ID"
        ));
        body.put("client_secret", requireConfigured(
                properties.clientSecret(),
                "JIRA_CLIENT_SECRET"
        ));
        body.put("code", code);
        body.put("redirect_uri", callbackUrl);
        return tokenRequest(body);
    }

    @Override
    public JiraOAuthToken refresh(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", requireConfigured(
                properties.clientId(),
                "JIRA_CLIENT_ID"
        ));
        body.put("client_secret", requireConfigured(
                properties.clientSecret(),
                "JIRA_CLIENT_SECRET"
        ));
        body.put("refresh_token", refreshToken);
        return tokenRequest(body);
    }

    @Override
    public List<JiraAccessibleResource> accessibleResources(String accessToken) {
        JsonNode response = get(
                URI.create(properties.apiBaseUrl()
                        + "/oauth/token/accessible-resources"),
                accessToken
        );
        if (!response.isArray()) {
            throw providerResponseInvalid();
        }
        List<JiraAccessibleResource> resources = new ArrayList<>();
        response.forEach(resource -> resources.add(new JiraAccessibleResource(
                requiredText(resource, "id"),
                text(resource, "name"),
                text(resource, "url")
        )));
        return resources;
    }

    @Override
    public JiraUserIdentity currentUser(String accessToken, String cloudId) {
        JsonNode response = get(jiraUri(
                cloudId,
                "/rest/api/3/myself"
        ), accessToken);
        return new JiraUserIdentity(
                requiredText(response, "accountId"),
                text(response, "displayName"),
                text(response, "emailAddress")
        );
    }

    @Override
    public List<JiraProjectInfo> projects(String accessToken, String cloudId) {
        List<JiraProjectInfo> projects = new ArrayList<>();
        int startAt = 0;
        boolean last = false;
        while (!last) {
            URI uri = UriComponentsBuilder.fromUri(
                            jiraUri(cloudId, "/rest/api/3/project/search")
                    )
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", 50)
                    .queryParam("orderBy", "key")
                    .build()
                    .toUri();
            JsonNode response = get(uri, accessToken);
            JsonNode values = response.path("values");
            if (!values.isArray()) {
                throw providerResponseInvalid();
            }
            values.forEach(project -> projects.add(new JiraProjectInfo(
                    requiredText(project, "id"),
                    requiredText(project, "key"),
                    text(project, "name")
            )));
            last = response.path("isLast").asBoolean(true);
            startAt += values.size();
            if (values.isEmpty()) {
                last = true;
            }
        }
        return projects;
    }

    @Override
    public List<JiraCreateIssueType> getCreateIssueTypes(
            String accessToken,
            String cloudId,
            String projectIdOrKey
    ) {
        String project = requiredPathSegment(projectIdOrKey);
        List<JiraCreateIssueType> issueTypes = new ArrayList<>();
        int startAt = 0;
        while (true) {
            JsonNode response = get(UriComponentsBuilder.fromUri(jiraUri(
                            cloudId,
                            "/rest/api/3/issue/createmeta/" + project + "/issuetypes"
                    ))
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", 100)
                    .build()
                    .encode()
                    .toUri(), accessToken);
            JsonNode values = response.path("issueTypes");
            if (!values.isArray()) {
                throw providerResponseInvalid();
            }
            values.forEach(value -> issueTypes.add(new JiraCreateIssueType(
                    requiredText(value, "id"),
                    requiredText(value, "name"),
                    value.path("subtask").asBoolean(false),
                    text(value, "description")
            )));
            if (!nextMetadataPage(response, values.size(), startAt)) {
                return List.copyOf(issueTypes);
            }
            startAt += values.size();
        }
    }

    @Override
    public List<JiraCreateField> getCreateFields(
            String accessToken,
            String cloudId,
            String projectIdOrKey,
            String issueTypeId
    ) {
        String project = requiredPathSegment(projectIdOrKey);
        String issueType = requiredPathSegment(issueTypeId);
        List<JiraCreateField> fields = new ArrayList<>();
        int startAt = 0;
        while (true) {
            JsonNode response = get(UriComponentsBuilder.fromUri(jiraUri(
                            cloudId,
                            "/rest/api/3/issue/createmeta/" + project
                                    + "/issuetypes/" + issueType
                    ))
                    .queryParam("startAt", startAt)
                    .queryParam("maxResults", 100)
                    .build()
                    .encode()
                    .toUri(), accessToken);
            JsonNode values = response.path("fields");
            if (!values.isArray()) {
                throw providerResponseInvalid();
            }
            values.forEach(value -> fields.add(toCreateField(value)));
            if (!nextMetadataPage(response, values.size(), startAt)) {
                return List.copyOf(fields);
            }
            startAt += values.size();
        }
    }

    @Override
    public JiraIssueSnapshot getIssue(
            String accessToken,
            String cloudId,
            String issueIdOrKey
    ) {
        return getIssue(accessToken, cloudId, issueIdOrKey, null);
    }

    @Override
    public JiraIssueSnapshot getIssue(
            String accessToken,
            String cloudId,
            String issueIdOrKey,
            String estimationFieldId
    ) {
        String issue = requiredPathSegment(issueIdOrKey);
        URI uri = UriComponentsBuilder.fromUri(jiraUri(
                        cloudId,
                        "/rest/api/3/issue/" + issue
                ))
                .queryParam("fields", issueFields(estimationFieldId))
                .build()
                .encode()
                .toUri();
        return toIssue(get(uri, accessToken), estimationFieldId);
    }

    @Override
    public List<JiraCreateField> getEditMetadata(String accessToken, String cloudId, String issueIdOrKey) {
        JsonNode fields = get(jiraUri(cloudId, "/rest/api/3/issue/"
                + requiredPathSegment(issueIdOrKey) + "/editmeta"), accessToken).path("fields");
        if (!fields.isObject()) throw providerResponseInvalid();
        List<JiraCreateField> result = new ArrayList<>();
        fields.properties().forEach(entry -> {
            JsonNode field = entry.getValue().deepCopy();
            if (!field.hasNonNull("key")) ((tools.jackson.databind.node.ObjectNode) field).put("key", entry.getKey());
            result.add(toCreateField(field));
        });
        return List.copyOf(result);
    }

    @Override
    public JiraIssueReference createIssue(String accessToken, String cloudId, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) throw IntegrationException.invalid("JIRA_REQUEST_INVALID", "Jira issue fields are required");
        JsonNode response = postJson(jiraUri(cloudId, "/rest/api/3/issue"), accessToken, Map.of("fields", fields));
        return new JiraIssueReference(requiredText(response, "id"), requiredText(response, "key"));
    }

    @Override
    public void updateIssue(String accessToken, String cloudId, String issueIdOrKey, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) throw IntegrationException.invalid("JIRA_REQUEST_INVALID", "Jira issue fields are required");
        putNoContent(jiraUri(cloudId, "/rest/api/3/issue/" + requiredPathSegment(issueIdOrKey)), accessToken, Map.of("fields", fields));
    }

    @Override
    public void deleteIssue(String accessToken, String cloudId, String issueIdOrKey) {
        delete(jiraUri(cloudId, "/rest/api/3/issue/" + requiredPathSegment(issueIdOrKey)), accessToken);
    }

    @Override
    public List<JiraTransition> getTransitions(String accessToken, String cloudId, String issueIdOrKey) {
        JsonNode values = get(jiraUri(cloudId, "/rest/api/3/issue/"
                + requiredPathSegment(issueIdOrKey) + "/transitions"), accessToken).path("transitions");
        if (!values.isArray()) throw providerResponseInvalid();
        List<JiraTransition> transitions = new ArrayList<>();
        values.forEach(value -> transitions.add(new JiraTransition(requiredText(value, "id"), requiredText(value, "name"),
                nestedText(value, "to", "id"), nestedText(value, "to", "name"))));
        return List.copyOf(transitions);
    }

    @Override
    public void transitionIssue(String accessToken, String cloudId, String issueIdOrKey, String transitionId) {
        postNoContent(jiraUri(cloudId, "/rest/api/3/issue/" + requiredPathSegment(issueIdOrKey)
                + "/transitions"), accessToken, Map.of("transition", Map.of("id", requiredPathSegment(transitionId))));
    }

    @Override
    public void assignIssue(String accessToken, String cloudId, String issueIdOrKey, String accountId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", accountId == null ? null : requiredStableId(accountId));
        putNoContent(jiraUri(cloudId, "/rest/api/3/issue/" + requiredPathSegment(issueIdOrKey)
                + "/assignee"), accessToken, body);
    }

    @Override
    public JiraSprintSnapshot getSprint(String accessToken, String cloudId, String sprintId) {
        return toSprint(get(jiraUri(
                cloudId, "/rest/agile/1.0/sprint/" + requiredPathSegment(sprintId)
        ), accessToken));
    }

    @Override
    public JiraSprintSnapshot createSprint(
            String accessToken,
            String cloudId,
            String boardId,
            String name,
            String goal,
            String startDate,
            String endDate
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originBoardId", requiredPathSegment(boardId));
        body.put("name", requiredNonBlank(name));
        if (goal != null) body.put("goal", goal);
        if (startDate != null) body.put("startDate", startDate);
        if (endDate != null) body.put("endDate", endDate);
        return toSprint(postJson(jiraUri(
                cloudId, "/rest/agile/1.0/sprint"
        ), accessToken, body));
    }

    @Override
    public JiraSprintSnapshot updateSprint(
            String accessToken,
            String cloudId,
            String sprintId,
            Map<String, Object> changes
    ) {
        if (changes == null || changes.isEmpty()) {
            throw IntegrationException.invalid("JIRA_SPRINT_UPDATE_EMPTY", "No sprint changes were supplied");
        }
        return toSprint(patchJson(jiraUri(
                cloudId, "/rest/agile/1.0/sprint/" + requiredPathSegment(sprintId)
        ), accessToken, changes));
    }

    @Override
    public void deleteSprint(String accessToken, String cloudId, String sprintId) {
        delete(jiraUri(cloudId, "/rest/agile/1.0/sprint/" + requiredPathSegment(sprintId)), accessToken);
    }

    @Override
    public void moveIssuesToSprint(
            String accessToken, String cloudId, String sprintId, List<String> issueIdsOrKeys
    ) {
        postNoContent(jiraUri(
                cloudId, "/rest/agile/1.0/sprint/" + requiredPathSegment(sprintId) + "/issue"
        ), accessToken, Map.of("issues", safeIssueIds(issueIdsOrKeys)));
    }

    @Override
    public void moveIssuesToBacklog(
            String accessToken, String cloudId, String boardId, List<String> issueIdsOrKeys
    ) {
        postNoContent(jiraUri(
                cloudId, "/rest/agile/1.0/backlog/" + requiredPathSegment(boardId) + "/issue"
        ), accessToken, Map.of("issues", safeIssueIds(issueIdsOrKeys)));
    }

    @Override
    public void estimateIssue(
            String accessToken,
            String cloudId,
            String boardId,
            String issueIdOrKey,
            Integer value
    ) {
        if (value == null || value < 0) {
            throw IntegrationException.invalid("JIRA_ESTIMATION_INVALID", "The Jira estimation is invalid");
        }
        URI uri = UriComponentsBuilder.fromUri(jiraUri(
                        cloudId,
                        "/rest/agile/1.0/issue/" + requiredPathSegment(issueIdOrKey)
                                + "/estimation"
                ))
                .queryParam("boardId", requiredPathSegment(boardId))
                .build().encode().toUri();
        putNoContent(uri, accessToken, Map.of("value", value));
    }

    @Override
    public boolean supportsEstimation(String accessToken, String cloudId, String boardId) {
        JsonNode estimation = estimation(accessToken, cloudId, boardId);
        if (estimation.isMissingNode() || estimation.isNull() || !estimation.isObject()) {
            return false;
        }
        String type = text(estimation, "type");
        return type != null && !type.isBlank() && !"none".equalsIgnoreCase(type);
    }

    @Override
    public String estimationFieldId(String accessToken, String cloudId, String boardId) {
        JsonNode estimation = estimation(accessToken, cloudId, boardId);
        String type = text(estimation, "type");
        if (type == null || type.isBlank() || "none".equalsIgnoreCase(type)) {
            return null;
        }
        return nestedText(estimation, "field", "fieldId");
    }

    @Override
    public String sprintFieldId(String accessToken, String cloudId) {
        JsonNode fields = get(jiraUri(cloudId, "/rest/api/3/field"), accessToken);
        if (!fields.isArray()) {
            throw providerResponseInvalid();
        }
        for (JsonNode field : fields) {
            if (JIRA_SPRINT_FIELD_SCHEMA.equals(nestedText(field, "schema", "custom"))) {
                return requiredStableId(requiredText(field, "id"));
            }
        }
        return null;
    }

    @Override
    public String registerWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        return createWebhook(accessToken, cloudId, projectKey, callbackUri);
    }

    @Override
    public JiraWebhookRegistration ensureWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri,
            String existingWebhookId
    ) {
        List<JiraWebhook> webhooks = listWebhooks(
                accessToken,
                cloudId,
                projectKey,
                callbackUri
        );
        JiraWebhook exactMatch = webhooks.stream()
                .filter(webhook -> matchesWebhook(
                        webhook,
                        projectKey,
                        callbackUri,
                        true
                ))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            return new JiraWebhookRegistration(exactMatch.id(), false);
        }

        JiraWebhook existing = webhooks.stream()
                .filter(webhook -> webhook.id().equals(existingWebhookId))
                .findFirst()
                .orElse(null);
        if (existing != null && matchesWebhook(
                existing,
                projectKey,
                callbackUri,
                false
        )) {
            return new JiraWebhookRegistration(existing.id(), false);
        }
        if (existing != null) {
            // This is the only deletion path: the ID is stored on this board
            // and was returned by Jira for this OAuth application.
            deleteWebhook(accessToken, cloudId, existing.id());
        }
        return new JiraWebhookRegistration(
                createWebhook(accessToken, cloudId, projectKey, callbackUri),
                true
        );
    }

    @Override
    public List<JiraWebhook> listWebhooks(String accessToken, String cloudId) {
        return listWebhooks(accessToken, cloudId, null, null);
    }

    private List<JiraWebhook> listWebhooks(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        List<JiraWebhook> webhooks = new ArrayList<>();
        int requestedStartAt = 0;
        boolean isLast = false;
        while (!isLast) {
            URI uri = UriComponentsBuilder.fromUri(
                            jiraUri(cloudId, "/rest/api/3/webhook")
                    )
                    .queryParam("startAt", requestedStartAt)
                    .queryParam("maxResults", 100)
                    .build()
                    .encode()
                    .toUri();
            JiraWebhookPage page = parseWebhookPage(
                    getWebhookJson(
                            uri,
                            accessToken,
                            cloudId,
                            projectKey,
                            callbackUri
                    ),
                    cloudId,
                    projectKey,
                    callbackUri
            );
            webhooks.addAll(page.values());
            isLast = Boolean.TRUE.equals(page.isLast());
            if (!isLast) {
                int pageStartAt = page.startAt() == null
                        ? requestedStartAt
                        : page.startAt();
                int maxResults = page.maxResults() == null
                        ? 100
                        : page.maxResults();
                if (maxResults <= 0 || pageStartAt + maxResults <= requestedStartAt) {
                    throw invalidWebhookResponse(
                            "GET",
                            cloudId,
                            projectKey,
                            callbackUri,
                            "pagination did not advance"
                    );
                }
                requestedStartAt = pageStartAt + maxResults;
            }
        }
        return List.copyOf(webhooks);
    }

    private String createWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("jqlFilter", webhookJql(projectKey));
        webhook.put("events", WEBHOOK_EVENTS);
        JsonNode response = postWebhookJson(
                jiraUri(cloudId, "/rest/api/3/webhook"),
                accessToken,
                cloudId,
                projectKey,
                callbackUri,
                Map.of(
                        "url", callbackUri.toString(),
                        "webhooks", List.of(webhook)
                )
        );
        JsonNode results = response.path("webhookRegistrationResult");
        if (!results.isArray() || results.isEmpty()) {
            throw providerResponseInvalid();
        }
        JsonNode first = results.get(0);
        if (first.hasNonNull("errors") && !first.path("errors").isEmpty()) {
            throw webhookRegistrationRejected(
                    200,
                    first,
                    cloudId,
                    projectKey,
                    callbackUri
            );
        }
        String webhookId = text(first, "createdWebhookId");
        if (webhookId == null || webhookId.isBlank()) {
            throw providerResponseInvalid();
        }
        return webhookId;
    }

    @Override
    public void deleteWebhook(
            String accessToken,
            String cloudId,
            String webhookId
    ) {
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri(jiraUri(cloudId, "/rest/api/3/webhook"))
                    .headers(headers -> bearer(headers, accessToken))
                    .body(Map.of("webhookIds", List.of(Long.parseLong(webhookId))))
                    .retrieve()
                    .toBodilessEntity();
        } catch (NumberFormatException exception) {
            throw providerResponseInvalid();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    @Override
    public JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            Instant lowerBoundUtc,
            Instant capturedUpperBoundUtc,
            String nextPageToken
    ) {
        return searchIssues(
                accessToken,
                cloudId,
                projectKey,
                lowerBoundUtc,
                capturedUpperBoundUtc,
                nextPageToken,
                null
        );
    }

    @Override
    public JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            Instant lowerBoundUtc,
            Instant capturedUpperBoundUtc,
            String nextPageToken,
            String sprintFieldId
    ) {
        ZoneId jiraZoneId = jiraZoneId();
        LocalDateTime lowerBoundForJql = JiraSyncWindow.lowerBoundForJql(
                lowerBoundUtc,
                jiraZoneId
        );
        LocalDateTime upperBoundExclusiveForJql =
                JiraSyncWindow.upperBoundExclusiveForJql(
                        capturedUpperBoundUtc,
                        jiraZoneId
                );
        StringBuilder jql = new StringBuilder(
                "project = " + safeJqlKey(projectKey)
        );
        if (lowerBoundForJql != null) {
            jql.append(" AND updated >= \"")
                    .append(JQL_DATE_TIME.format(lowerBoundForJql))
                    .append("\"");
        }
        jql.append(" AND updated < \"")
                .append(JQL_DATE_TIME.format(upperBoundExclusiveForJql))
                .append("\"");
        jql.append(" ORDER BY updated ASC, id ASC");

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(
                        jiraUri(cloudId, "/rest/api/3/search/jql")
                )
                .queryParam("jql", jql)
                .queryParam("maxResults", 100)
                .queryParam("fields", issueFields(null, sprintFieldId));
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            builder.queryParam("nextPageToken", nextPageToken);
        }

        JsonNode response = get(builder.build().encode().toUri(), accessToken);
        JsonNode issues = response.path("issues");
        if (!issues.isArray()) {
            throw providerResponseInvalid();
        }
        List<JiraIssueSnapshot> snapshots = new ArrayList<>();
        issues.forEach(issue -> snapshots.add(toIssue(issue, null, sprintFieldId)));
        String next = text(response, "nextPageToken");
        return new JiraIssuePage(
                snapshots,
                next,
                response.path("isLast").asBoolean(next == null || next.isBlank())
        );
    }

    private JiraOAuthToken tokenRequest(Map<String, Object> body) {
        try {
            JsonNode response = restClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw providerResponseInvalid();
            }
            String accessToken = requiredText(response, "access_token");
            String refreshToken = text(response, "refresh_token");
            long expiresIn = response.path("expires_in").asLong(3600);
            Set<String> scopes = new LinkedHashSet<>();
            String scopeText = text(response, "scope");
            if (scopeText != null) {
                scopes.addAll(Arrays.asList(scopeText.split("\\s+")));
            }
            return new JiraOAuthToken(
                    accessToken,
                    refreshToken,
                    Instant.now().plusSeconds(expiresIn),
                    Set.copyOf(scopes)
            );
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private JsonNode get(URI uri, String token) {
        for (int attempt = 1; attempt <= MAX_GET_ATTEMPTS; attempt++) {
            try {
                String response = restClient.get()
                        .uri(uri)
                        .headers(headers -> bearer(headers, token))
                        .retrieve()
                        .body(String.class);
                return parsedJson(response);
            } catch (RestClientResponseException exception) {
                if (attempt == MAX_GET_ATTEMPTS || !retryable(exception)) {
                    throw translate(exception);
                }
            } catch (RestClientException exception) {
                throw providerUnavailable();
            }
        }
        throw IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE");
    }

    private JsonNode parsedJson(String response) {
        if (response == null || response.isBlank()) {
            throw providerResponseInvalid();
        }
        try {
            JsonNode json = objectMapper.readTree(response);
            if (json == null) {
                throw providerResponseInvalid();
            }
            return json;
        } catch (RuntimeException exception) {
            throw providerResponseInvalid();
        }
    }

    private JsonNode postJson(URI uri, String token, Object body) {
        try {
            JsonNode response = restClient.post()
                    .uri(uri)
                    .headers(headers -> bearer(headers, token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw providerResponseInvalid();
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private JsonNode patchJson(URI uri, String token, Object body) {
        try {
            JsonNode response = restClient.patch()
                    .uri(uri)
                    .headers(headers -> bearer(headers, token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) throw providerResponseInvalid();
            return response;
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private void postNoContent(URI uri, String token, Object body) {
        try {
            restClient.post().uri(uri).headers(headers -> bearer(headers, token))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private void putNoContent(URI uri, String token, Object body) {
        try {
            restClient.put().uri(uri).headers(headers -> bearer(headers, token))
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private void delete(URI uri, String token) {
        try {
            restClient.delete().uri(uri).headers(headers -> bearer(headers, token))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            throw providerUnavailable();
        }
    }

    private String getWebhookJson(
            URI uri,
            String token,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        try {
            String response = restClient.get()
                    .uri(uri)
                    .headers(headers -> bearer(headers, token))
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw invalidWebhookResponse(
                        "GET",
                        cloudId,
                        projectKey,
                        callbackUri,
                        "empty response body"
                );
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw webhookHttpFailure(
                    exception,
                    cloudId,
                    projectKey,
                    callbackUri
            );
        }
    }

    private JsonNode postWebhookJson(
            URI uri,
            String token,
            String cloudId,
            String projectKey,
            URI callbackUri,
            Object body
    ) {
        try {
            String response = restClient.post()
                    .uri(uri)
                    .headers(headers -> bearer(headers, token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseWebhookJson(
                    response,
                    "POST",
                    cloudId,
                    projectKey,
                    callbackUri
            );
        } catch (RestClientResponseException exception) {
            throw webhookHttpFailure(
                    exception,
                    cloudId,
                    projectKey,
                    callbackUri
            );
        }
    }

    private JiraWebhookPage parseWebhookPage(
            String body,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        try {
            JiraWebhookPage page = objectMapper.readValue(
                    body,
                    JiraWebhookPage.class
            );
            if (page == null || page.values() == null) {
                throw invalidWebhookResponse(
                        "GET",
                        cloudId,
                        projectKey,
                        callbackUri,
                        "values is missing",
                        body
                );
            }
            return page;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidWebhookResponse(
                    "GET",
                    cloudId,
                    projectKey,
                    callbackUri,
                    "malformed PageBeanWebhook response",
                    body
            );
        }
    }

    private JsonNode parseWebhookJson(
            String body,
            String operation,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        if (body == null || body.isBlank()) {
            throw invalidWebhookResponse(
                    operation,
                    cloudId,
                    projectKey,
                    callbackUri,
                    "empty response body"
            );
        }
        try {
            return objectMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw invalidWebhookResponse(
                    operation,
                    cloudId,
                    projectKey,
                    callbackUri,
                    "malformed JSON response",
                    body
            );
        }
    }

    private IntegrationException invalidWebhookResponse(
            String operation,
            String cloudId,
            String projectKey,
            URI callbackUri,
            String reason
    ) {
        return invalidWebhookResponse(
                operation,
                cloudId,
                projectKey,
                callbackUri,
                reason,
                null
        );
    }

    private IntegrationException invalidWebhookResponse(
            String operation,
            String cloudId,
            String projectKey,
            URI callbackUri,
            String reason,
            String body
    ) {
        log.warn(
                "Jira dynamic webhook response invalid: operation={}, "
                        + "providerStatus=2xx, cloudId={}, projectKey={}, "
                        + "callbackHost={}, reason={}, body={}",
                operation,
                cloudId,
                projectKey,
                callbackUri == null ? "" : callbackUri.getHost(),
                reason,
                redactAndTruncate(body)
        );
        return providerResponseInvalid();
    }

    private String redactAndTruncate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String redacted = body
                .replaceAll(
                        "(?i)\\\"(access_token|refresh_token|token|authorization|client_secret)\\\"\\s*:\\s*\\\"[^\\\"]*\\\"",
                        "\\\"$1\\\":\\\"<redacted>\\\""
                )
                .replaceAll(
                        "(?i)([?&](?:access_token|refresh_token|token|authorization|client_secret)=)[^&\\\"\\s]*",
                        "$1<redacted>"
                )
                .replaceAll("(?i)Bearer\\s+[^\\s\\\"]+", "Bearer <redacted>")
                .replaceAll("[\\r\\n]", " ");
        return redacted.length() <= 1024
                ? redacted
                : redacted.substring(0, 1024) + "…";
    }

    private IntegrationException webhookHttpFailure(
            RestClientResponseException exception,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        JsonNode body = responseBody(exception);
        int status = exception.getStatusCode().value();
        logWebhookFailure(
                status,
                body,
                cloudId,
                projectKey,
                callbackUri
        );
        if (status == 401 || status == 403) {
            return new IntegrationException(
                    HttpStatus.FORBIDDEN,
                    "JIRA_WEBHOOK_PERMISSION_DENIED",
                    "Jira denied webhook access; reconnect Jira with webhook scope"
            );
        }
        if (status == 400) {
            return IntegrationException.invalid(
                    "JIRA_WEBHOOK_REGISTRATION_INVALID",
                    "Jira rejected the webhook registration request"
            );
        }
        if (status == 429 || isWebhookLimit(providerErrors(body))) {
            return new IntegrationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "JIRA_WEBHOOK_LIMIT_REACHED",
                    "Jira dynamic webhook limit was reached"
            );
        }
        return IntegrationException.unavailable("JIRA_WEBHOOK_PROVIDER_UNAVAILABLE");
    }

    private IntegrationException webhookRegistrationRejected(
            int providerStatus,
            JsonNode body,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        List<String> errors = providerErrors(body);
        logWebhookFailure(
                providerStatus,
                body,
                cloudId,
                projectKey,
                callbackUri
        );
        if (isWebhookLimit(errors)) {
            return new IntegrationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "JIRA_WEBHOOK_LIMIT_REACHED",
                    "Jira dynamic webhook limit was reached"
            );
        }
        return IntegrationException.conflict(
                "JIRA_WEBHOOK_REGISTRATION_REJECTED",
                "Jira rejected the webhook registration: " + errorSummary(errors)
        );
    }

    private void logWebhookFailure(
            int providerStatus,
            JsonNode body,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        log.warn(
                "Jira dynamic webhook registration rejected: providerStatus={}, "
                        + "cloudId={}, projectKey={}, callbackHost={}, errors={}",
                providerStatus,
                cloudId,
                projectKey,
                callbackUri == null ? "" : callbackUri.getHost(),
                errorSummary(providerErrors(body))
        );
    }

    private JsonNode responseBody(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            return body == null || body.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(body);
        } catch (RuntimeException ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> providerErrors(JsonNode response) {
        List<String> values = new ArrayList<>();
        collectErrors(response == null ? null : response.path("errors"), values);
        collectErrors(response == null ? null : response.path("errorMessages"), values);
        if (values.isEmpty() && response != null) {
            collectErrors(response.path("message"), values);
        }
        return List.copyOf(values);
    }

    private void collectErrors(JsonNode node, List<String> values) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isValueNode()) {
            String value = node.asText();
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(value -> collectErrors(value, values));
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> collectErrors(
                    entry.getValue(),
                    values
            ));
        }
    }

    private String errorSummary(List<String> errors) {
        if (errors.isEmpty()) {
            return "no provider error detail";
        }
        String summary = String.join("; ", errors)
                .replaceAll("[\\r\\n]", " ");
        return summary.length() <= 512 ? summary : summary.substring(0, 512);
    }

    private boolean isWebhookLimit(List<String> errors) {
        String detail = String.join(" ", errors).toLowerCase(java.util.Locale.ROOT);
        return detail.contains("webhook")
                && (detail.contains("limit")
                        || detail.contains("maximum")
                        || detail.contains("too many"));
    }

    private boolean matchesWebhook(
            JiraWebhook webhook,
            String projectKey,
            URI callbackUri,
            boolean exactUrl
    ) {
        if (!webhookJql(projectKey).equals(webhook.jqlFilter())
                || !new LinkedHashSet<>(WEBHOOK_EVENTS).equals(
                        new LinkedHashSet<>(webhook.events())
                )) {
            return false;
        }
        if (exactUrl) {
            return callbackUri.toString().equals(webhook.url());
        }
        try {
            URI existing = URI.create(webhook.url());
            return sameCallbackEndpoint(existing, callbackUri);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean sameCallbackEndpoint(URI existing, URI expected) {
        return java.util.Objects.equals(existing.getScheme(), expected.getScheme())
                && java.util.Objects.equals(existing.getHost(), expected.getHost())
                && existing.getPort() == expected.getPort()
                && java.util.Objects.equals(existing.getPath(), expected.getPath());
    }

    private String webhookJql(String projectKey) {
        return "project = " + safeJqlKey(projectKey);
    }

    private void bearer(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    }

    private URI jiraUri(String cloudId, String path) {
        return UriComponentsBuilder.fromUriString(properties.apiBaseUrl())
                .pathSegment("ex", "jira", cloudId)
                .path(path)
                .build()
                .encode()
                .toUri();
    }

    private JiraCreateField toCreateField(JsonNode field) {
        if (!field.isObject()) {
            throw providerResponseInvalid();
        }
        JsonNode schema = field.path("schema");
        if (!schema.isMissingNode() && !schema.isNull() && !schema.isObject()) {
            throw providerResponseInvalid();
        }
        return new JiraCreateField(
                requiredText(field, "key"),
                requiredText(field, "name"),
                field.path("required").asBoolean(false),
                schemaText(schema, "type"),
                schemaText(schema, "items"),
                allowedValues(field.path("allowedValues"))
        );
    }

    private JiraSprintSnapshot toSprint(JsonNode sprint) {
        return new JiraSprintSnapshot(
                requiredText(sprint, "id"),
                requiredText(sprint, "name"),
                text(sprint, "state"),
                text(sprint, "goal"),
                parseSprintDateTime(text(sprint, "startDate")),
                parseSprintDateTime(text(sprint, "endDate")),
                parseSprintDateTime(text(sprint, "completeDate")),
                text(sprint, "originBoardId")
        );
    }

    private String schemaText(JsonNode schema, String field) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return null;
        }
        return scalarText(schema.get(field));
    }

    private List<JiraCreateFieldAllowedValue> allowedValues(JsonNode values) {
        if (values == null || values.isMissingNode() || values.isNull()) {
            return List.of();
        }
        if (!values.isArray()) {
            throw providerResponseInvalid();
        }
        List<JiraCreateFieldAllowedValue> sanitized = new ArrayList<>();
        for (JsonNode value : values) {
            if (value.isValueNode()) {
                sanitized.add(new JiraCreateFieldAllowedValue(
                        null, scalarText(value), null
                ));
                continue;
            }
            if (!value.isObject()) {
                throw providerResponseInvalid();
            }
            String id = scalarText(value.get("id"));
            String displayValue = scalarText(value.get("value"));
            String name = scalarText(value.get("name"));
            if (id == null && displayValue == null && name == null) {
                throw providerResponseInvalid();
            }
            sanitized.add(new JiraCreateFieldAllowedValue(
                    id, displayValue, name
            ));
        }
        return List.copyOf(sanitized);
    }

    private String scalarText(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isValueNode()) {
            throw providerResponseInvalid();
        }
        return value.asText();
    }

    private boolean nextMetadataPage(
            JsonNode response,
            int returned,
            int requestedStartAt
    ) {
        if (returned == 0) {
            return false;
        }
        int total = response.path("total").asInt(-1);
        if (total >= 0) {
            if (total < requestedStartAt + returned) {
                throw providerResponseInvalid();
            }
            return requestedStartAt + returned < total;
        }
        int maxResults = response.path("maxResults").asInt(-1);
        return maxResults > 0 && returned == maxResults;
    }

    private JsonNode estimation(String accessToken, String cloudId, String boardId) {
        return get(jiraUri(
                cloudId, "/rest/agile/1.0/board/" + requiredPathSegment(boardId) + "/configuration"
        ), accessToken).path("estimation");
    }

    private String issueFields() {
        return issueFields(null);
    }

    private String issueFields(String estimationFieldId) {
        return issueFields(estimationFieldId, null);
    }

    private String issueFields(String estimationFieldId, String sprintFieldId) {
        return "summary,issuetype,status,priority,assignee,reporter,"
                + "duedate,created,updated,resolutiondate,resolution,"
                + "labels,components,description"
                + (estimationFieldId == null ? "" : "," + requiredStableId(estimationFieldId))
                + (sprintFieldId == null ? "" : "," + requiredStableId(sprintFieldId));
    }

    private JiraIssueSnapshot toIssue(JsonNode issue) {
        return toIssue(issue, null);
    }

    private JiraIssueSnapshot toIssue(JsonNode issue, String estimationFieldId) {
        return toIssue(issue, estimationFieldId, null);
    }

    private JiraIssueSnapshot toIssue(
            JsonNode issue,
            String estimationFieldId,
            String sprintFieldId
    ) {
        JsonNode fields = issue.path("fields");
        JsonNode sprint = sprintValue(fields, sprintFieldId);
        String updatedText = text(fields, "updated");
        LocalDateTime updatedAt = parseDateOrDateTime(updatedText);
        Instant updatedAtUtc = parseInstant(updatedText);
        if (updatedAt == null) {
            throw providerResponseInvalid();
        }
        return new JiraIssueSnapshot(
                requiredText(issue, "id"),
                requiredText(issue, "key"),
                text(fields, "summary"),
                nestedText(fields, "issuetype", "name"),
                nestedText(fields, "status", "name"),
                nestedText(fields, "priority", "name"),
                estimationFieldId == null ? null : integer(fields.path(estimationFieldId)),
                nestedText(fields, "assignee", "accountId"),
                nestedText(fields, "reporter", "accountId"),
                parseDateOrDateTime(text(fields, "duedate")),
                parseDateOrDateTime(text(fields, "created")),
                updatedAt,
                parseDateOrDateTime(text(fields, "resolutiondate")),
                nestedText(fields, "resolution", "name"),
                sprint == null ? null : scalarText(sprint.get("id")),
                sprint == null ? null : scalarText(sprint.get("name")),
                updatedAtUtc,
                labels(fields.path("labels")),
                description(fields.path("description")),
                components(fields.path("components"))
        );
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isInt()) {
            throw providerResponseInvalid();
        }
        return node.asInt();
    }

    private List<String> labels(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw providerResponseInvalid();
        }
        List<String> labels = new ArrayList<>();
        for (JsonNode label : node) {
            if (!label.isTextual()) {
                throw providerResponseInvalid();
            }
            labels.add(label.asText());
        }
        return List.copyOf(labels);
    }

    private List<TaskComponentSnapshot> components(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw providerResponseInvalid();
        }
        List<TaskComponentSnapshot> components = new ArrayList<>();
        for (JsonNode component : node) {
            if (!component.isObject()) {
                throw providerResponseInvalid();
            }
            components.add(new TaskComponentSnapshot(
                    requiredText(component, "id"),
                    requiredText(component, "name")
            ));
        }
        return List.copyOf(components);
    }

    private String description(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (!node.isObject()) {
            throw providerResponseInvalid();
        }
        StringBuilder text = new StringBuilder();
        appendAdfText(node, text);
        return text.toString();
    }

    private void appendAdfText(JsonNode node, StringBuilder text) {
        JsonNode value = node.path("text");
        if (!value.isMissingNode() && !value.isNull()) {
            if (!value.isTextual()) {
                throw providerResponseInvalid();
            }
            text.append(value.asText());
        }
        JsonNode content = node.path("content");
        if (content.isMissingNode() || content.isNull()) {
            return;
        }
        if (!content.isArray()) {
            throw providerResponseInvalid();
        }
        for (JsonNode child : content) {
            if (!child.isObject()) {
                throw providerResponseInvalid();
            }
            appendAdfText(child, text);
        }
    }

    private JsonNode first(JsonNode node) {
        return node.isArray() && !node.isEmpty() ? node.get(0) : null;
    }

    private Integer nullableInt(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull()
                ? null
                : node.asInt();
    }

    private LocalDateTime parseDateOrDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException isoException) {
            try {
                return OffsetDateTime.parse(value, JIRA_OFFSET_DATE_TIME)
                        .toLocalDateTime();
            } catch (RuntimeException jiraOffsetException) {
                try {
                    return LocalDate.parse(value).atStartOfDay();
                } catch (RuntimeException dateException) {
                    throw providerResponseInvalid();
                }
            }
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException isoException) {
            try {
                return OffsetDateTime.parse(value, JIRA_OFFSET_DATE_TIME)
                        .toInstant();
            } catch (RuntimeException jiraOffsetException) {
                throw providerResponseInvalid();
            }
        }
    }

    private ZoneId jiraZoneId() {
        try {
            return ZoneId.of(timeZoneProperties.timeZone());
        } catch (RuntimeException exception) {
            throw IntegrationException.invalid(
                    "JIRA_TIME_ZONE_INVALID",
                    "The Jira time zone is invalid"
            );
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) {
            throw providerResponseInvalid();
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String nestedText(JsonNode node, String parent, String field) {
        JsonNode value = node == null ? null : node.path(parent);
        return value == null || value.isMissingNode() || value.isNull()
                ? null
                : text(value, field);
    }

    private String safeJqlKey(String key) {
        if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_]{0,127}")) {
            throw IntegrationException.invalid(
                    "JIRA_PROJECT_KEY_INVALID",
                    "The Jira project key is invalid"
            );
        }
        return key;
    }

    private LocalDateTime parseSprintDateTime(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw providerResponseInvalid();
        }
        try {
            return LocalDateTime.ofInstant(
                    OffsetDateTime.parse(value).toInstant(),
                    ZoneOffset.UTC
            );
        } catch (RuntimeException isoException) {
            try {
                return LocalDateTime.ofInstant(
                        OffsetDateTime.parse(value, JIRA_OFFSET_DATE_TIME).toInstant(),
                        ZoneOffset.UTC
                );
            } catch (RuntimeException jiraOffsetException) {
                throw providerResponseInvalid();
            }
        }
    }

    private JsonNode sprintValue(JsonNode fields, String sprintFieldId) {
        if (sprintFieldId == null) {
            return null;
        }
        JsonNode value = fields.path(sprintFieldId);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isObject()) {
            return scalarText(value.get("id")) == null ? null : value;
        }
        if (!value.isArray()) {
            throw providerResponseInvalid();
        }
        for (int index = value.size() - 1; index >= 0; index--) {
            JsonNode sprint = value.get(index);
            if (!sprint.isObject()) {
                throw providerResponseInvalid();
            }
            if (scalarText(sprint.get("id")) != null) {
                return sprint;
            }
        }
        return null;
    }

    private String requiredPathSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,254}")) {
            throw IntegrationException.invalid(
                    "JIRA_IDENTIFIER_INVALID",
                    "The Jira identifier is invalid"
            );
        }
        return value;
    }

    private String requiredNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw IntegrationException.invalid("JIRA_REQUEST_INVALID", "A required Jira value is missing");
        }
        return value.trim();
    }

    private String requiredStableId(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl)) {
            throw IntegrationException.invalid("JIRA_IDENTIFIER_INVALID", "The Jira identifier is invalid");
        }
        return value.trim();
    }

    private List<String> safeIssueIds(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 50) {
            throw IntegrationException.invalid("JIRA_ISSUE_LIST_INVALID", "Between 1 and 50 Jira issues are required");
        }
        return values.stream().map(this::requiredPathSegment).toList();
    }

    private boolean retryable(RestClientResponseException exception) {
        return exception.getStatusCode().is5xxServerError()
                || exception.getStatusCode().value() == 429;
    }

    private IntegrationException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 400) {
            return IntegrationException.invalid(
                    "JIRA_REQUEST_REJECTED",
                    "Jira rejected the request"
            );
        }
        if (status == 401) {
            return IntegrationException.conflict(
                    "JIRA_ACCESS_REVOKED",
                    "Jira access is invalid or has been revoked"
            );
        }
        if (status == 403) {
            return new IntegrationException(
                    HttpStatus.FORBIDDEN,
                    "JIRA_ACCESS_FORBIDDEN",
                    "Jira denied access to the requested resource"
            );
        }
        if (status == 429) {
            return new IntegrationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "JIRA_RATE_LIMITED",
                    "Jira rate limit was reached"
            );
        }
        if (status == 404) {
            return IntegrationException.conflict(
                    "JIRA_RESOURCE_NOT_FOUND",
                    "The selected Jira resource is no longer accessible"
            );
        }
        return providerUnavailable();
    }

    private IntegrationException providerResponseInvalid() {
        return IntegrationException.unavailable("JIRA_RESPONSE_INVALID");
    }

    private IntegrationException providerUnavailable() {
        return IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE");
    }

    private String requireConfigured(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "JIRA_NOT_CONFIGURED",
                    name + " is not configured"
            );
        }
        return value;
    }

    private static JdkClientHttpRequestFactory requestFactory(
            IntegrationProperties integrationProperties
    ) {
        Duration connect = integrationProperties.httpConnectTimeout() == null
                ? Duration.ofSeconds(3)
                : integrationProperties.httpConnectTimeout();
        Duration read = integrationProperties.httpReadTimeout() == null
                ? Duration.ofSeconds(10)
                : integrationProperties.httpReadTimeout();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return factory;
    }
}
