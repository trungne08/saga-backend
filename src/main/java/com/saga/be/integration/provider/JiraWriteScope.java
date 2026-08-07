package com.saga.be.integration.provider;

import com.saga.be.exception.IntegrationException;
import com.saga.be.entity.JiraBoard;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Shared validation of persisted and accessible-resource Jira scopes. */
public final class JiraWriteScope {

    public static final String CLASSIC_WRITE_SCOPE = "write:jira-work";
    public static final String READ_SPRINT_SCOPE = "read:sprint:jira-software";
    public static final String WRITE_SPRINT_SCOPE = "write:sprint:jira-software";
    public static final String DELETE_SPRINT_SCOPE = "delete:sprint:jira-software";
    public static final String WRITE_BOARD_SCOPE = "write:board-scope:jira-software";
    public static final String READ_ISSUE_SOFTWARE_SCOPE = "read:issue:jira-software";
    public static final String READ_ISSUE_DETAILS_SCOPE = "read:issue-details:jira";
    public static final String WRITE_ISSUE_SOFTWARE_SCOPE = "write:issue:jira-software";
    public static final String READ_JIRA_WORK_SCOPE = "read:jira-work";
    public static final String MANAGE_WEBHOOK_SCOPE = "manage:jira-webhook";
    public static final String READ_BOARD_SCOPE = "read:board-scope:jira-software";
    public static final String READ_BOARD_ADMIN_SCOPE = "read:board-scope.admin:jira-software";
    public static final String READ_PROJECT_SCOPE = "read:project:jira";
    public static final String OFFLINE_ACCESS_SCOPE = "offline_access";

    private static final Set<String> PROJECT_INTEGRATION_SCOPES = Set.of(
            READ_JIRA_WORK_SCOPE,
            CLASSIC_WRITE_SCOPE,
            MANAGE_WEBHOOK_SCOPE,
            READ_BOARD_SCOPE,
            READ_BOARD_ADMIN_SCOPE,
            READ_PROJECT_SCOPE,
            READ_SPRINT_SCOPE,
            WRITE_SPRINT_SCOPE,
            DELETE_SPRINT_SCOPE,
            WRITE_BOARD_SCOPE,
            WRITE_ISSUE_SOFTWARE_SCOPE
    );
    private static final Set<String> LINK_SCOPES = Set.of(
            READ_JIRA_WORK_SCOPE,
            MANAGE_WEBHOOK_SCOPE,
            READ_BOARD_SCOPE,
            READ_BOARD_ADMIN_SCOPE,
            READ_PROJECT_SCOPE
    );

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
        requireGranted(scopes(grantedScopes), requiredScopes);
    }

    public static void requireGranted(Set<String> grantedScopes, String... requiredScopes) {
        Set<String> missing = missing(grantedScopes, requiredScopes);
        if (!missing.isEmpty()) {
            throw IntegrationException.conflict(
                    "JIRA_SCOPE_INSUFFICIENT",
                    "Jira authorization does not include the permissions required by this integration"
            );
        }
    }

    public static Set<String> projectIntegrationScopes() {
        return PROJECT_INTEGRATION_SCOPES;
    }

    /** Scopes used by the provider calls performed by POST /jira/link itself. */
    public static Set<String> linkScopes() {
        return LINK_SCOPES;
    }

    public static Set<String> missing(Set<String> grantedScopes, String... requiredScopes) {
        Set<String> granted = grantedScopes == null ? Set.of() : grantedScopes;
        Set<String> missing = new LinkedHashSet<>();
        for (String scope : requiredScopes == null ? new String[0] : requiredScopes) {
            if (scope != null && !scope.isBlank() && !granted.contains(scope)) {
                missing.add(scope);
            }
        }
        return Set.copyOf(missing);
    }

    public static Set<String> scopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopes.trim().split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
