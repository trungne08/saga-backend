package com.saga.be.integration.webhook;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.exception.IntegrationException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private final GitHubIntegrationProperties properties;

    public GitHubWebhookSignatureVerifier(
            GitHubIntegrationProperties properties
    ) {
        this.properties = properties;
    }

    public void verify(byte[] payload, String signatureHeader) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw IntegrationException.forbidden(
                    "GitHub webhook verification is not configured"
            );
        }
        if (
            signatureHeader == null
            || !signatureHeader.startsWith(PREFIX)
            || signatureHeader.length() != PREFIX.length() + 64
        ) {
            throw IntegrationException.forbidden(
                    "The GitHub webhook signature is invalid"
            );
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(
                    signatureHeader.substring(PREFIX.length())
            );
        } catch (IllegalArgumentException exception) {
            throw IntegrationException.forbidden(
                    "The GitHub webhook signature is invalid"
            );
        }
        byte[] expected = hmac(payload, secret);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw IntegrationException.forbidden(
                    "The GitHub webhook signature is invalid"
            );
        }
    }

    private byte[] hmac(byte[] payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return mac.doFinal(payload);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC SHA-256 is unavailable", exception);
        }
    }
}
