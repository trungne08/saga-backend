package com.saga.be.integration.webhook;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraBoardRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class JiraWebhookAuthenticator {

    private final JiraBoardRepository boardRepository;
    private final JiraWebhookJwtVerifier jwtVerifier;

    public JiraWebhookAuthenticator(
            JiraBoardRepository boardRepository,
            JiraWebhookJwtVerifier jwtVerifier
    ) {
        this.boardRepository = boardRepository;
        this.jwtVerifier = jwtVerifier;
    }

    public JiraBoard authenticate(
            String authorizationHeader,
            String connectionToken
    ) {
        jwtVerifier.verify(authorizationHeader);
        if (
            connectionToken == null
            || connectionToken.length() < 32
            || connectionToken.length() > 128
        ) {
            throw IntegrationException.forbidden(
                    "The Jira webhook credential is invalid"
            );
        }
        String computedHash = sha256(connectionToken);
        JiraBoard board = boardRepository
                .findByWebhookSecretHashAndConnectionStatusNot(
                        computedHash,
                        IntegrationStatus.DISCONNECTED
                )
                .orElseThrow(() -> IntegrationException.forbidden(
                        "The Jira webhook credential is invalid"
                ));
        if (!MessageDigest.isEqual(
                computedHash.getBytes(StandardCharsets.US_ASCII),
                board.getWebhookSecretHash()
                        .getBytes(StandardCharsets.US_ASCII)
        )) {
            throw IntegrationException.forbidden(
                    "The Jira webhook credential is invalid"
            );
        }
        return board;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
