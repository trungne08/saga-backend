package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.saga.be.config.AgentAiProperties;
import com.saga.be.dto.response.CommitReviewJobResponses;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CommitReviewAiClientTest {

    @Test
    void startHistoricalIsLowAndLiveIsHigh() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommitReviewAiClient client = new CommitReviewAiClient(properties(), builder.build());
        UUID projectId = UUID.randomUUID();
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        UUID jobId = UUID.randomUUID();
        server.expect(once(), requestTo("https://ai.example/internal/backend/v1/commit-reviews"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(AgentAiClient.SERVICE_TOKEN_HEADER, "b".repeat(40)))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.providerRepositoryId").value(42))
                .andExpect(jsonPath("$.commitSha").value(sha))
                .andExpect(jsonPath("$.reviewPolicyVersion").value("commit-review-historical-v1"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andRespond(withSuccess("""
                        {"jobId":"%s","status":"PENDING","reviewPolicyVersion":"commit-review-historical-v1","priority":"LOW"}
                        """.formatted(jobId), MediaType.APPLICATION_JSON));

        CommitReviewJobResponses.Start started = client.start(
                projectId, 42L, sha, CommitReviewPolicyVersion.HISTORICAL_V1
        );
        assertEquals(jobId, started.jobId());
        assertEquals("LOW", started.priority());
        server.verify();
    }

    @Test
    void startLiveIsHigh() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommitReviewAiClient client = new CommitReviewAiClient(properties(), builder.build());
        UUID projectId = UUID.randomUUID();
        String sha = "abcdef0123456789abcdef0123456789abcdef01";
        server.expect(once(), requestTo("https://ai.example/internal/backend/v1/commit-reviews"))
                .andExpect(jsonPath("$.reviewPolicyVersion").value("commit-review-live-task-aware-v1"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andRespond(withSuccess("""
                        {"jobId":"%s","status":"PENDING","reviewPolicyVersion":"commit-review-live-task-aware-v1","priority":"HIGH"}
                        """.formatted(UUID.randomUUID()), MediaType.APPLICATION_JSON));

        assertEquals("HIGH", client.start(
                projectId, 7L, sha, CommitReviewPolicyVersion.LIVE_TASK_AWARE_V1
        ).priority());
        server.verify();
    }

    @Test
    void statusParsesCompletedV2AndRejectsUnknownEnum() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CommitReviewAiClient client = new CommitReviewAiClient(properties(), builder.build());
        UUID jobId = UUID.randomUUID();
        server.expect(once(), requestTo("https://ai.example/internal/backend/v1/commit-reviews/" + jobId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "jobId":"%s",
                          "status":"COMPLETED",
                          "reviewPolicyVersion":"commit-review-live-task-aware-v1",
                          "priority":"HIGH",
                          "finalResult":{
                            "kind":"LIVE_TASK_LINKED",
                            "messageQuality":"GOOD",
                            "confidence":"HIGH",
                            "traceability":"EXPLICIT_LINKS_PRESENT",
                            "verdictStatus":"NEEDS_CHANGES",
                            "findings":[],
                            "evidenceRefs":[]
                          }
                        }
                        """.formatted(jobId), MediaType.APPLICATION_JSON));

        CommitReviewJobResponses.Status completed = client.status(jobId);
        assertEquals("COMPLETED", completed.status());
        assertEquals("NEEDS_CHANGES", completed.finalResult().get("verdictStatus"));
        server.verify();

        RestClient.Builder unknownBuilder = RestClient.builder();
        MockRestServiceServer unknownServer = MockRestServiceServer.bindTo(unknownBuilder).build();
        CommitReviewAiClient unknownClient = new CommitReviewAiClient(properties(), unknownBuilder.build());
        UUID unknownJob = UUID.randomUUID();
        unknownServer.expect(once(), requestTo("https://ai.example/internal/backend/v1/commit-reviews/" + unknownJob))
                .andRespond(withSuccess("""
                        {
                          "jobId":"%s",
                          "status":"COMPLETED",
                          "finalResult":{
                            "kind":"BRAND_NEW",
                            "messageQuality":"GOOD",
                            "confidence":"HIGH",
                            "traceability":"NOT_PROVEN"
                          }
                        }
                        """.formatted(unknownJob), MediaType.APPLICATION_JSON));
        assertThrows(
                CommitReviewResultParser.CommitReviewResultRejected.class,
                () -> unknownClient.status(unknownJob)
        );
    }

    @Test
    void failedAndCancelledAreNotNeedsChanges() {
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning(
                "FAILED",
                CommitReviewResultParser.parse(java.util.Map.of(
                        "kind", "LIVE_TASK_LINKED",
                        "messageQuality", "GOOD",
                        "confidence", "HIGH",
                        "traceability", "EXPLICIT_LINKS_PRESENT",
                        "verdictStatus", "NEEDS_CHANGES"
                ))
        ));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning(
                "CANCELLED",
                CommitReviewResultParser.parse(java.util.Map.of(
                        "kind", "LIVE_TASK_LINKED",
                        "messageQuality", "GOOD",
                        "confidence", "HIGH",
                        "traceability", "EXPLICIT_LINKS_PRESENT",
                        "verdictStatus", "NEEDS_CHANGES"
                ))
        ));
        assertThrows(
                CommitReviewPolicyVersion.CommitReviewContractRejected.class,
                () -> CommitReviewPolicyVersion.requireExact("commit-review-made-up-v9", com.saga.be.entity.enums.CommitReviewPriority.LOW)
        );
        assertThrows(
                CommitReviewPolicyVersion.CommitReviewContractRejected.class,
                () -> CommitReviewPolicyVersion.requireExact(
                        "commit-review-historical-v1",
                        com.saga.be.entity.enums.CommitReviewPriority.HIGH
                )
        );
    }

    private AgentAiProperties properties() {
        return new AgentAiProperties(
                "https://ai.example", "b".repeat(40),
                Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofMinutes(5)
        );
    }
}
