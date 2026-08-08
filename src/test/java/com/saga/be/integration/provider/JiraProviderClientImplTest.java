package com.saga.be.integration.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.entity.value.TaskComponentSnapshot;
import com.saga.be.exception.IntegrationException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class JiraProviderClientImplTest {

    private static final String BASE = "https://api.atlassian.test";
    private static final String CLOUD_ID = "cloud-123";
    private static final String WEBHOOK_URL = BASE
            + "/ex/jira/" + CLOUD_ID + "/rest/api/3/webhook";
    private static final String FIRST_WEBHOOK_PAGE_URL = WEBHOOK_URL
            + "?startAt=0&maxResults=100";
    private static final String SPRINT_URL = BASE + "/ex/jira/" + CLOUD_ID
            + "/rest/agile/1.0/sprint/42";
    private static final String AGILE_BOARD_URL = BASE + "/ex/jira/" + CLOUD_ID
            + "/rest/agile/1.0/board";
    private static final String BOARD_FEATURES_URL = AGILE_BOARD_URL + "/35/features";
    private static final String BOARD_SPRINTS_URL = AGILE_BOARD_URL + "/35/sprint";
    private static final String PROJECT_FEATURES_URL = BASE + "/ex/jira/" + CLOUD_ID
            + "/rest/api/3/project/10034/features";
    private static final String PROJECT_URL = BASE + "/ex/jira/" + CLOUD_ID
            + "/rest/api/3/project/search?startAt=0&maxResults=50&orderBy=key";
    private static final URI CALLBACK = URI.create(
            "https://callback.test/api/webhooks/jira?token=CALLBACK_SECRET"
    );
    private static final List<String> EVENTS = List.of(
            "jira:issue_created", "jira:issue_updated", "jira:issue_deleted",
            "comment_created", "comment_updated", "comment_deleted",
            "sprint_created", "sprint_updated", "sprint_deleted",
            "sprint_started", "sprint_closed"
    );

    @Test
    void parsesCreatedWebhookIdFromSuccessfulRegistration() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"webhookRegistrationResult\":[{\"createdWebhookId\":123}]}"));

        assertEquals("123", fixture.client.registerWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK
        ));
        fixture.server.verify();
    }

    @Test
    void redactsProviderErrorsContainingCallbackSecrets() {
        Fixture fixture = fixture();
        Logger logger = (Logger) LoggerFactory.getLogger(
                JiraProviderClientImpl.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"webhookRegistrationResult\":[{\"errors\":[\"callback https://callback.test/api/webhooks/jira?token=PROVIDER_CALLBACK_SECRET is already registered\"]}]}"));

        try {
            IntegrationException error = assertThrows(
                    IntegrationException.class,
                    () -> fixture.client.registerWebhook(
                            "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK
                    )
            );
            assertEquals("JIRA_WEBHOOK_REGISTRATION_REJECTED", error.getCode());
            assertThat(error.getMessage()).doesNotContain("PROVIDER_CALLBACK_SECRET");
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);
            assertThat(logged)
                    .doesNotContain("ACCESS_TOKEN_SECRET")
                    .doesNotContain("CALLBACK_SECRET")
                    .doesNotContain("PROVIDER_CALLBACK_SECRET")
                    .contains("errorCategory=REJECTED")
                    .contains("errorCount=1");
        } finally {
            logger.detachAppender(appender);
        }
        fixture.server.verify();
    }

    @Test
    void replacesMatchingRetainedWebhookWithFreshCallback() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse("12", CALLBACK.toString())));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"createdWebhookId\":13}]}"));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, "12"
        );

        assertEquals("13", result.webhookId());
        assertThat(result.created()).isTrue();
        fixture.server.verify();
    }

    @Test
    void maintenanceRefreshDoesNotRotateMatchingWebhook() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse("14", CALLBACK.toString())));

        JiraWebhookRegistration result = fixture.client.refreshWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, "14"
        );

        assertEquals("14", result.webhookId());
        assertThat(result.created()).isFalse();
        fixture.server.verify();
    }

    @Test
    void doesNotDeleteRetainedWebhookWhenCallbackEndpointIsUnrelated() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse(
                        "9",
                        "https://example.com/api/webhooks/jira?token=old"
                )));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"createdWebhookId\":10}]}"));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, "9"
        );

        assertEquals("10", result.webhookId());
        assertThat(result.created()).isTrue();
        fixture.server.verify();
    }

    @Test
    void mapsProvider400And403ToActionableCategories() {
        Fixture badRequest = fixture();
        badRequest.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessages\":[\"bad JQL\"]}"));
        assertEquals("JIRA_WEBHOOK_REGISTRATION_INVALID", assertThrows(
                IntegrationException.class,
                () -> badRequest.client.listWebhooks("ACCESS_TOKEN_SECRET", CLOUD_ID)
        ).getCode());

        Fixture forbidden = fixture();
        forbidden.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessages\":[\"scope missing\"]}"));
        assertEquals("JIRA_WEBHOOK_PERMISSION_DENIED", assertThrows(
                IntegrationException.class,
                () -> forbidden.client.listWebhooks("ACCESS_TOKEN_SECRET", CLOUD_ID)
        ).getCode());
        badRequest.server.verify();
        forbidden.server.verify();
    }

    @Test
    void detectsTheDynamicWebhookLimitFromSuccessfulHttpResponse() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"errors\":[\"Maximum of 5 webhooks reached\"]}]}"));

        assertEquals("JIRA_WEBHOOK_LIMIT_REACHED", assertThrows(
                IntegrationException.class,
                () -> fixture.client.registerWebhook(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK
                )
        ).getCode());
        fixture.server.verify();
    }

    @Test
    void parsesAnEmptyOfficialPageAsNoWebhooks() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(true, 0, 100, 0, "")));

        assertThat(fixture.client.listWebhooks("ACCESS_TOKEN_SECRET", CLOUD_ID))
                .isEmpty();
        fixture.server.verify();
    }

    @Test
    void parsesFlatValuesIncludingOffsetExpirationAndUnknownFields() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(
                        true,
                        0,
                        100,
                        1,
                        webhookValue(
                                "10000",
                                CALLBACK.toString(),
                                "\"expirationDate\":\"2019-06-01T12:42:30.000+0000\","
                                        + "\"futureProviderField\":true"
                        )
                )));

        JiraWebhook webhook = fixture.client.listWebhooks(
                "ACCESS_TOKEN_SECRET", CLOUD_ID
        ).get(0);

        assertEquals("10000", webhook.id());
        assertEquals("2019-06-01T12:42:30.000+0000", webhook.expirationDate());
        assertEquals(EVENTS, webhook.events());
        fixture.server.verify();
    }

    @Test
    void parsesMultipleWebhooksAndFollowsPageBeanPagination() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(
                        false,
                        0,
                        1,
                        2,
                        webhookValue("1", CALLBACK.toString(), "")
                )));
        fixture.server.expect(requestTo(
                        WEBHOOK_URL + "?startAt=1&maxResults=100"
                ))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(
                        true,
                        1,
                        1,
                        2,
                        webhookValue(
                                "2",
                                "https://callback.test/api/webhooks/jira?token=other",
                                ""
                        )
                )));

        assertThat(fixture.client.listWebhooks("ACCESS_TOKEN_SECRET", CLOUD_ID))
                .extracting(JiraWebhook::id)
                .containsExactly("1", "2");
        fixture.server.verify();
    }

    @Test
    void parsesEnhancedSearchLastPageWithoutNextTokenAndPreservesOffsetWallTime() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
            assertEquals(
                    "/ex/jira/" + CLOUD_ID + "/rest/api/3/search/jql",
                    request.getURI().getPath()
            );
            String query = URLDecoder.decode(
                    request.getURI().getRawQuery(), StandardCharsets.UTF_8
            );
            assertThat(query)
                    .contains("project = SDP")
                    .contains("updated >= \"2026-07-31 00:25\"")
                    .contains("updated < \"2026-07-31 00:32\"")
                    .contains("labels")
                    .contains("components")
                    .contains("description")
                    .doesNotContain(":57")
                    .doesNotContain(":02")
                    .doesNotContainPattern(
                            "updated [<>]=? \\\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"
                    )
                    .doesNotContain("nextPageToken=");
        }).andExpect(method(HttpMethod.GET)).andRespond(json("""
                {"isLast":true,"issues":[{
                  "id":"10452","key":"SDP-1",
                  "fields":{"summary":"SAGA WEBHOOK TEST 01 - UPDATED",
                    "status":{"name":"In Progress"},
                    "updated":"2026-07-31T00:30:57.360+0700"}
                }]}
                """));

        JiraIssuePage page = fixture.client.searchIssues(
                "ACCESS_TOKEN_SECRET",
                CLOUD_ID,
                "SDP",
                Instant.parse("2026-07-31T00:25:57Z"),
                Instant.parse("2026-07-31T00:31:02Z"),
                null
        );

        assertThat(page.issues()).hasSize(1);
        assertEquals("SDP-1", page.issues().get(0).key());
        assertEquals(
                LocalDateTime.parse("2026-07-31T00:30:57.360"),
                page.issues().get(0).updatedAt()
        );
        assertEquals(
                Instant.parse("2026-07-30T17:30:57.360Z"),
                page.issues().get(0).updatedAtUtc()
        );
        assertThat(page.last()).isTrue();
        assertThat(page.nextPageToken()).isNull();
        fixture.server.verify();
    }

    @Test
    void parsesMissingNullAndEmptyLabelsAsEmptyImmutableLists() {
        assertThat(searchFirstIssue(null).labels()).isEmpty();
        assertThat(searchFirstIssue("null").labels()).isEmpty();
        assertThat(searchFirstIssue("[]").labels()).isEmpty();
    }

    @Test
    void parsesOneAndMultipleLabelsWithoutChangingTheirValues() {
        assertThat(searchFirstIssue("[\"Backend\"]").labels())
                .containsExactly("Backend");
        JiraIssueSnapshot multiple = searchFirstIssue("[\"UI Ready\",\"P1\",\"MiXeD\"]");
        assertThat(multiple.labels())
                .containsExactly("UI Ready", "P1", "MiXeD");
        assertThrows(UnsupportedOperationException.class,
                () -> multiple.labels().add("must-not-mutate"));
    }

    @Test
    void rejectsNonStringJiraLabelValues() {
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> searchFirstIssue("[{\"name\":\"not-a-string\"}]")
        );

        assertEquals("JIRA_RESPONSE_INVALID", exception.getCode());
    }

    @Test
    void parsesMissingNullAndEmptyComponentsAsEmptyImmutableLists() {
        assertThat(searchFirstIssueWithRaw(null, null, null).components()).isEmpty();
        assertThat(searchFirstIssueWithRaw(null, "null", null).components()).isEmpty();
        assertThat(searchFirstIssueWithRaw(null, "[]", null).components()).isEmpty();
    }

    @Test
    void parsesAndPreservesComponentIdAndNameSnapshots() {
        JiraIssueSnapshot issue = searchFirstIssueWithRaw(
                null,
                "[{\"id\":\"10\",\"name\":\"Backend\"},"
                        + "{\"id\":\"20\",\"name\":\"UI\"}]",
                null
        );

        assertThat(issue.components()).containsExactly(
                new TaskComponentSnapshot("10", "Backend"),
                new TaskComponentSnapshot("20", "UI")
        );
        assertThrows(UnsupportedOperationException.class,
                () -> issue.components().add(new TaskComponentSnapshot("30", "Other")));
    }

    @Test
    void rejectsInvalidComponentAndDescriptionShapes() {
        assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                IntegrationException.class,
                () -> searchFirstIssueWithRaw(null, "[\"not-an-object\"]", null)
        ).getCode());
        assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                IntegrationException.class,
                () -> searchFirstIssueWithRaw(null, null, "{\"content\":\"not-an-array\"}")
        ).getCode());
    }

    @Test
    void convertsPlainTextAndAtlassianDocumentFormatDescriptionToSafeText() {
        assertEquals("Plain description", searchFirstIssueWithRaw(
                null,
                null,
                "\"Plain description\""
        ).description());
        assertEquals("Heading body", searchFirstIssueWithRaw(
                null,
                null,
                "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\","
                        + "\"content\":[{\"type\":\"text\",\"text\":\"Heading \"},"
                        + "{\"type\":\"text\",\"text\":\"body\"}]}]}"
        ).description());
    }

    @Test
    void discoversAllAgileBoardsAcrossPagesUsingTheCanonicalProjectId() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
            assertEquals("/ex/jira/" + CLOUD_ID + "/rest/agile/1.0/board", request.getURI().getPath());
            String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query).contains("projectKeyOrId=10034").contains("startAt=0").contains("maxResults=50");
        }).andExpect(method(HttpMethod.GET)).andRespond(json(
                page(false, 0, 50, 2, "{\"id\":12,\"name\":\"Scrum\",\"type\":\"scrum\"}")
        ));
        fixture.server.expect(request -> {
            String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
            assertThat(query).contains("projectKeyOrId=10034").contains("startAt=1");
        }).andExpect(method(HttpMethod.GET)).andRespond(json(
                page(true, 1, 50, 2, "{\"id\":13,\"name\":\"Kanban\",\"type\":\"kanban\"}")
        ));

        assertThat(fixture.client.discoverAgileBoards("ACCESS_TOKEN_SECRET", CLOUD_ID, "10034"))
                .containsExactly(
                        new JiraAgileBoardInfo("12", "Scrum", "scrum"),
                        new JiraAgileBoardInfo("13", "Kanban", "kanban")
                );
        fixture.server.verify();
    }

    @Test
    void doesNotDeleteWebhookForAnotherJiraProject() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse("68", CALLBACK.toString())
                        .replace("project = SDP", "project = OTHER")));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"createdWebhookId\":69}]}"));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, null
        );

        assertEquals("69", result.webhookId());
        fixture.server.verify();
    }

    @Test
    void failsClosedWhenMoreThanOneSafeWebhookCandidateExists() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(
                        true,
                        0,
                        100,
                        2,
                        webhookValue("70", CALLBACK.toString(), "") + ","
                                + webhookValue(
                                        "71",
                                        "https://callback.test/api/webhooks/jira?token=OLD_SECRET",
                                        ""
                                )
                )));

        IntegrationException error = assertThrows(IntegrationException.class,
                () -> fixture.client.ensureWebhook(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, null
                ));

        assertEquals("JIRA_WEBHOOK_ORPHAN_AMBIGUOUS", error.getCode());
        fixture.server.verify();
    }

    @Test
    void doesNotRegisterWhenSafeWebhookDeletionFails() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse("72", CALLBACK.toString())));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        IntegrationException error = assertThrows(IntegrationException.class,
                () -> fixture.client.ensureWebhook(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, null
                ));

        assertEquals("JIRA_PROVIDER_UNAVAILABLE", error.getCode());
        fixture.server.verify();
    }

    @Test
    void replacesExactlyOneSafeOrphanWebhookWhenNoLocalWebhookIdExists() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse(
                        "44",
                        "https://callback.test/api/webhooks/jira?token=ORPHAN_SECRET"
                )));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"createdWebhookId\":45}]}"));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, null
        );

        assertEquals("45", result.webhookId());
        assertThat(result.created()).isTrue();
        fixture.server.verify();
    }

    @Test
    void doesNotDeleteAnUnrelatedWebhookDuringOrphanRecovery() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(page(
                        true,
                        0,
                        100,
                        1,
                        webhookValue(
                                "66",
                                "https://other.example/api/webhooks/jira?token=UNRELATED_SECRET",
                                ""
                        )
                )));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"webhookRegistrationResult\":[{\"createdWebhookId\":67}]}"));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, null
        );

        assertEquals("67", result.webhookId());
        fixture.server.verify();
    }

    @Test
    void parsesTheOfficialBoardFeaturesShapeAndIgnoresDocumentedExtraFields() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(BOARD_FEATURES_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("""
                        {"features":[{
                          "boardFeature":"SIMPLE_ROADMAP",
                          "featureId":"feature-35",
                          "featureType":"BASIC",
                          "state":"ENABLED",
                          "boardId":35,
                          "localisedName":"Sprints",
                          "localisedDescription":"Not retained",
                          "unknownFutureField":true
                        }]}
                        """));

        assertThat(fixture.client.getBoardFeatures("ACCESS_TOKEN_SECRET", CLOUD_ID, "35"))
                .containsExactly(new JiraBoardFeature(
                        "SIMPLE_ROADMAP", "feature-35", "ENABLED", "35"
                ));
        fixture.server.verify();
    }

    @Test
    void acceptsMissingOptionalBoardFeatureFieldsWithoutCallingTheResponseMalformed() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(BOARD_FEATURES_URL))
                .andRespond(json("{" + "\"features\":[{}]}"));

        assertThat(fixture.client.getBoardFeatures("ACCESS_TOKEN_SECRET", CLOUD_ID, "35"))
                .containsExactly(new JiraBoardFeature(null, null, null, null));
        fixture.server.verify();
    }

    @Test
    void acceptsAnEmptyBoardSprintPageAsSprintEndpointSupport() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
            assertEquals("/ex/jira/" + CLOUD_ID + "/rest/agile/1.0/board/35/sprint",
                    request.getURI().getPath());
            assertEquals("maxResults=1", request.getURI().getRawQuery());
        }).andExpect(method(HttpMethod.GET)).andRespond(json("{\"values\":[]}"));

        assertThat(fixture.client.supportsBoardSprintEndpoint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "35"
        )).isTrue();
        fixture.server.verify();
    }

    @Test
    void acceptsANonEmptyBoardSprintPageWithoutRetainingSprintValues() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(BOARD_SPRINTS_URL + "?maxResults=1"))
                .andRespond(json("{\"values\":[{\"id\":42,\"name\":\"Sprint private\"}]}"));

        assertThat(fixture.client.supportsBoardSprintEndpoint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "35"
        )).isTrue();
        fixture.server.verify();
    }

    @Test
    void rejectsMalformedBoardSprintPageWithoutLoggingProviderBody() {
        Fixture fixture = fixture();
        Logger logger = (Logger) LoggerFactory.getLogger(JiraProviderClientImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        fixture.server.expect(requestTo(BOARD_SPRINTS_URL + "?maxResults=1"))
                .andRespond(json("{\"unexpected\":\"PROVIDER_SECRET\"}"));
        try {
            assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                    IntegrationException.class,
                    () -> fixture.client.supportsBoardSprintEndpoint(
                            "ACCESS_TOKEN_SECRET", CLOUD_ID, "35"
                    )
            ).getCode());
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(value -> value.startsWith("Jira sprint capability probe response invalid:"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged)
                    .contains("providerOperation=supportsBoardSprintEndpoint")
                    .contains("endpointPathTemplate=/rest/agile/1.0/board/{boardId}/sprint")
                    .contains("responseShapeCategory=VALUES_MISSING")
                    .doesNotContain("PROVIDER_SECRET")
                    .doesNotContain("ACCESS_TOKEN_SECRET");
        } finally {
            logger.detachAppender(appender);
        }
        fixture.server.verify();
    }

    @Test
    void parsesProjectFeatureIdentifiersAndStatesWithoutLocalizedValues() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(PROJECT_FEATURES_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("""
                        {"features":[{
                          "feature":"jsw.team.example",
                          "projectId":10034,
                          "state":"ENABLED",
                          "localisedName":"Ignored",
                          "localisedDescription":"Ignored"
                        }]}
                        """));

        assertThat(fixture.client.getProjectFeatures("ACCESS_TOKEN_SECRET", CLOUD_ID, "10034"))
                .containsExactly(new JiraProjectFeature("jsw.team.example", "ENABLED"));
        fixture.server.verify();
    }

    @Test
    void logsSafeShapeDiagnosticsWhenBoardFeaturesRootIsMalformed() {
        Fixture fixture = fixture();
        Logger logger = (Logger) LoggerFactory.getLogger(JiraProviderClientImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        fixture.server.expect(requestTo(BOARD_FEATURES_URL))
                .andRespond(json("{\"unexpected\":\"PROVIDER_SECRET\"}"));
        try {
            assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                    IntegrationException.class,
                    () -> fixture.client.getBoardFeatures("ACCESS_TOKEN_SECRET", CLOUD_ID, "35")
            ).getCode());
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(value -> value.startsWith("Jira feature response invalid:"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged)
                    .contains("providerOperation=getBoardFeatures")
                    .contains("endpointPathTemplate=/rest/agile/1.0/board/{boardId}/features")
                    .contains("responseShapeCategory=FEATURES_MISSING")
                    .contains("missingRequiredFields=features")
                    .contains("exceptionClass=IntegrationException")
                    .doesNotContain("PROVIDER_SECRET")
                    .doesNotContain("ACCESS_TOKEN_SECRET");
        } finally {
            logger.detachAppender(appender);
        }
        fixture.server.verify();
    }

    @Test
    void uses3loGatewayForProjectAndBoardDiscoveryAndParsesResourceScopes() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(BASE + "/oauth/token/accessible-resources"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("""
                        [{"id":"cloud-123","name":"SAGA","url":"https://site.example",
                          "scopes":["read:jira-work","read:board-scope:jira-software"]}]
                        """));
        fixture.server.expect(requestTo(PROJECT_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("{\"isLast\":true,\"values\":[]}"));
        fixture.server.expect(request -> assertThat(request.getURI().toString())
                        .startsWith(AGILE_BOARD_URL + "?projectKeyOrId=10034"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("{\"isLast\":true,\"values\":[]}"));

        assertThat(fixture.client.accessibleResources("ACCESS_TOKEN_SECRET"))
                .singleElement()
                .satisfies(resource -> assertThat(resource.scopes())
                        .containsExactlyInAnyOrder(
                                "read:jira-work", "read:board-scope:jira-software"
                        ));
        fixture.client.projects("ACCESS_TOKEN_SECRET", CLOUD_ID);
        fixture.client.discoverAgileBoards("ACCESS_TOKEN_SECRET", CLOUD_ID, "10034");
        fixture.server.verify();
    }

    @Test
    void normalizesCanonicalSprintOffsetsToUtcAndKeepsNull() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json("""
                        {
                          "id":42,
                          "name":"Sprint 42",
                          "state":"active",
                          "startDate":"2026-08-06T09:15:30.123+07:00",
                          "endDate":"2026-08-16T02:15:30.123Z",
                          "completeDate":"2026-08-06T02:15:30.123Z",
                          "originBoardId":99
                        }
                        """));

        JiraSprintSnapshot sprint = fixture.client.getSprint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "42"
        );

        assertEquals(
                LocalDateTime.parse("2026-08-06T02:15:30.123"),
                sprint.startDate()
        );
        assertEquals(
                LocalDateTime.parse("2026-08-16T02:15:30.123"),
                sprint.endDate()
        );
        assertEquals(sprint.startDate(), sprint.completeDate());
        fixture.server.verify();
    }

    @Test
    void startsSprintThroughTheAgileSprintPartialUpdateEndpoint() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::assertJsonBearerRequest)
                .andExpect(content().json("{\"state\":\"active\"}"))
                .andRespond(json("{\"id\":\"42\",\"name\":\"Sprint 42\",\"state\":\"active\"}"));

        JiraSprintSnapshot sprint = fixture.client.updateSprint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "42", java.util.Map.of("state", "active")
        );

        assertEquals("active", sprint.state());
        fixture.server.verify();
    }

    @Test
    void closesSprintThroughTheAgileSprintPartialUpdateEndpoint() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::assertJsonBearerRequest)
                .andExpect(content().json("{\"state\":\"closed\"}"))
                .andRespond(json("{\"id\":\"42\",\"name\":\"Sprint 42\",\"state\":\"closed\"}"));

        JiraSprintSnapshot sprint = fixture.client.updateSprint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "42", java.util.Map.of("state", "closed")
        );

        assertEquals("closed", sprint.state());
        fixture.server.verify();
    }

    @Test
    void partiallyUpdatesSprintThroughPostWithoutReplacingOmittedFields() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(this::assertJsonBearerRequest)
                .andExpect(content().json("{\"name\":\"Renamed\"}"))
                .andRespond(json("{\"id\":\"42\",\"name\":\"Renamed\",\"state\":\"future\"}"));

        JiraSprintSnapshot sprint = fixture.client.updateSprint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "42", java.util.Map.of("name", "Renamed")
        );

        assertEquals("Renamed", sprint.name());
        fixture.server.verify();
    }

    @Test
    void acceptsJiraCompactOffsetForCanonicalSprintDates() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andRespond(json("""
                        {
                          "id":"42",
                          "name":"Sprint 42",
                          "startDate":"2026-08-06T09:15:30.123+0700"
                        }
                        """));

        JiraSprintSnapshot sprint = fixture.client.getSprint(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "42"
        );

        assertEquals(
                LocalDateTime.parse("2026-08-06T02:15:30.123"),
                sprint.startDate()
        );
        assertThat(sprint.endDate()).isNull();
        assertThat(sprint.completeDate()).isNull();
        fixture.server.verify();
    }

    @Test
    void rejectsMalformedCanonicalSprintDatesWithSanitizedCategory() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(SPRINT_URL))
                .andRespond(json("""
                        {"id":"42","name":"Sprint 42","endDate":"not-a-date"}
                        """));

        IntegrationException error = assertThrows(
                IntegrationException.class,
                () -> fixture.client.getSprint(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "42"
                )
        );

        assertEquals("JIRA_RESPONSE_INVALID", error.getCode());
        assertThat(error.getMessage()).doesNotContain("not-a-date");
        fixture.server.verify();

        Fixture malformedStart = fixture();
        malformedStart.server.expect(requestTo(SPRINT_URL))
                .andRespond(json("""
                        {"id":"42","name":"Sprint 42","startDate":"not-a-date"}
                        """));
        IntegrationException startError = assertThrows(
                IntegrationException.class,
                () -> malformedStart.client.getSprint(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "42"
                )
        );
        assertEquals("JIRA_RESPONSE_INVALID", startError.getCode());
        malformedStart.server.verify();
    }

    @Test
    void discoversSprintFieldBySchemaWithoutHardcodedFieldId() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(
                        BASE + "/ex/jira/" + CLOUD_ID + "/rest/api/3/field"
                ))
                .andRespond(json("""
                        [
                          {"id":"customfield_10016","name":"Story point estimate",
                           "schema":{"custom":"com.atlassian.jira.plugin.system.customfieldtypes:float"}},
                          {"id":"customfield_10020","name":"Sprint",
                           "schema":{"custom":"com.pyxis.greenhopper.jira:gh-sprint"}}
                        ]
                        """));

        assertEquals(
                "customfield_10020",
                fixture.client.sprintFieldId("ACCESS_TOKEN_SECRET", CLOUD_ID)
        );
        fixture.server.verify();
    }

    @Test
    void mapsPartialIssueSprintWithoutTreatingItAsCanonicalDates() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
            String query = URLDecoder.decode(
                    request.getURI().getRawQuery(), StandardCharsets.UTF_8
            );
            assertThat(query).contains("customfield_10020");
        }).andRespond(json("""
                {"isLast":true,"issues":[{
                  "id":"10452","key":"SDP-1",
                  "fields":{"summary":"Issue","updated":"2026-08-04T05:00:00Z",
                    "customfield_10020":[
                      {"id":41,"name":"Old Sprint","state":"closed",
                       "startDate":"2026-07-01T00:00:00Z"},
                      {"id":42,"name":"Current Sprint","state":"active",
                       "startDate":null}
                    ]}
                }]}
                """));

        JiraIssueSnapshot issue = fixture.client.searchIssues(
                "ACCESS_TOKEN_SECRET",
                CLOUD_ID,
                "SDP",
                null,
                Instant.parse("2026-08-04T06:00:00Z"),
                null,
                "customfield_10020"
        ).issues().get(0);

        assertEquals("42", issue.sprintId());
        assertEquals("Current Sprint", issue.sprintName());
        fixture.server.verify();
    }

    @Test
    void mapsCreateMetadataIntoSanitizedProviderDtos() {
        Fixture fixture = fixture();
        String issueTypesUrl = BASE + "/ex/jira/" + CLOUD_ID
                + "/rest/api/3/issue/createmeta/10000/issuetypes?startAt=0&maxResults=100";
        String fieldsUrl = BASE + "/ex/jira/" + CLOUD_ID
                + "/rest/api/3/issue/createmeta/10000/issuetypes/3?startAt=0&maxResults=100";
        fixture.server.expect(requestTo(issueTypesUrl)).andRespond(json("""
                {"startAt":0,"maxResults":100,"total":1,"issueTypes":[{
                  "id":"3","name":"Task","subtask":false,
                  "description":"A delivery item","unexpected":{"raw":"payload"}
                }]}
                """));
        fixture.server.expect(requestTo(fieldsUrl)).andRespond(json("""
                {"startAt":0,"maxResults":100,"total":2,"fields":[
                  {"key":"summary","name":"Summary","required":true,
                   "schema":{"type":"string"},"allowedValues":["one"]},
                  {"key":"customfield_10001","name":"Release","required":false,
                   "schema":{"type":"array","items":"option"},"allowedValues":[
                     {"id":"10","value":"1.0","name":"Release 1.0","secret":"ignored"}
                   ]}
                ]}
                """));

        assertThat(fixture.client.getCreateIssueTypes(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "10000"
        )).containsExactly(new JiraCreateIssueType(
                "3", "Task", false, "A delivery item"
        ));
        assertThat(fixture.client.getCreateFields(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "10000", "3"
        )).containsExactly(
                new JiraCreateField(
                        "summary", "Summary", true, "string", null,
                        List.of(new JiraCreateFieldAllowedValue(null, "one", null))
                ),
                new JiraCreateField(
                        "customfield_10001", "Release", false, "array", "option",
                        List.of(new JiraCreateFieldAllowedValue(
                                "10", "1.0", "Release 1.0"
                        ))
                )
        );
        fixture.server.verify();
    }

    @Test
    void acceptsEmptyCreateMetadata() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> { }).andRespond(json(
                "{\"startAt\":0,\"maxResults\":100,\"total\":0,\"issueTypes\":[]}"
        ));

        assertThat(fixture.client.getCreateIssueTypes(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP"
        )).isEmpty();
        fixture.server.verify();
    }

    @Test
    void getIssueUsesTheCanonicalSearchSnapshotMapper() {
        Fixture fixture = fixture();
        fixture.server.expect(request -> {
            assertEquals(
                    "/ex/jira/" + CLOUD_ID + "/rest/api/3/issue/SDP-42",
                    request.getURI().getPath()
            );
            assertThat(URLDecoder.decode(
                    request.getURI().getRawQuery(), StandardCharsets.UTF_8
            )).contains("labels").contains("components").contains("description");
        }).andRespond(json("""
                {"id":"10452","key":"SDP-42","fields":{
                  "summary":"Canonical issue","issuetype":{"name":"Task"},
                  "status":{"name":"To Do"},"priority":{"name":"High"},
                  "updated":"2026-07-31T00:30:57.360+0700",
                  "labels":["Backend"],"components":[{"id":"10","name":"API"}],
                  "description":{"type":"doc","content":[{"type":"paragraph",
                    "content":[{"type":"text","text":"Safe description"}]}]}
                }}
                """));

        JiraIssueSnapshot issue = fixture.client.getIssue(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP-42"
        );

        assertEquals("10452", issue.id());
        assertEquals("SDP-42", issue.key());
        assertEquals("Canonical issue", issue.title());
        assertThat(issue.labels()).containsExactly("Backend");
        assertThat(issue.components()).containsExactly(
                new TaskComponentSnapshot("10", "API")
        );
        assertEquals("Safe description", issue.description());
        assertThat(issue.assigneeAccountId()).isNull();
        assertThat(issue.sprintId()).isNull();
        assertThat(issue.storyPoints()).isNull();
        fixture.server.verify();
    }

    @Test
    void mapsProviderHttpFailuresWithoutExposingProviderBody() {
        assertProviderFailure(org.springframework.http.HttpStatus.BAD_REQUEST,
                "JIRA_REQUEST_REJECTED", 1);
        assertProviderFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "JIRA_ACCESS_REVOKED", 1);
        assertProviderFailure(org.springframework.http.HttpStatus.FORBIDDEN,
                "JIRA_ACCESS_FORBIDDEN", 1);
        assertProviderFailure(org.springframework.http.HttpStatus.NOT_FOUND,
                "JIRA_RESOURCE_NOT_FOUND", 1);
        assertProviderFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "JIRA_RATE_LIMITED", 3);
        assertProviderFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "JIRA_PROVIDER_UNAVAILABLE", 3);
    }

    @Test
    void getSprintPreservesAuthenticationAccessAndMissingSprintCategories() {
        assertSprintFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "JIRA_ACCESS_REVOKED", 1);
        assertSprintFailure(org.springframework.http.HttpStatus.FORBIDDEN,
                "JIRA_ACCESS_FORBIDDEN", 1);
        assertSprintFailure(org.springframework.http.HttpStatus.NOT_FOUND,
                "JIRA_SPRINT_NOT_FOUND", 1);
        assertSprintFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "JIRA_RATE_LIMITED", 3);
        assertSprintFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "JIRA_PROVIDER_UNAVAILABLE", 3);
    }

    @Test
    void getBoardFeaturesMapsFailuresWithoutExposingProviderBodies() {
        assertBoardFeaturesFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "JIRA_ACCESS_REVOKED", 1);
        assertBoardFeaturesFailure(org.springframework.http.HttpStatus.FORBIDDEN,
                "JIRA_ACCESS_FORBIDDEN", 1);
        assertBoardFeaturesFailure(org.springframework.http.HttpStatus.NOT_FOUND,
                "JIRA_BOARD_NOT_FOUND", 1);
        assertBoardFeaturesFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "JIRA_RATE_LIMITED", 3);
        assertBoardFeaturesFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "JIRA_PROVIDER_UNAVAILABLE", 3);
    }

    @Test
    void boardSprintCapabilityProbeMapsDocumentedHttpSemantics() {
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.BAD_REQUEST,
                "JIRA_SPRINT_CAPABILITY_UNCONFIRMED", 1);
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "JIRA_ACCESS_REVOKED", 1);
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.FORBIDDEN,
                "JIRA_ACCESS_FORBIDDEN", 1);
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.NOT_FOUND,
                "JIRA_BOARD_NOT_FOUND", 1);
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "JIRA_RATE_LIMITED", 3);
        assertBoardSprintProbeFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "JIRA_PROVIDER_UNAVAILABLE", 3);
    }

    @Test
    void boardSprintCapabilityProbeMapsNetworkFailureToProviderUnavailable() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(BOARD_SPRINTS_URL + "?maxResults=1"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withException(new SocketTimeoutException("timeout")));

        assertEquals("JIRA_PROVIDER_UNAVAILABLE", assertThrows(
                IntegrationException.class,
                () -> fixture.client.supportsBoardSprintEndpoint(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "35"
                )
        ).getCode());
        fixture.server.verify();
    }

    @Test
    void mapsTimeoutAndMalformedMetadataResponsesSafely() {
        Fixture timeout = fixture();
        timeout.server.expect(request -> { }).andRespond(
                org.springframework.test.web.client.response.MockRestResponseCreators
                        .withException(new SocketTimeoutException("timeout"))
        );
        assertEquals("JIRA_PROVIDER_UNAVAILABLE", assertThrows(
                IntegrationException.class,
                () -> timeout.client.getCreateIssueTypes(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP"
                )
        ).getCode());

        Fixture malformed = fixture();
        malformed.server.expect(request -> { }).andRespond(json(
                "{\"issueTypes\":[{\"id\":\"3\"}"
        ));
        assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                IntegrationException.class,
                () -> malformed.client.getCreateIssueTypes(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP"
                )
        ).getCode());
        timeout.server.verify();
        malformed.server.verify();
    }

    @Test
    void requiresTheClassicWriteScopeBeforeFutureWriteCalls() {
        JiraWriteScope.requireGranted("read:jira-work write:jira-work");

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> JiraWriteScope.requireGranted("read:jira-work")
        );

        assertEquals("JIRA_SCOPE_INSUFFICIENT", exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("ACCESS_TOKEN_SECRET");
    }

    private void assertProviderFailure(
            org.springframework.http.HttpStatus status,
            String expectedCode,
            int calls
    ) {
        Fixture fixture = fixture();
        fixture.server.expect(ExpectedCount.times(calls), request -> { })
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"PROVIDER_SECRET\"}"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.client.getCreateIssueTypes(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP"
                )
        );

        assertEquals(expectedCode, exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("PROVIDER_SECRET");
        fixture.server.verify();
    }

    private void assertJsonBearerRequest(
            org.springframework.http.client.ClientHttpRequest request
    ) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertThat(authorization != null
                && authorization.startsWith("Bearer ")
                && authorization.length() > "Bearer ".length()).isTrue();
        assertThat(request.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
        assertThat(request.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    private void assertSprintFailure(
            org.springframework.http.HttpStatus status,
            String expectedCode,
            int calls
    ) {
        Fixture fixture = fixture();
        fixture.server.expect(ExpectedCount.times(calls), requestTo(SPRINT_URL))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"PROVIDER_SECRET\"}"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.client.getSprint(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "42"
                )
        );

        assertEquals(expectedCode, exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("PROVIDER_SECRET");
        fixture.server.verify();
    }

    private void assertBoardFeaturesFailure(
            org.springframework.http.HttpStatus status,
            String expectedCode,
            int calls
    ) {
        Fixture fixture = fixture();
        fixture.server.expect(ExpectedCount.times(calls), requestTo(BOARD_FEATURES_URL))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"PROVIDER_SECRET\"}"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.client.getBoardFeatures("ACCESS_TOKEN_SECRET", CLOUD_ID, "35")
        );

        assertEquals(expectedCode, exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("PROVIDER_SECRET");
        fixture.server.verify();
    }

    private void assertBoardSprintProbeFailure(
            org.springframework.http.HttpStatus status,
            String expectedCode,
            int calls
    ) {
        Fixture fixture = fixture();
        fixture.server.expect(ExpectedCount.times(calls), requestTo(BOARD_SPRINTS_URL + "?maxResults=1"))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"PROVIDER_SECRET\"}"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.client.supportsBoardSprintEndpoint(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID, "35"
                )
        );

        assertEquals(expectedCode, exception.getCode());
        assertThat(exception.getMessage()).doesNotContain("PROVIDER_SECRET");
        fixture.server.verify();
    }

    private JiraIssueSnapshot searchFirstIssue(String labelsJson) {
        return searchFirstIssueWithRaw(labelsJson, null, null);
    }

    private JiraIssueSnapshot searchFirstIssueWithRaw(
            String labelsJson,
            String componentsJson,
            String descriptionJson
    ) {
        Fixture fixture = fixture();
        String labelsField = optionalJsonField("labels", labelsJson);
        String componentsField = optionalJsonField("components", componentsJson);
        String descriptionField = optionalJsonField("description", descriptionJson);
        fixture.server.expect(request -> { }).andRespond(json("""
                {"isLast":true,"issues":[{
                  "id":"10452","key":"SDP-1",
                  "fields":{"summary":"Labels test",
                    "updated":"2026-07-31T00:30:57.360+0700"%s%s%s}
                }]}
                """.formatted(labelsField, componentsField, descriptionField)));

        JiraIssueSnapshot issue = fixture.client.searchIssues(
                "ACCESS_TOKEN_SECRET",
                CLOUD_ID,
                "SDP",
                null,
                Instant.parse("2026-07-31T00:31:02Z"),
                null
        ).issues().get(0);
        fixture.server.verify();
        return issue;
    }

    private String optionalJsonField(String name, String json) {
        return json == null ? "" : ",\"" + name + "\":" + json;
    }

    @Test
    void formatsUtcSearchBoundsInConfiguredJiraZoneAcrossMidnight() {
        Fixture fixture = fixture("Asia/Ho_Chi_Minh");
        fixture.server.expect(request -> {
            String query = URLDecoder.decode(
                    request.getURI().getRawQuery(), StandardCharsets.UTF_8
            );
            assertThat(query)
                    .contains("updated >= \"2026-07-31 04:22\"")
                    .contains("updated < \"2026-07-31 04:23\"")
                    .doesNotContainPattern(
                            "updated [<>]=? \\\"\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"
                    );
        }).andExpect(method(HttpMethod.GET)).andRespond(json(
                "{\"isLast\":true,\"issues\":[]}"
        ));

        fixture.client.searchIssues(
                "ACCESS_TOKEN_SECRET",
                CLOUD_ID,
                "SDP",
                Instant.parse("2026-07-30T21:22:18Z"),
                Instant.parse("2026-07-30T21:22:30Z"),
                null
        );

        fixture.server.verify();
    }

    @Test
    void treatsMissingValuesAndMalformedJsonAsInvalidWithoutLoggingRawBody() {
        Fixture missingValues = fixture();
        missingValues.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andRespond(json("{\"isLast\":true}"));
        assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                IntegrationException.class,
                () -> missingValues.client.listWebhooks(
                        "ACCESS_TOKEN_SECRET", CLOUD_ID
                )
        ).getCode());

        Fixture malformed = fixture();
        Logger logger = (Logger) LoggerFactory.getLogger(
                JiraProviderClientImpl.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        malformed.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andRespond(json("{\"token\":\"RESPONSE_SECRET\","));
        try {
            assertEquals("JIRA_RESPONSE_INVALID", assertThrows(
                    IntegrationException.class,
                    () -> malformed.client.listWebhooks(
                            "ACCESS_TOKEN_SECRET", CLOUD_ID
                    )
            ).getCode());
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);
            assertThat(logged)
                    .contains("operation=GET")
                .doesNotContain("<redacted>")
                    .doesNotContain("RESPONSE_SECRET")
                    .doesNotContain("ACCESS_TOKEN_SECRET");
        } finally {
            logger.detachAppender(appender);
        }
        missingValues.server.verify();
        malformed.server.verify();
    }

    private Fixture fixture() {
        return fixture("UTC");
    }

    private Fixture fixture(String jiraTimeZone) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
                .build();
        RestClient restClient = builder.build();
        return new Fixture(
                new JiraProviderClientImpl(
                        new JiraIntegrationProperties(
                                true, "id", "secret", "", "", BASE,
                                "", "", "read:jira-work manage:jira-webhook"
                        ),
                        new JiraTimeZoneProperties(jiraTimeZone),
                        JsonMapper.builder().build(),
                        restClient
                ),
                server
        );
    }

    private org.springframework.test.web.client.response.DefaultResponseCreator json(
            String body
    ) {
        return withStatus(org.springframework.http.HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private String listResponse(String id, String callback) {
        return page(true, 0, 100, 1, webhookValue(id, callback, ""));
    }

    private String page(
            boolean isLast,
            int startAt,
            int maxResults,
            int total,
            String values
    ) {
        return "{\"isLast\":" + isLast + ",\"maxResults\":" + maxResults
                + ",\"startAt\":" + startAt + ",\"total\":" + total
                + ",\"values\":[" + values + "]}";
    }

    private String webhookValue(String id, String callback, String extraFields) {
        String events = EVENTS.stream()
                .map(event -> "\"" + event + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String extra = extraFields == null || extraFields.isBlank()
                ? ""
                : "," + extraFields;
        return "{\"id\":\"" + id + "\",\"url\":\""
                + callback + "\",\"jqlFilter\":\"project = SDP\","
                + "\"events\":[" + events + "]" + extra + "}";
    }

    private record Fixture(
            JiraProviderClientImpl client,
            MockRestServiceServer server
    ) {
    }
}
