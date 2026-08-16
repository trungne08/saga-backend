package com.saga.be.service;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.dto.request.CommitReviewStartRequest;
import com.saga.be.dto.response.CommitReviewJobResponses;
import com.saga.be.entity.enums.CommitReviewPriority;
import com.saga.be.exception.IntegrationException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CommitReviewAiClient {

    static final String SERVICE_TOKEN_HEADER = AgentAiClient.SERVICE_TOKEN_HEADER;
    static final String START_PATH = "/internal/backend/v1/commit-reviews";
    static final String STATUS_PATH = "/internal/backend/v1/commit-reviews/{jobId}";
    static final String SCHEMA_PATH = "/internal/backend/v1/commit-reviews/schemas/final-result-v2";
    static final String RUN_BOUNDED_PATH = "/internal/backend/v1/commit-reviews/execution/run-bounded";

    private final AgentAiProperties properties;
    private final RestClient client;

    @Autowired
    public CommitReviewAiClient(AgentAiProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)).build());
    }

    CommitReviewAiClient(AgentAiProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    public boolean isConfigured() {
        String value = properties.baseUrl();
        String token = properties.serviceToken();
        return value != null && !value.isBlank() && token != null && token.length() >= 32;
    }

    public CommitReviewJobResponses.Start start(
            UUID projectId,
            long providerRepositoryId,
            String commitSha,
            CommitReviewPolicyVersion policy
    ) {
        if (projectId == null || policy == null) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_START_INVALID");
        }
        String sha = requireSha(commitSha);
        CommitReviewStartRequest request = new CommitReviewStartRequest(
                projectId,
                providerRepositoryId,
                sha,
                policy.wireValue(),
                policy.requiredPriority().name()
        );
        CommitReviewJobResponses.Start started = post(START_PATH, request, CommitReviewJobResponses.Start.class);
        return requireStart(started, policy);
    }

    public CommitReviewJobResponses.Status status(UUID jobId) {
        if (jobId == null) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_JOB_ID_INVALID");
        }
        CommitReviewJobResponses.Status status = get(
                START_PATH + "/" + jobId,
                CommitReviewJobResponses.Status.class
        );
        return requireStatus(status);
    }

    public Map<?, ?> finalResultSchemaV2() {
        Map<?, ?> schema = get(SCHEMA_PATH, Map.class);
        if (schema == null || schema.isEmpty()) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_SCHEMA_INVALID");
        }
        return Map.copyOf(schema);
    }

    public void runBounded() {
        post(RUN_BOUNDED_PATH, Map.of(), Void.class);
    }

    private CommitReviewJobResponses.Start requireStart(
            CommitReviewJobResponses.Start started,
            CommitReviewPolicyVersion policy
    ) {
        if (started == null || started.jobId() == null) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_START_RESPONSE_INVALID");
        }
        String jobStatus = CommitReviewResultParser.requireJobStatus(started.status());
        CommitReviewPolicyVersion.requireExact(
                started.reviewPolicyVersion() == null ? policy.wireValue() : started.reviewPolicyVersion(),
                started.priority() == null
                        ? policy.requiredPriority()
                        : requirePriority(started.priority())
        );
        if (started.reviewPolicyVersion() != null && !policy.wireValue().equals(started.reviewPolicyVersion())) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_POLICY_MISMATCH");
        }
        if (started.priority() != null && policy.requiredPriority() != requirePriority(started.priority())) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_PRIORITY_MISMATCH");
        }
        return new CommitReviewJobResponses.Start(
                started.jobId(), jobStatus, policy.wireValue(), policy.requiredPriority().name()
        );
    }

    private CommitReviewJobResponses.Status requireStatus(CommitReviewJobResponses.Status status) {
        if (status == null || status.jobId() == null) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_STATUS_RESPONSE_INVALID");
        }
        String jobStatus = CommitReviewResultParser.requireJobStatus(status.status());
        if (status.reviewPolicyVersion() != null) {
            CommitReviewPriority priority = status.priority() == null
                    ? null
                    : requirePriority(status.priority());
            if (priority != null) {
                CommitReviewPolicyVersion.requireExact(status.reviewPolicyVersion(), priority);
            } else {
                CommitReviewPolicyVersion.requireExact(
                        status.reviewPolicyVersion(),
                        CommitReviewPolicyVersion.HISTORICAL_V1.wireValue().equals(status.reviewPolicyVersion())
                                ? CommitReviewPriority.LOW
                                : CommitReviewPriority.HIGH
                );
            }
        }
        if ("COMPLETED".equals(jobStatus)) {
            CommitReviewResultParser.parse(status.finalResult());
        }
        return new CommitReviewJobResponses.Status(
                status.jobId(),
                jobStatus,
                status.reviewPolicyVersion(),
                status.priority(),
                status.safeErrorCode(),
                status.finalResult()
        );
    }

    private CommitReviewPriority requirePriority(String priority) {
        try {
            return CommitReviewPriority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_PRIORITY_UNKNOWN");
        }
    }

    private String requireSha(String commitSha) {
        if (commitSha == null || !commitSha.matches("^[0-9a-fA-F]{40}$")) {
            throw new CommitReviewPolicyVersion.CommitReviewContractRejected("AI_REVIEW_COMMIT_SHA_INVALID");
        }
        return commitSha.toLowerCase(Locale.ROOT);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            RestClient.RequestBodySpec request = client.post().uri(uri(path)).header(
                    SERVICE_TOKEN_HEADER, requireServiceToken()
            );
            if (type == Void.class) {
                request.body(body).retrieve().toBodilessEntity();
                return null;
            }
            T response = request.body(body).retrieve().body(type);
            if (response == null) {
                throw invalidResponse("POST", path);
            }
            return response;
        } catch (CommitReviewPolicyVersion.CommitReviewContractRejected | CommitReviewResultParser.CommitReviewResultRejected exception) {
            throw exception;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw translate("POST", path, exception);
        } catch (RestClientException exception) {
            throw transportUnavailable("POST", path, exception);
        }
    }

    private <T> T get(String path, Class<T> type) {
        try {
            T response = client.get().uri(uri(path))
                    .header(SERVICE_TOKEN_HEADER, requireServiceToken())
                    .retrieve().body(type);
            if (response == null) {
                throw invalidResponse("GET", path);
            }
            return response;
        } catch (CommitReviewPolicyVersion.CommitReviewContractRejected | CommitReviewResultParser.CommitReviewResultRejected exception) {
            throw exception;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw translate("GET", path, exception);
        } catch (RestClientException exception) {
            throw transportUnavailable("GET", path, exception);
        }
    }

    private URI uri(String path) {
        String value = properties.baseUrl();
        if (value == null || value.isBlank()) {
            throw unavailable("AI_AGENT_NOT_CONFIGURED");
        }
        URI base;
        try {
            base = URI.create(value.trim());
        } catch (IllegalArgumentException exception) {
            throw unavailable("AI_AGENT_NOT_CONFIGURED");
        }
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw unavailable("AI_AGENT_NOT_CONFIGURED");
        }
        return URI.create(base.toString().replaceAll("/$", "") + path);
    }

    private String requireServiceToken() {
        String token = properties.serviceToken();
        if (token == null || token.length() < 32) {
            throw unavailable("AI_AGENT_NOT_CONFIGURED");
        }
        return token;
    }

    private IntegrationException translate(
            String operation, String path, RestClientResponseException exception
    ) {
        IntegrationException mapped = mappedResponse(exception);
        AgentAiHttpFailureLogger.logResponse(operation, path, exception, mapped.getCode());
        return mapped;
    }

    private IntegrationException mappedResponse(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new IntegrationException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_AGENT_SERVICE_AUTH_FAILED",
                    "AI Agent service authentication failed",
                    exception
            );
        }
        if (status == 404) {
            return new IntegrationException(
                    HttpStatus.NOT_FOUND, "AI_AGENT_RESOURCE_NOT_FOUND", "AI Agent resource not found",
                    exception
            );
        }
        if (status == 409) {
            return new IntegrationException(
                    HttpStatus.CONFLICT,
                    "AI_AGENT_CONFLICT",
                    "The AI Agent request conflicts with current state",
                    exception
            );
        }
        if (status >= 400 && status < 500) {
            return new IntegrationException(
                    HttpStatus.BAD_REQUEST,
                    "AI_AGENT_REQUEST_INVALID",
                    "The AI Agent request is invalid",
                    exception
            );
        }
        return unavailable("AI_AGENT_UNAVAILABLE", exception);
    }

    private IntegrationException transportUnavailable(
            String operation, String path, RestClientException exception
    ) {
        AgentAiHttpFailureLogger.logTransport(operation, path, exception, "AI_AGENT_UNAVAILABLE");
        return unavailable("AI_AGENT_UNAVAILABLE", exception);
    }

    private IntegrationException invalidResponse(String operation, String path) {
        AgentAiHttpFailureLogger.logInvalidResponse(operation, path, "AI_AGENT_RESPONSE_INVALID");
        return unavailable("AI_AGENT_RESPONSE_INVALID");
    }

    private IntegrationException unavailable(String code) {
        return unavailable(code, null);
    }

    private IntegrationException unavailable(String code, Throwable cause) {
        return new IntegrationException(
                HttpStatus.SERVICE_UNAVAILABLE, code, "The AI Agent service is unavailable", cause
        );
    }

    private static JdkClientHttpRequestFactory requestFactory(AgentAiProperties properties) {
        Duration connect = properties.connectTimeout() == null ? Duration.ofSeconds(3) : properties.connectTimeout();
        Duration read = properties.readTimeout() == null ? Duration.ofSeconds(130) : properties.readTimeout();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connect).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return factory;
    }
}
