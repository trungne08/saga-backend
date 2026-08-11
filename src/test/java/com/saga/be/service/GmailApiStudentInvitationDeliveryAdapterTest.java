package com.saga.be.service;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;

import com.saga.be.entity.enums.StudentInvitationType;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GmailApiStudentInvitationDeliveryAdapterTest {

    private static final String ACCESS_TOKEN = "runtime-access-token";
    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void refreshesTokenAndSendsUtf8MultipartMessageAsBase64UrlRaw() {
        Fixture fixture = fixture();
        AtomicReference<String> requestBody = new AtomicReference<>();
        expectToken(fixture, 3600);
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> requestBody.set(
                        ((MockClientHttpRequest) request).getBodyAsString()
                ))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":\"provider-message-id\"}"));

        fixture.adapter.deliver(message());

        JsonNode json = fixture.mapper.readTree(requestBody.get());
        String raw = json.path("raw").asText();
        assertFalse(raw.contains("="));
        String mime = new String(
                Base64.getUrlDecoder().decode(raw),
                StandardCharsets.UTF_8
        );
        assertTrue(mime.contains("From: =?UTF-8?B?U0FHQSBUZWFt?= <sender@example.test>"));
        assertTrue(mime.contains("To: student@example.test"));
        assertTrue(mime.contains("Subject: =?UTF-8?B?"));
        assertTrue(mime.contains("Content-Type: multipart/alternative"));
        assertTrue(mime.contains("Content-Type: text/plain; charset=UTF-8"));
        assertTrue(mime.contains("Content-Type: text/html; charset=UTF-8"));
        assertTrue(mime.contains(Base64.getEncoder().encodeToString(
                message().body().getBytes(StandardCharsets.UTF_8)
        )));
        assertTrue(mime.contains(Base64.getMimeEncoder(
                76,
                new byte[]{'\r', '\n'}
        ).encodeToString(message().htmlBody().getBytes(StandardCharsets.UTF_8))));
        fixture.server.verify();
    }

    @Test
    void tokenRequestUsesExactRefreshGrantAndCachesAccessToken() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("client_id=test-client"),
                        containsString("client_secret=test-secret"),
                        containsString("refresh_token=test-refresh"),
                        containsString("grant_type=refresh_token")
                )))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"expires_in\":3600}"));
        fixture.server.expect(manyTimes(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andRespond(withStatus(HttpStatus.OK));

        fixture.adapter.deliver(message());
        fixture.adapter.deliver(message());

        fixture.server.verify();
    }

    @Test
    void malformedTokenResponseIsNonRetryableAndNeverCallsSend() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"expires_in\":3600}"));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, "TOKEN_RESPONSE_INVALID", false, 200);
        fixture.server.verify();
    }

    @Test
    void revokedRefreshTokenIsNonRetryableAndSanitized() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\","
                                + "\"error_description\":\"sensitive detail\"}"));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, "REFRESH_TOKEN_INVALID", false, 400);
        assertFalse(exception.getMessage().contains("sensitive detail"));
        fixture.server.verify();
    }

    @Test
    void tokenUnauthorizedIsNonRetryableClientCredentialFailure() {
        assertTokenFailure(
                HttpStatus.UNAUTHORIZED,
                "{}",
                "CLIENT_CREDENTIALS_INVALID",
                false
        );
    }

    @Test
    void tokenRateLimitIsRetryable() {
        assertTokenFailure(
                HttpStatus.TOO_MANY_REQUESTS,
                "{}",
                "TOKEN_RATE_LIMITED",
                true
        );
    }

    @Test
    void tokenProvider5xxIsRetryable() {
        assertTokenFailure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "not-json",
                "TOKEN_PROVIDER_UNAVAILABLE",
                true
        );
    }

    @Test
    void tokenNetworkTimeoutIsRetryable() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andRespond(withException(new SocketTimeoutException("test timeout")));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, "TOKEN_NETWORK", true, null);
        fixture.server.verify();
    }

    @Test
    void unauthorizedSendIsNonRetryableAndInvalidatesCachedToken() {
        Fixture fixture = fixture();
        expectToken(fixture, 3600);
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"secret provider message\"}}"));
        expectToken(fixture, 3600);
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andRespond(withStatus(HttpStatus.OK));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );
        fixture.adapter.deliver(message());

        assertFailure(exception, "GMAIL_UNAUTHORIZED", false, 401);
        assertFalse(exception.getMessage().contains("secret provider message"));
        fixture.server.verify();
    }

    @Test
    void permission403IsNotRetryable() {
        assertSendFailure(
                HttpStatus.FORBIDDEN,
                "{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}",
                "GMAIL_FORBIDDEN",
                false
        );
    }

    @Test
    void malformedMessage400IsNonRetryable() {
        assertSendFailure(
                HttpStatus.BAD_REQUEST,
                "{\"error\":{\"message\":\"provider detail\"}}",
                "GMAIL_MESSAGE_REJECTED",
                false
        );
    }

    @Test
    void quota403IsRetryable() {
        assertSendFailure(
                HttpStatus.FORBIDDEN,
                "{\"error\":{\"errors\":[{\"reason\":\"userRateLimitExceeded\"}]}}",
                "GMAIL_RATE_LIMITED",
                true
        );
    }

    @Test
    void tooManyRequestsIsRetryable() {
        assertSendFailure(HttpStatus.TOO_MANY_REQUESTS, "{}", "GMAIL_RATE_LIMITED", true);
    }

    @Test
    void provider5xxIsRetryable() {
        assertSendFailure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "not-json",
                "GMAIL_PROVIDER_UNAVAILABLE",
                true
        );
    }

    @Test
    void sendNetworkTimeoutIsRetryable() {
        Fixture fixture = fixture();
        expectToken(fixture, 3600);
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andRespond(withException(new SocketTimeoutException("test timeout")));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, "GMAIL_NETWORK", true, null);
        fixture.server.verify();
    }

    @Test
    void headerInjectionIsRejectedBeforeAnyProviderCall() {
        Fixture fixture = fixture();
        StudentInvitationMessage unsafe = new StudentInvitationMessage(
                message().recipientEmail(),
                "Subject\r\nBcc: attacker@example.test",
                message().body(),
                message().htmlBody(),
                message().invitationType(),
                message().courseName(),
                message().teamNames(),
                message().loginUri(),
                message().attemptNumber()
        );

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(unsafe)
        );

        assertFailure(exception, "MESSAGE_BUILD", false, null);
        fixture.server.verify();
    }

    private void assertSendFailure(
            HttpStatus status,
            String body,
            String category,
            boolean retryable
    ) {
        Fixture fixture = fixture();
        expectToken(fixture, 3600);
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.SEND_URI
                ))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, category, retryable, status.value());
        fixture.server.verify();
    }

    private void assertTokenFailure(
            HttpStatus status,
            String body,
            String category,
            boolean retryable
    ) {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> fixture.adapter.deliver(message())
        );

        assertFailure(exception, category, retryable, status.value());
        fixture.server.verify();
    }

    private void expectToken(Fixture fixture, long expiresIn) {
        fixture.server.expect(once(), requestTo(
                        GmailApiStudentInvitationDeliveryAdapter.TOKEN_URI
                ))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"expires_in\":" + expiresIn + "}"));
    }

    private void assertFailure(
            StudentInvitationDeliveryException exception,
            String category,
            boolean retryable,
            Integer status
    ) {
        assertEquals(category, exception.getCategory());
        assertEquals(retryable, exception.isRetryable());
        assertEquals(status, exception.getHttpStatus());
        assertEquals("Student invitation delivery failed", exception.getMessage());
    }

    private Fixture fixture() {
        var mapper = JsonMapper.builder().build();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiStudentInvitationDeliveryAdapter adapter =
                new GmailApiStudentInvitationDeliveryAdapter(
                        builder.build(),
                        mapper,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        "test-client",
                        "test-secret",
                        "test-refresh",
                        "sender@example.test",
                        "SAGA Team"
                );
        return new Fixture(adapter, server, mapper);
    }

    private StudentInvitationMessage message() {
        return new StudentInvitationMessage(
                "student@example.test",
                "Mời tham gia khóa học",
                "Lời mời dạng text",
                "<html><body><a href=\"https://frontend.example.test/login\">"
                        + "Đăng nhập SAGA</a></body></html>",
                StudentInvitationType.LINKED_STUDENT,
                "Khóa học",
                List.of(),
                URI.create("https://frontend.example.test/login"),
                2
        );
    }

    private record Fixture(
            GmailApiStudentInvitationDeliveryAdapter adapter,
            MockRestServiceServer server,
            tools.jackson.databind.ObjectMapper mapper
    ) {
    }
}
