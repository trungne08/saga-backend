package com.saga.be.integration.webhook;

import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JiraWebhookJwtVerifier {

    private static final long CLOCK_SKEW_SECONDS = 30;
    private static final int MAX_TOKEN_LENGTH = 8192;

    private final JiraIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JiraWebhookJwtVerifier(
            JiraIntegrationProperties properties,
            ObjectMapper objectMapper
    ) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    JiraWebhookJwtVerifier(
            JiraIntegrationProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void verify(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        String[] segments = token.split("\\.", -1);
        if (
            segments.length != 3
            || segments[0].isBlank()
            || segments[1].isBlank()
            || segments[2].isBlank()
        ) {
            reject();
        }

        try {
            JsonNode header = decodeJson(segments[0]);
            JsonNode claims = decodeJson(segments[1]);
            if (
                !header.isObject()
                || !claims.isObject()
                || !"HS256".equals(header.path("alg").asText())
                || hasCriticalHeaders(header)
                || hasInvalidType(header)
            ) {
                reject();
            }

            byte[] signature = Base64.getUrlDecoder().decode(segments[2]);
            if (signature.length != 32) {
                reject();
            }
            byte[] expected = hmac(
                    segments[0] + "." + segments[1],
                    clientSecret()
            );
            if (!MessageDigest.isEqual(expected, signature)) {
                reject();
            }
            validateTimeClaims(claims);
        } catch (IllegalArgumentException exception) {
            reject();
        }
    }

    private String bearerToken(String authorizationHeader) {
        if (
            authorizationHeader == null
            || authorizationHeader.length() > MAX_TOKEN_LENGTH + 7
        ) {
            reject();
        }
        int separator = authorizationHeader.indexOf(' ');
        if (
            separator < 0
            || !"bearer".equals(
                    authorizationHeader.substring(0, separator)
                            .toLowerCase(Locale.ROOT)
            )
        ) {
            reject();
        }
        String token = authorizationHeader.substring(separator + 1);
        if (
            token.isBlank()
            || token.length() > MAX_TOKEN_LENGTH
            || !token.equals(token.trim())
        ) {
            reject();
        }
        return token;
    }

    private JsonNode decodeJson(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            JsonNode value = objectMapper.readTree(decoded);
            if (value == null) {
                reject();
            }
            return value;
        } catch (Exception exception) {
            reject();
            throw new IllegalStateException("Unreachable");
        }
    }

    private boolean hasCriticalHeaders(JsonNode header) {
        JsonNode critical = header.path("crit");
        return !critical.isMissingNode()
                && (!critical.isArray() || !critical.isEmpty());
    }

    private boolean hasInvalidType(JsonNode header) {
        JsonNode type = header.path("typ");
        return !type.isMissingNode() && !"JWT".equals(type.asText());
    }

    private void validateTimeClaims(JsonNode claims) {
        long now = Instant.now(clock).getEpochSecond();
        JsonNode expiration = claims.path("exp");
        if (
            !expiration.isIntegralNumber()
            || expiration.asLong() <= now - CLOCK_SKEW_SECONDS
        ) {
            reject();
        }
        JsonNode notBefore = claims.path("nbf");
        if (
            !notBefore.isMissingNode()
            && (
                !notBefore.isIntegralNumber()
                || notBefore.asLong() > now + CLOCK_SKEW_SECONDS
            )
        ) {
            reject();
        }
        JsonNode issuedAt = claims.path("iat");
        if (
            !issuedAt.isMissingNode()
            && (
                !issuedAt.isIntegralNumber()
                || issuedAt.asLong() > now + CLOCK_SKEW_SECONDS
            )
        ) {
            reject();
        }
    }

    private byte[] hmac(String signingInput, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC SHA-256 is unavailable", exception);
        }
    }

    private String clientSecret() {
        String secret = properties.clientSecret();
        if (secret == null || secret.isBlank()) {
            throw new IntegrationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "JIRA_WEBHOOK_VERIFICATION_NOT_CONFIGURED",
                    "Jira webhook verification is not configured"
            );
        }
        return secret;
    }

    private void reject() {
        throw IntegrationException.forbidden(
                "The Jira webhook authorization is invalid"
        );
    }
}
