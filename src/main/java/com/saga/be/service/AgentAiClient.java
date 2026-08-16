package com.saga.be.service;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.SagaPrincipal;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AgentAiClient {

    public static final String SERVICE_TOKEN_HEADER = "X-SAGA-Backend-Service-Token";
    public static final String DELEGATED_CONTEXT_HEADER = "X-SAGA-Agent-Context";

    private final AgentAiProperties properties;
    private final RestClient client;

    @Autowired
    public AgentAiClient(AgentAiProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)).build());
    }

    AgentAiClient(AgentAiProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    public AgentApiResponses.Conversation createConversation(
            SagaPrincipal actor, String title, String studentCode
    ) {
        return createConversation(actor, title, studentCode, null);
    }

    public AgentApiResponses.Conversation createConversation(
            SagaPrincipal actor, String title, String studentCode, UUID courseId
    ) {
        return post(
                "/internal/backend/v1/agent/conversations",
                conversationBody(actor, title == null ? "" : title, null, courseId),
                null,
                AgentApiResponses.Conversation.class
        );
    }

    public AgentApiResponses.ConversationList listConversations(SagaPrincipal actor) {
        return get(
                "/internal/backend/v1/agent/conversations",
                Map.of("ownerId", ownerId(actor)),
                AgentApiResponses.ConversationList.class
        );
    }

    public AgentApiResponses.Conversation conversation(SagaPrincipal actor, UUID conversationId) {
        return get(
                "/internal/backend/v1/agent/conversations/" + conversationId,
                Map.of("ownerId", ownerId(actor)),
                AgentApiResponses.Conversation.class
        );
    }

    public AgentApiResponses.Chat sendMessage(
            SagaPrincipal actor,
            UUID conversationId,
            String delegatedContext,
            String content,
            String studentCode
    ) {
        return sendMessage(actor, conversationId, delegatedContext, content, studentCode, null);
    }

    public AgentApiResponses.Chat sendMessage(
            SagaPrincipal actor,
            UUID conversationId,
            String delegatedContext,
            String content,
            String studentCode,
            UUID courseId
    ) {
        return post(
                "/internal/backend/v1/agent/conversations/" + conversationId + "/messages",
                conversationBody(actor, null, content, courseId),
                delegatedContext,
                AgentApiResponses.Chat.class
        );
    }

    public AgentApiResponses.PendingAction inspectAction(SagaPrincipal actor, UUID actionId) {
        return get(
                "/internal/backend/v1/agent/pending-actions/" + actionId,
                Map.of("ownerId", ownerId(actor)),
                AgentApiResponses.PendingAction.class
        );
    }

    public AgentApiResponses.PendingAction claimAction(SagaPrincipal actor, UUID actionId) {
        return post(
                "/internal/backend/v1/agent/pending-actions/" + actionId + "/claim",
                Map.of("ownerId", ownerId(actor)), null,
                AgentApiResponses.PendingAction.class
        );
    }

    public AgentApiResponses.PendingAction rejectAction(SagaPrincipal actor, UUID actionId) {
        return post(
                "/internal/backend/v1/agent/pending-actions/" + actionId + "/reject",
                Map.of("ownerId", ownerId(actor)), null,
                AgentApiResponses.PendingAction.class
        );
    }

    public AgentApiResponses.PendingAction finalizeAction(
            SagaPrincipal actor, UUID actionId, boolean succeeded, String safeErrorCode
    ) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ownerId", ownerId(actor));
        body.put("succeeded", succeeded);
        if (safeErrorCode != null) {
            body.put("safeErrorCode", safeErrorCode);
        }
        return post(
                "/internal/backend/v1/agent/pending-actions/" + actionId + "/finalize",
                body, null, AgentApiResponses.PendingAction.class
        );
    }

    public AgentApiResponses.GeneratedArtifact artifact(SagaPrincipal actor, UUID artifactId) {
        return get(
                "/internal/backend/v1/agent/artifacts/" + artifactId,
                Map.of("ownerId", ownerId(actor)),
                AgentApiResponses.GeneratedArtifact.class
        );
    }

    public byte[] artifactContent(SagaPrincipal actor, UUID artifactId) {
        return get(
                "/internal/backend/v1/agent/artifacts/" + artifactId + "/content",
                Map.of("ownerId", ownerId(actor)),
                byte[].class
        );
    }

    public String ownerId(SagaPrincipal actor) {
        if (actor == null || actor.localProfileId() == null || actor.applicationRole() == null) {
            throw IntegrationException.forbidden("An authenticated local profile is required");
        }
        return actor.applicationRole().name() + ":" + actor.localProfileId();
    }

    private Map<String, Object> conversationBody(
            SagaPrincipal actor, String title, String content, UUID courseId
    ) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("ownerId", ownerId(actor));
        body.put("applicationRole", actor.applicationRole().name());
        if (title != null) {
            body.put("title", title);
        }
        if (content != null) {
            body.put("content", content);
        }
        if (courseId != null) {
            body.put("courseId", courseId.toString());
        }
        return body;
    }

    private <T> T post(String path, Object body, String delegatedContext, Class<T> type) {
        try {
            RestClient.RequestBodySpec request = client.post().uri(uri(path)).header(
                    SERVICE_TOKEN_HEADER, requireServiceToken()
            );
            if (delegatedContext != null) {
                request.header(DELEGATED_CONTEXT_HEADER, delegatedContext);
            }
            T response = request.body(body).retrieve().body(type);
            if (response == null) {
                throw invalidResponse("POST", path);
            }
            return response;
        } catch (IntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw translate("POST", path, exception);
        } catch (RestClientException exception) {
            throw transportUnavailable("POST", path, exception);
        }
    }

    private <T> T get(String path, Map<String, String> query, Class<T> type) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri(path));
            query.forEach(builder::queryParam);
            T response = client.get().uri(builder.build(true).toUri())
                    .header(SERVICE_TOKEN_HEADER, requireServiceToken())
                    .retrieve().body(type);
            if (response == null) {
                throw invalidResponse("GET", path);
            }
            return response;
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
