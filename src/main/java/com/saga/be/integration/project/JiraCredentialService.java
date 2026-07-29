package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.JiraBoardRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraCredentialService {

    private final IntegrationSecretCipher cipher;
    private final JiraProviderClient jiraClient;
    private final JiraBoardRepository boardRepository;

    public JiraCredentialService(
            IntegrationSecretCipher cipher,
            JiraProviderClient jiraClient,
            JiraBoardRepository boardRepository
    ) {
        this.cipher = cipher;
        this.jiraClient = jiraClient;
        this.boardRepository = boardRepository;
    }

    @Transactional
    public String validAccessToken(JiraBoard board) {
        String purpose = purpose(board, "access");
        if (
            board.getTokenExpiresAt() != null
            && board.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))
        ) {
            return cipher.decrypt(board.getEncryptedAccessToken(), purpose);
        }
        if (board.getEncryptedRefreshToken() == null) {
            throw IntegrationException.conflict(
                    "JIRA_REFRESH_TOKEN_MISSING",
                    "Jira access has expired and must be reconnected"
            );
        }
        String refreshToken = cipher.decrypt(
                board.getEncryptedRefreshToken(),
                purpose(board, "refresh")
        );
        JiraOAuthToken refreshed = jiraClient.refresh(refreshToken);
        board.setEncryptedAccessToken(cipher.encrypt(
                refreshed.accessToken(),
                purpose
        ));
        if (refreshed.refreshToken() != null) {
            board.setEncryptedRefreshToken(cipher.encrypt(
                    refreshed.refreshToken(),
                    purpose(board, "refresh")
            ));
        }
        board.setTokenExpiresAt(LocalDateTime.ofInstant(
                refreshed.expiresAt(),
                ZoneOffset.UTC
        ));
        board.setGrantedScopes(String.join(" ", refreshed.scopes()));
        boardRepository.saveAndFlush(board);
        return refreshed.accessToken();
    }

    public String encryptAccess(JiraBoard board, String token) {
        return cipher.encrypt(token, purpose(board, "access"));
    }

    public String encryptRefresh(JiraBoard board, String token) {
        return cipher.encrypt(token, purpose(board, "refresh"));
    }

    private String purpose(JiraBoard board, String kind) {
        if (board.getId() == null) {
            throw new IllegalStateException(
                    "JiraBoard must be persisted before token encryption"
            );
        }
        return "jira-board:" + board.getId() + ":" + kind;
    }
}
