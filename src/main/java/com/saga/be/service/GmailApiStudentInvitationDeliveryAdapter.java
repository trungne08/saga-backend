package com.saga.be.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class GmailApiStudentInvitationDeliveryAdapter
        implements StudentInvitationDeliveryAdapter {

    static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    static final String SEND_URI =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(60);
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+$"
    );
    private static final Set<String> RETRYABLE_GMAIL_REASONS = Set.of(
            "ratelimitexceeded",
            "userratelimitexceeded",
            "quotaexceeded",
            "backenderror"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String senderEmail;
    private final String senderName;
    private final Object tokenRefreshLock = new Object();
    private volatile CachedAccessToken cachedAccessToken;

    public GmailApiStudentInvitationDeliveryAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            String clientId,
            String clientSecret,
            String refreshToken,
            String senderEmail,
            String senderName
    ) {
        this(
                restClient,
                objectMapper,
                Clock.systemUTC(),
                clientId,
                clientSecret,
                refreshToken,
                senderEmail,
                senderName
        );
    }

    GmailApiStudentInvitationDeliveryAdapter(
            RestClient restClient,
            ObjectMapper objectMapper,
            Clock clock,
            String clientId,
            String clientSecret,
            String refreshToken,
            String senderEmail,
            String senderName
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    @Override
    public void deliver(StudentInvitationMessage message) {
        String rawMessage = encodeRawMessage(message);
        String accessToken = accessToken();
        try {
            restClient.post()
                    .uri(SEND_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(gmailRequestBody(rawMessage))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401) {
                invalidate(accessToken);
            }
            throw translateGmailFailure(exception);
        } catch (RestClientException exception) {
            throw failure("GMAIL_NETWORK", true, null, exception);
        }
    }

    private String accessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (usable(current)) {
            return current.value();
        }
        synchronized (tokenRefreshLock) {
            current = cachedAccessToken;
            if (usable(current)) {
                return current.value();
            }
            cachedAccessToken = refreshAccessToken();
            return cachedAccessToken.value();
        }
    }

    private CachedAccessToken refreshAccessToken() {
        try {
            String body = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRequestBody())
                    .retrieve()
                    .body(String.class);
            JsonNode response = readJson(body);
            String accessToken = response.path("access_token").asText("");
            long expiresIn = response.path("expires_in").asLong(0);
            if (accessToken.isBlank() || expiresIn <= 0) {
                throw failure(
                        "TOKEN_RESPONSE_INVALID",
                        false,
                        200,
                        new IllegalStateException("Required token fields are absent")
                );
            }
            return new CachedAccessToken(
                    accessToken,
                    clock.instant().plusSeconds(expiresIn)
            );
        } catch (StudentInvitationDeliveryException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw translateTokenFailure(exception);
        } catch (RestClientException exception) {
            throw failure("TOKEN_NETWORK", true, null, exception);
        } catch (RuntimeException exception) {
            throw failure("TOKEN_RESPONSE_INVALID", false, 200, exception);
        }
    }

    private boolean usable(CachedAccessToken token) {
        return token != null
                && token.expiresAt().isAfter(clock.instant().plus(TOKEN_EXPIRY_MARGIN));
    }

    private void invalidate(String rejectedToken) {
        synchronized (tokenRefreshLock) {
            if (cachedAccessToken != null
                    && cachedAccessToken.value().equals(rejectedToken)) {
                cachedAccessToken = null;
            }
        }
    }

    private StudentInvitationDeliveryException translateTokenFailure(
            RestClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();
        String error = safeText(responseBody(exception).path("error"));
        if (status == 429) {
            return failure("TOKEN_RATE_LIMITED", true, status, exception);
        }
        if (status >= 500) {
            return failure("TOKEN_PROVIDER_UNAVAILABLE", true, status, exception);
        }
        if ("invalid_grant".equals(error)) {
            return failure("REFRESH_TOKEN_INVALID", false, status, exception);
        }
        if ("invalid_client".equals(error) || status == 401) {
            return failure("CLIENT_CREDENTIALS_INVALID", false, status, exception);
        }
        return failure("TOKEN_REFRESH_REJECTED", false, status, exception);
    }

    private StudentInvitationDeliveryException translateGmailFailure(
            RestClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();
        if (status == 401) {
            return failure("GMAIL_UNAUTHORIZED", false, status, exception);
        }
        if (status == 403) {
            boolean retryable = gmailReasons(responseBody(exception)).stream()
                    .anyMatch(RETRYABLE_GMAIL_REASONS::contains);
            return failure(
                    retryable ? "GMAIL_RATE_LIMITED" : "GMAIL_FORBIDDEN",
                    retryable,
                    status,
                    exception
            );
        }
        if (status == 429) {
            return failure("GMAIL_RATE_LIMITED", true, status, exception);
        }
        if (status >= 500) {
            return failure("GMAIL_PROVIDER_UNAVAILABLE", true, status, exception);
        }
        return failure("GMAIL_MESSAGE_REJECTED", false, status, exception);
    }

    private Set<String> gmailReasons(JsonNode body) {
        java.util.HashSet<String> reasons = new java.util.HashSet<>();
        JsonNode error = body.path("error");
        JsonNode errors = error.path("errors");
        if (errors.isArray()) {
            errors.forEach(item -> addReason(reasons, item.path("reason")));
        }
        addReason(reasons, error.path("status"));
        return Set.copyOf(reasons);
    }

    private void addReason(Set<String> reasons, JsonNode node) {
        String value = safeText(node);
        if (!value.isBlank()) {
            reasons.add(value.toLowerCase(Locale.ROOT));
        }
    }

    private String safeText(JsonNode node) {
        return node == null || !node.isValueNode() ? "" : node.asText("");
    }

    private JsonNode responseBody(RestClientResponseException exception) {
        return readJson(exception.getResponseBodyAsString());
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(value);
        } catch (RuntimeException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private StudentInvitationDeliveryException failure(
            String category,
            boolean retryable,
            Integer httpStatus,
            Throwable exception
    ) {
        return new StudentInvitationDeliveryException(
                category,
                retryable,
                httpStatus,
                exception
        );
    }

    private byte[] tokenRequestBody() {
        return ("client_id=" + formValue(clientId)
                + "&client_secret=" + formValue(clientSecret)
                + "&refresh_token=" + formValue(refreshToken)
                + "&grant_type=refresh_token")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String formValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private byte[] gmailRequestBody(String rawMessage) {
        try {
            return objectMapper.writeValueAsBytes(
                    objectMapper.createObjectNode().put("raw", rawMessage)
            );
        } catch (RuntimeException exception) {
            throw failure("MESSAGE_BUILD", false, null, exception);
        }
    }

    private String encodeRawMessage(StudentInvitationMessage message) {
        requireSafeHeader(message.recipientEmail(), "recipient");
        requireSafeHeader(message.subject(), "subject");
        requireSafeHeader(senderEmail, "sender email");
        requireSafeHeader(senderName, "sender name");
        if (!looksLikeEmail(message.recipientEmail()) || !looksLikeEmail(senderEmail)) {
            throw failure(
                    "MESSAGE_BUILD",
                    false,
                    null,
                    new IllegalArgumentException("Invalid email address")
            );
        }

        String boundary = "saga-" + UUID.randomUUID();
        String mime = "From: " + encodedWord(senderName) + " <" + senderEmail + ">\r\n"
                + "To: " + message.recipientEmail() + "\r\n"
                + "Subject: " + encodedWord(message.subject()) + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative; boundary=\"" + boundary + "\"\r\n"
                + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + mimeBase64(message.body()) + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + mimeBase64(message.htmlBody()) + "\r\n"
                + "--" + boundary + "--\r\n";
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mime.getBytes(StandardCharsets.UTF_8));
    }

    private String mimeBase64(String value) {
        return Base64.getMimeEncoder(76, new byte[]{'\r', '\n'})
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encodedWord(String value) {
        return "=?UTF-8?B?"
                + Base64.getEncoder().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        )
                + "?=";
    }

    private void requireSafeHeader(String value, String field) {
        if (value == null || value.isBlank()
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw failure(
                    "MESSAGE_BUILD",
                    false,
                    null,
                    new IllegalArgumentException("Invalid " + field)
            );
        }
    }

    private boolean looksLikeEmail(String value) {
        return EMAIL_ADDRESS.matcher(value).matches();
    }

    private record CachedAccessToken(String value, Instant expiresAt) {
    }
}
