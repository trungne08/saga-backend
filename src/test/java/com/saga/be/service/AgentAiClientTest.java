package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class AgentAiClientTest {

    @Test
    void messageUsesDedicatedCredentialOpaqueContextAndParsesJobReference() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentAiClient client = new AgentAiClient(properties("b".repeat(40)), builder.build());
        SagaPrincipal actor = actor();
        UUID conversationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        server.expect(once(), requestTo(
                        "https://ai.example/internal/backend/v1/agent/conversations/"
                                + conversationId + "/messages"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AgentAiClient.SERVICE_TOKEN_HEADER, "b".repeat(40)))
                .andExpect(header(AgentAiClient.DELEGATED_CONTEXT_HEADER, "opaque-context"))
                .andExpect(jsonPath("$.ownerId").value("STUDENT:" + actor.localProfileId()))
                .andExpect(jsonPath("$.applicationRole").value("STUDENT"))
                .andExpect(jsonPath("$.content").value("Review this commit"))
                .andExpect(jsonPath("$.currentActor").doesNotExist())
                .andExpect(jsonPath("$.studentCode").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {
                          "conversationId":"%s",
                          "messageId":"%s",
                          "text":"Review queued",
                          "status":"COMPLETED",
                          "citations":[],
                          "pendingAction":null,
                          "generatedArtifact":null,
                          "jobReference":{
                            "jobId":"%s",
                            "jobType":"COMMIT_REVIEW",
                            "status":"PENDING",
                            "currentStep":"COLLECT_CONTEXT",
                            "runNumber":1,
                            "projectId":"%s",
                            "safeErrorCode":null
                          },
                          "suggestedFollowups":[],
                          "provider":"OPENAI",
                          "model":"test-model"
                        }
                        """.formatted(
                                conversationId, UUID.randomUUID(), jobId, UUID.randomUUID()
                        ),
                        MediaType.APPLICATION_JSON
                ));

        AgentApiResponses.Chat result = client.sendMessage(
                actor, conversationId, "opaque-context", "Review this commit", "SE123456"
        );

        assertEquals(jobId, result.jobReference().jobId());
        assertEquals("PENDING", result.jobReference().status());
        server.verify();
    }

    @Test
    void createConversationOmitsCurrentActorAndKeepsCamelCaseContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentAiClient client = new AgentAiClient(properties("b".repeat(40)), builder.build());
        SagaPrincipal actor = actor();
        UUID conversationId = UUID.randomUUID();
        server.expect(once(), requestTo("https://ai.example/internal/backend/v1/agent/conversations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ownerId").value("STUDENT:" + actor.localProfileId()))
                .andExpect(jsonPath("$.applicationRole").value("STUDENT"))
                .andExpect(jsonPath("$.title").value("Private chat"))
                .andExpect(jsonPath("$.currentActor").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {
                          "id":"%s",
                          "title":"Private chat",
                          "applicationRoleSnapshot":"STUDENT",
                          "archived":false,
                          "createdAt":"2026-08-16T00:00:00Z",
                          "updatedAt":"2026-08-16T00:00:00Z",
                          "messages":[]
                        }
                        """.formatted(conversationId),
                        MediaType.APPLICATION_JSON
                ));

        AgentApiResponses.Conversation created = client.createConversation(actor, "Private chat", "SE123456");

        assertEquals(conversationId, created.id());
        server.verify();
    }

    @Test
    void createConversationSendsCourseIdAsResourceScopeNotActorIdentity() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentAiClient client = new AgentAiClient(properties("b".repeat(40)), builder.build());
        SagaPrincipal actor = actor();
        UUID conversationId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        server.expect(once(), requestTo("https://ai.example/internal/backend/v1/agent/conversations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.ownerId").value("STUDENT:" + actor.localProfileId()))
                .andExpect(jsonPath("$.courseId").value(courseId.toString()))
                .andExpect(jsonPath("$.actorId").doesNotExist())
                .andExpect(jsonPath("$.currentActor").doesNotExist())
                .andRespond(withSuccess(
                        """
                        {
                          "id":"%s",
                          "title":"Course chat",
                          "courseId":"%s",
                          "applicationRoleSnapshot":"STUDENT",
                          "archived":false,
                          "createdAt":"2026-08-16T00:00:00Z",
                          "updatedAt":"2026-08-16T00:00:00Z",
                          "messages":[]
                        }
                        """.formatted(conversationId, courseId),
                        MediaType.APPLICATION_JSON
                ));

        AgentApiResponses.Conversation created = client.createConversation(
                actor, "Course chat", "SE123456", courseId
        );

        assertEquals(courseId, created.courseId());
        server.verify();
    }

    @Test
    void missingCredentialFailsClosedBeforeHttp() {
        AgentAiClient client = new AgentAiClient(properties(null), RestClient.create());

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> client.listConversations(actor())
        );

        assertEquals("AI_AGENT_NOT_CONFIGURED", failure.getCode());
    }

    @Test
    void aiAuthenticationFailureMapsWithoutResponseBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AgentAiClient client = new AgentAiClient(properties("b".repeat(40)), builder.build());
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString(
                        "/internal/backend/v1/agent/conversations"
                )))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"secret\":\"must-not-escape\"}"));

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> client.listConversations(actor())
        );

        assertEquals("AI_AGENT_SERVICE_AUTH_FAILED", failure.getCode());
        server.verify();
    }

    private AgentAiProperties properties(String token) {
        return new AgentAiProperties(
                "https://ai.example", token,
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(5)
        );
    }

    private SagaPrincipal actor() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
