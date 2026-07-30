package com.saga.be.integration.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class JiraProviderClientImplTest {

    private static final String BASE = "https://api.atlassian.test";
    private static final String CLOUD_ID = "cloud-123";
    private static final String WEBHOOK_URL = BASE
            + "/ex/jira/" + CLOUD_ID + "/rest/api/3/webhook";
    private static final String FIRST_WEBHOOK_PAGE_URL = WEBHOOK_URL
            + "?startAt=0&maxResults=100";
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
    void exposesProviderErrorsFromHttp200WithoutLoggingSecrets() {
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
                        .body("{\"webhookRegistrationResult\":[{\"errors\":[\"JQL is invalid\"]}]}"));

        try {
            IntegrationException error = assertThrows(
                    IntegrationException.class,
                    () -> fixture.client.registerWebhook(
                            "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK
                    )
            );
            assertEquals("JIRA_WEBHOOK_REGISTRATION_REJECTED", error.getCode());
            assertThat(error.getMessage()).contains("JQL is invalid");
            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", String::concat);
            assertThat(logged)
                    .doesNotContain("ACCESS_TOKEN_SECRET")
                    .doesNotContain("CALLBACK_SECRET");
        } finally {
            logger.detachAppender(appender);
        }
        fixture.server.verify();
    }

    @Test
    void reusesMatchingExistingWebhookWithoutPostingAnother() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse("12", CALLBACK.toString())));

        JiraWebhookRegistration result = fixture.client.ensureWebhook(
                "ACCESS_TOKEN_SECRET", CLOUD_ID, "SDP", CALLBACK, "12"
        );

        assertEquals("12", result.webhookId());
        assertThat(result.created()).isFalse();
        fixture.server.verify();
    }

    @Test
    void replacesOnlyTheBoardWebhookWhenCallbackUrlIsOld() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(FIRST_WEBHOOK_PAGE_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(json(listResponse(
                        "9",
                        "https://example.com/api/webhooks/jira?token=old"
                )));
        fixture.server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));
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
                LocalDateTime.parse("2026-07-31T00:25:57"),
                LocalDateTime.parse("2026-07-31T00:32:00"),
                null
        );

        assertThat(page.issues()).hasSize(1);
        assertEquals("SDP-1", page.issues().get(0).key());
        assertEquals(
                LocalDateTime.parse("2026-07-31T00:30:57.360"),
                page.issues().get(0).updatedAt()
        );
        assertThat(page.last()).isTrue();
        assertThat(page.nextPageToken()).isNull();
        fixture.server.verify();
    }

    @Test
    void treatsMissingValuesAndMalformedJsonAsInvalidAndRedactsBody() {
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
                    .contains("<redacted>")
                    .doesNotContain("RESPONSE_SECRET")
                    .doesNotContain("ACCESS_TOKEN_SECRET");
        } finally {
            logger.detachAppender(appender);
        }
        missingValues.server.verify();
        malformed.server.verify();
    }

    private Fixture fixture() {
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
