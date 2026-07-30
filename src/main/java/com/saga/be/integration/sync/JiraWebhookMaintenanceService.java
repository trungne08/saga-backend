package com.saga.be.integration.sync;

import com.saga.be.config.IntegrationUrlResolver;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.JiraBoardRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class JiraWebhookMaintenanceService {

    private final JiraBoardRepository boardRepository;
    private final JiraCredentialService credentialService;
    private final JiraProviderClient jiraClient;
    private final IntegrationUrlResolver urlResolver;
    private final SecureRandom random = new SecureRandom();

    public JiraWebhookMaintenanceService(
            JiraBoardRepository boardRepository,
            JiraCredentialService credentialService,
            JiraProviderClient jiraClient,
            IntegrationUrlResolver urlResolver
    ) {
        this.boardRepository = boardRepository;
        this.credentialService = credentialService;
        this.jiraClient = jiraClient;
        this.urlResolver = urlResolver;
    }

    public void refresh(UUID boardId) {
        JiraBoard board = boardRepository.findById(boardId).orElse(null);
        if (
            board == null
            || board.getConnectionStatus() == IntegrationStatus.DISCONNECTED
        ) {
            return;
        }
        try {
            String token = credentialService.validAccessToken(board);
            String secret = randomSecret();
            String newWebhookId = jiraClient.registerWebhook(
                    token,
                    board.getCloudId(),
                    board.getProjectKey(),
                    callback(secret)
            );
            String oldWebhookId = board.getWebhookId();
            board.setWebhookId(newWebhookId);
            board.setWebhookSecretHash(sha256(secret));
            board.setWebhookExpiresAt(LocalDateTime.now().plusDays(29));
            boardRepository.saveAndFlush(board);
            if (oldWebhookId != null) {
                jiraClient.deleteWebhook(
                        token,
                        board.getCloudId(),
                        oldWebhookId
                );
            }
        } catch (RuntimeException exception) {
            board.setConnectionStatus(IntegrationStatus.DEGRADED);
            board.setConsecutiveFailures(board.getConsecutiveFailures() + 1);
            boardRepository.saveAndFlush(board);
        }
    }

    private URI callback(String secret) {
        return UriComponentsBuilder.fromUriString(urlResolver.jiraWebhookPublicUrl())
                .queryParam("token", secret)
                .build()
                .encode()
                .toUri();
    }

    private String randomSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
