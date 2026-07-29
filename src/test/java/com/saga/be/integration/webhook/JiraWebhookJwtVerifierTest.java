package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JiraWebhookJwtVerifierTest {

    private static final String SECRET = "atlassian-client-secret";
    private static final Instant NOW = Instant.parse("2026-07-29T05:00:00Z");

    private JiraWebhookJwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JiraWebhookJwtVerifier(
                properties(SECRET),
                JsonMapper.builder().build(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void acceptsValidAtlassianBearerJwtSignedWithClientSecret() {
        String jwt = jwt(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"iat\":1785301170,\"exp\":1785301500}",
                SECRET
        );

        assertDoesNotThrow(() -> verifier.verify("Bearer " + jwt));
    }

    @Test
    void rejectsTamperedSignature() {
        String jwt = jwt(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"iat\":1785301170,\"exp\":1785301500}",
                "wrong-secret"
        );

        assertThrows(
                IntegrationException.class,
                () -> verifier.verify("Bearer " + jwt)
        );
    }

    @Test
    void rejectsUnsupportedAlgorithmBeforeTrustingClaims() {
        String jwt = jwt(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}",
                "{\"exp\":1785301500}",
                SECRET
        );

        assertThrows(
                IntegrationException.class,
                () -> verifier.verify("Bearer " + jwt)
        );
    }

    @Test
    void rejectsExpiredOrMissingExpiration() {
        String expired = jwt(
                "{\"alg\":\"HS256\"}",
                "{\"exp\":1785300000}",
                SECRET
        );
        String noExpiration = jwt(
                "{\"alg\":\"HS256\"}",
                "{\"iat\":1785301170}",
                SECRET
        );

        assertThrows(
                IntegrationException.class,
                () -> verifier.verify("Bearer " + expired)
        );
        assertThrows(
                IntegrationException.class,
                () -> verifier.verify("Bearer " + noExpiration)
        );
    }

    private String jwt(String header, String claims, String secret) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(
                header.getBytes(StandardCharsets.UTF_8)
        ) + "." + encoder.encodeToString(
                claims.getBytes(StandardCharsets.UTF_8)
        );
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return signingInput
                    + "."
                    + encoder.encodeToString(mac.doFinal(
                            signingInput.getBytes(StandardCharsets.US_ASCII)
                    ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JiraIntegrationProperties properties(String clientSecret) {
        return new JiraIntegrationProperties(
                "client-id",
                clientSecret,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
