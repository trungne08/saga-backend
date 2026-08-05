package com.saga.be.integration.provider;

import com.saga.be.exception.IntegrationException;
import com.saga.be.entity.JiraBoard;
import java.util.Arrays;

/** Validates the persisted classic Jira scopes without inspecting a token. */
public final class JiraWriteScope {

    public static final String CLASSIC_WRITE_SCOPE = "write:jira-work";

    private JiraWriteScope() {
    }

    public static void requireGranted(JiraBoard board) {
        requireGranted(board == null ? null : board.getGrantedScopes());
    }

    public static void requireGranted(String grantedScopes) {
        boolean granted = grantedScopes != null && Arrays.stream(
                        grantedScopes.trim().split("\\s+")
                )
                .anyMatch(CLASSIC_WRITE_SCOPE::equals);
        if (!granted) {
            throw IntegrationException.conflict(
                    "JIRA_WRITE_SCOPE_MISSING",
                    "Jira write permission is missing; reconnect Jira to grant it"
            );
        }
    }
}
