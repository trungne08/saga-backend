package com.saga.be.integration.provider;

import com.saga.be.exception.IntegrationException;
import com.saga.be.entity.JiraBoard;
import java.util.Arrays;

/** Validates the persisted classic Jira scopes without inspecting a token. */
public final class JiraWriteScope {

    public static final String CLASSIC_WRITE_SCOPE = "write:jira-work";
    public static final String READ_SPRINT_SCOPE = "read:sprint:jira-software";
    public static final String WRITE_SPRINT_SCOPE = "write:sprint:jira-software";
    public static final String DELETE_SPRINT_SCOPE = "delete:sprint:jira-software";
    public static final String WRITE_BOARD_SCOPE = "write:board-scope:jira-software";
    public static final String READ_ISSUE_SOFTWARE_SCOPE = "read:issue:jira-software";
    public static final String READ_ISSUE_DETAILS_SCOPE = "read:issue-details:jira";
    public static final String WRITE_ISSUE_SOFTWARE_SCOPE = "write:issue:jira-software";

    private JiraWriteScope() {
    }

    public static void requireGranted(JiraBoard board) {
        requireGranted(board == null ? null : board.getGrantedScopes());
    }

    public static void requireGranted(String grantedScopes) {
        requireGranted(grantedScopes, CLASSIC_WRITE_SCOPE);
    }

    public static void requireGranted(JiraBoard board, String... requiredScopes) {
        requireGranted(board == null ? null : board.getGrantedScopes(), requiredScopes);
    }

    public static void requireGranted(String grantedScopes, String... requiredScopes) {
        var granted = grantedScopes == null ? java.util.Set.<String>of() : Arrays.stream(
                        grantedScopes.trim().split("\\s+")
                )
                .filter(scope -> !scope.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean missing = Arrays.stream(requiredScopes == null ? new String[0] : requiredScopes)
                .anyMatch(scope -> !granted.contains(scope));
        if (missing) {
            throw IntegrationException.conflict(
                    Arrays.equals(requiredScopes, new String[] {CLASSIC_WRITE_SCOPE})
                            ? "JIRA_WRITE_SCOPE_MISSING"
                            : "JIRA_REQUIRED_SCOPE_MISSING",
                    "Jira write permission is missing; reconnect Jira to grant it"
            );
        }
    }
}
