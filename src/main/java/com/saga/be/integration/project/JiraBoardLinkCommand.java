package com.saga.be.integration.project;

import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import java.time.Instant;
import java.util.Set;

/** Safe, already-verified values needed for the local Jira link upsert. */
record JiraBoardLinkCommand(
        Project project,
        String name,
        String cloudId,
        String siteUrl,
        String jiraProjectId,
        String projectKey,
        String jiraBoardId,
        String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        Set<String> grantedScopes,
        String connectedByCognitoSub,
        Student connectedByStudent
) {
}
