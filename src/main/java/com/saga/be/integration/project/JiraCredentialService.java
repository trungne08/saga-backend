package com.saga.be.integration.project;

import com.saga.be.entity.JiraBoard;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.JiraOAuthToken;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWriteScope;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.JiraBoardRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraCredentialService {

    private static final Logger log = LoggerFactory.getLogger(JiraCredentialService.class);

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String validAccessToken(JiraBoard board) {
        JiraBoard current = lockedBoard(board);
        String purpose = purpose(current, "access");
        boolean tokenExpired = current.getTokenExpiresAt() == null
                || !current.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1));
        if (!tokenExpired) {
            log.debug("Jira credential resolved: jiraBoardEntityId={}, projectId={}, tokenExpired=false, "
                            + "refreshAttempted=false, refreshResult=NOT_NEEDED, credentialVersion={}",
                    current.getId(), projectId(current), current.getVersion());
            return cipher.decrypt(current.getEncryptedAccessToken(), purpose);
        }
        if (current.getEncryptedRefreshToken() == null) {
            log.warn("Jira credential resolution failed: jiraBoardEntityId={}, projectId={}, tokenExpired=true, "
                            + "refreshAttempted=false, refreshResult=FAILED, credentialVersion={}, errorCategory=JIRA_REFRESH_TOKEN_MISSING",
                    current.getId(), projectId(current), current.getVersion());
            throw IntegrationException.conflict(
                    "JIRA_REFRESH_TOKEN_MISSING",
                    "Jira access has expired and must be reconnected"
            );
        }
        try {
            String refreshToken = cipher.decrypt(
                    current.getEncryptedRefreshToken(),
                    purpose(current, "refresh")
            );
            JiraOAuthToken refreshed = jiraClient.refresh(refreshToken);
            current.setEncryptedAccessToken(cipher.encrypt(refreshed.accessToken(), purpose));
            if (refreshed.refreshToken() != null) {
                current.setEncryptedRefreshToken(cipher.encrypt(
                        refreshed.refreshToken(),
                        purpose(current, "refresh")
                ));
            }
            current.setTokenExpiresAt(LocalDateTime.ofInstant(refreshed.expiresAt(), ZoneOffset.UTC));
            current.setGrantedScopes(String.join(" ", refreshed.scopes()));
            boardRepository.saveAndFlush(current);
            log.debug("Jira credential resolved: jiraBoardEntityId={}, projectId={}, tokenExpired=true, "
                            + "refreshAttempted=true, refreshResult=SUCCESS, credentialVersion={}",
                    current.getId(), projectId(current), current.getVersion());
            return refreshed.accessToken();
        } catch (RuntimeException exception) {
            log.warn("Jira credential resolution failed: jiraBoardEntityId={}, projectId={}, tokenExpired=true, "
                            + "refreshAttempted=true, refreshResult=FAILED, credentialVersion={}, errorCategory={}",
                    current.getId(), projectId(current), current.getVersion(), errorCategory(exception));
            throw exception;
        }
    }

    private JiraBoard lockedBoard(JiraBoard board) {
        if (board == null || board.getId() == null) {
            throw new IllegalStateException("JiraBoard must be persisted before credential resolution");
        }
        // The fallback preserves isolated unit tests; production always reloads the locked row.
        return boardRepository.findForCredentialRefreshById(board.getId()).orElse(board);
    }

    /** Rechecks persisted scopes after resolving a possibly rotated credential. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requireCurrentScopes(JiraBoard board, String... requiredScopes) {
        JiraWriteScope.requireGranted(lockedBoard(board), requiredScopes);
    }

    private java.util.UUID projectId(JiraBoard board) {
        return board.getProject() == null ? null : board.getProject().getId();
    }

    private String errorCategory(RuntimeException exception) {
        return exception instanceof IntegrationException integrationException
                ? integrationException.getCode()
                : "JIRA_CREDENTIAL_REFRESH_UNKNOWN";
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
