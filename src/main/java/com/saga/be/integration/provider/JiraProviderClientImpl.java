package com.saga.be.integration.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class JiraProviderClientImpl implements JiraProviderClient {

    private static final int MAX_GET_ATTEMPTS = 3;

    private final JiraIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public JiraProviderClientImpl(
            JiraIntegrationProperties properties,
            IntegrationProperties integrationProperties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory(integrationProperties))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
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
    public String registerWebhook(
            String accessToken,
            String cloudId,
            String projectKey,
            URI callbackUri
    ) {
        Map<String, Object> webhook = new LinkedHashMap<>();
        webhook.put("jqlFilter", "project = \"" + safeJqlKey(projectKey) + "\"");
        webhook.put("events", List.of(
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
        ));
        JsonNode response = postJson(
                jiraUri(cloudId, "/rest/api/3/webhook"),
                accessToken,
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
            throw IntegrationException.conflict(
                    "JIRA_WEBHOOK_REGISTRATION_FAILED",
                    "Jira rejected the webhook registration"
            );
        }
        return first.path("createdWebhookId").asText();
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
        }
    }

    @Override
    public JiraIssuePage searchIssues(
            String accessToken,
            String cloudId,
            String projectKey,
            LocalDateTime updatedAfter,
            String nextPageToken
    ) {
        StringBuilder jql = new StringBuilder(
                "project = \"" + safeJqlKey(projectKey) + "\""
        );
        if (updatedAfter != null) {
            jql.append(" AND updated >= \"")
                    .append(updatedAfter.toString().replace('T', ' '))
                    .append("\"");
        }
        jql.append(" ORDER BY updated ASC, id ASC");

        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(
                        jiraUri(cloudId, "/rest/api/3/search/jql")
                )
                .queryParam("jql", jql)
                .queryParam("maxResults", 100)
                .queryParam(
                        "fields",
                        "summary,issuetype,status,priority,assignee,reporter,"
                                + "duedate,created,updated,resolutiondate,resolution,"
                                + "customfield_10016,customfield_10020"
                );
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            builder.queryParam("nextPageToken", nextPageToken);
        }

        JsonNode response = get(builder.build().encode().toUri(), accessToken);
        JsonNode issues = response.path("issues");
        if (!issues.isArray()) {
            throw providerResponseInvalid();
        }
        List<JiraIssueSnapshot> snapshots = new ArrayList<>();
        issues.forEach(issue -> snapshots.add(toIssue(issue)));
        String next = text(response, "nextPageToken");
        return new JiraIssuePage(
                snapshots,
                next,
                next == null || next.isBlank()
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
        }
    }

    private JsonNode get(URI uri, String token) {
        for (int attempt = 1; attempt <= MAX_GET_ATTEMPTS; attempt++) {
            try {
                JsonNode response = restClient.get()
                        .uri(uri)
                        .headers(headers -> bearer(headers, token))
                        .retrieve()
                        .body(JsonNode.class);
                if (response == null) {
                    throw providerResponseInvalid();
                }
                return response;
            } catch (RestClientResponseException exception) {
                if (attempt == MAX_GET_ATTEMPTS || !retryable(exception)) {
                    throw translate(exception);
                }
            }
        }
        throw IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE");
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
        }
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

    private JiraIssueSnapshot toIssue(JsonNode issue) {
        JsonNode fields = issue.path("fields");
        JsonNode sprint = first(fields.path("customfield_10020"));
        return new JiraIssueSnapshot(
                requiredText(issue, "id"),
                requiredText(issue, "key"),
                text(fields, "summary"),
                nestedText(fields, "issuetype", "name"),
                nestedText(fields, "status", "name"),
                nestedText(fields, "priority", "name"),
                nullableInt(fields.path("customfield_10016")),
                nestedText(fields, "assignee", "accountId"),
                nestedText(fields, "reporter", "accountId"),
                parseDateOrDateTime(text(fields, "duedate")),
                parseDateOrDateTime(text(fields, "created")),
                parseDateOrDateTime(text(fields, "updated")),
                parseDateOrDateTime(text(fields, "resolutiondate")),
                nestedText(fields, "resolution", "name"),
                sprint == null ? null : text(sprint, "id"),
                sprint == null ? null : text(sprint, "name")
        );
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
        } catch (RuntimeException ignored) {
            return LocalDate.parse(value).atStartOfDay();
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

    private boolean retryable(RestClientResponseException exception) {
        return exception.getStatusCode().is5xxServerError()
                || exception.getStatusCode().value() == 429;
    }

    private IntegrationException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return IntegrationException.conflict(
                    "JIRA_ACCESS_REVOKED",
                    "Jira access is invalid or has been revoked"
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
        return IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE");
    }

    private IntegrationException providerResponseInvalid() {
        return IntegrationException.unavailable("JIRA_RESPONSE_INVALID");
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

    private JdkClientHttpRequestFactory requestFactory(
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
