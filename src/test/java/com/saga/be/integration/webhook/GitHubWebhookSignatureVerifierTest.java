package com.saga.be.integration.webhook;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GitHubWebhookSignatureVerifierTest {

    private static final String SECRET = "production-grade-webhook-secret";

    @Test
    void acceptsValidSha256Signature() {
        byte[] payload = "{\"action\":\"opened\"}"
                .getBytes(StandardCharsets.UTF_8);
        GitHubWebhookSignatureVerifier verifier = verifier(SECRET);

        assertDoesNotThrow(() -> verifier.verify(payload, signature(payload)));
    }

    @Test
    void rejectsTamperedPayload() {
        byte[] original = "{\"action\":\"opened\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"action\":\"closed\"}"
                .getBytes(StandardCharsets.UTF_8);

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> verifier(SECRET).verify(tampered, signature(original))
        );
        assertEquals("INTEGRATION_FORBIDDEN", exception.getCode());
    }

    @Test
    void rejectsMissingOrMalformedSignature() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(
                IntegrationException.class,
                () -> verifier(SECRET).verify(payload, null)
        );
        assertThrows(
                IntegrationException.class,
                () -> verifier(SECRET).verify(payload, "sha1=abcd")
        );
    }

    @Test
    void rejectsRequestsWhenSecretIsNotConfigured() {
        assertThrows(
                IntegrationException.class,
                () -> verifier("").verify(new byte[0], "sha256=" + "0".repeat(64))
        );
    }

    private GitHubWebhookSignatureVerifier verifier(String secret) {
        return new GitHubWebhookSignatureVerifier(
                new GitHubIntegrationProperties(
                        null,
                        null,
                        null,
                        null,
                        secret,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private String signature(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    SECRET.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
