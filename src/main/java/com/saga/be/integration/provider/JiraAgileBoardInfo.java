package com.saga.be.integration.provider;

/** Safe, provider-neutral representation of a Jira Agile board. */
public record JiraAgileBoardInfo(
        String boardId,
        String name,
        String type,
        String locationProjectId,
        String locationProjectKey
) {
    public JiraAgileBoardInfo(String boardId, String name, String type) {
        this(boardId, name, type, null, null);
    }

    public String projectAssociation() {
        if (locationProjectId != null && locationProjectKey != null) {
            return "projectId=" + locationProjectId + ",projectKey=" + locationProjectKey;
        }
        if (locationProjectId != null) {
            return "projectId=" + locationProjectId;
        }
        if (locationProjectKey != null) {
            return "projectKey=" + locationProjectKey;
        }
        return "NOT_REPORTED";
    }
}
