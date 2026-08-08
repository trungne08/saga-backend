package com.saga.be.integration.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraBoard;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.repository.JiraBoardRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JiraCredentialFoundationTest {

    @Test
    void usesAnActiveAccessTokenWithoutRefreshing() {
        IntegrationSecretCipher cipher = mock(IntegrationSecretCipher.class);
        JiraProviderClient client = mock(JiraProviderClient.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraBoard board = board();
        board.setTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(cipher.decrypt("encrypted-access", accessPurpose(board)))
                .thenReturn("ACCESS_TOKEN_SECRET");

        String accessToken = new JiraCredentialService(cipher, client, boards)
                .validAccessToken(board);

        assertEquals("ACCESS_TOKEN_SECRET", accessToken);
        verify(client, never()).refresh(any());
        verify(boards, never()).saveAndFlush(any());
    }

    @Test
    void refreshesExpiredCredentialsAndPersistsReturnedScopes() {
        IntegrationSecretCipher cipher = mock(IntegrationSecretCipher.class);
        JiraProviderClient client = mock(JiraProviderClient.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraBoard board = board();
        board.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        board.setEncryptedRefreshToken("encrypted-refresh");
        when(cipher.decrypt("encrypted-refresh", refreshPurpose(board)))
                .thenReturn("REFRESH_TOKEN_SECRET");
        when(client.refresh("REFRESH_TOKEN_SECRET")).thenReturn(new JiraOAuthToken(
                "NEW_ACCESS_TOKEN_SECRET",
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Set.of("read:jira-work", "write:jira-work")
        ));
        when(cipher.encrypt("NEW_ACCESS_TOKEN_SECRET", accessPurpose(board)))
                .thenReturn("new-encrypted-access");

        String accessToken = new JiraCredentialService(cipher, client, boards)
                .validAccessToken(board);

        assertEquals("NEW_ACCESS_TOKEN_SECRET", accessToken);
        assertEquals("new-encrypted-access", board.getEncryptedAccessToken());
        assertThat(board.getGrantedScopes()).contains("read:jira-work")
                .contains("write:jira-work");
        JiraWriteScope.requireGranted(board);
        verify(boards).saveAndFlush(board);
    }

    @Test
    void reloadsTheLockedCredentialRowBeforeDecryptingToAvoidStaleCallerToken() {
        IntegrationSecretCipher cipher = mock(IntegrationSecretCipher.class);
        JiraProviderClient client = mock(JiraProviderClient.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraBoard staleCallerBoard = board();
        staleCallerBoard.setEncryptedAccessToken("stale-ciphertext");
        staleCallerBoard.setTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        JiraBoard refreshedBoard = board();
        refreshedBoard.setEncryptedAccessToken("fresh-ciphertext");
        refreshedBoard.setTokenExpiresAt(LocalDateTime.now().plusMinutes(5));
        refreshedBoard.setGrantedScopes("write:sprint:jira-software");
        when(boards.findForCredentialRefreshById(staleCallerBoard.getId())).thenReturn(Optional.of(refreshedBoard));
        when(cipher.decrypt("fresh-ciphertext", accessPurpose(refreshedBoard))).thenReturn("FRESH_ACCESS_TOKEN");

        String accessToken = new JiraCredentialService(cipher, client, boards)
                .validAccessToken(staleCallerBoard);

        assertEquals("FRESH_ACCESS_TOKEN", accessToken);
        verify(cipher, never()).decrypt("stale-ciphertext", accessPurpose(staleCallerBoard));
        verify(client, never()).refresh(any());
    }

    private JiraBoard board() {
        JiraBoard board = new JiraBoard();
        board.setId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        board.setEncryptedAccessToken("encrypted-access");
        return board;
    }

    private String accessPurpose(JiraBoard board) {
        return "jira-board:" + board.getId() + ":access";
    }

    private String refreshPurpose(JiraBoard board) {
        return "jira-board:" + board.getId() + ":refresh";
    }
}
