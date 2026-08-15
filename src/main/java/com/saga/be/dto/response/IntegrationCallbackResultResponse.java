package com.saga.be.dto.response;

import com.saga.be.entity.enums.IntegrationProvider;
import java.util.UUID;

/**
 * Safe, short-lived result of an OAuth callback. Provider tokens, OAuth state,
 * and provider payloads are deliberately not represented here.
 */
public record IntegrationCallbackResultResponse(
        IntegrationProvider provider,
        IntegrationCallbackFlow flow,
        UUID projectId,
        boolean success,
        IdentityConnectionResponse identityConnection,
        JiraAuthorizationResponse jiraAuthorization,
        GitHubInstallationResponse gitHubInstallation,
        String errorCode,
        String message
) {

    public static IntegrationCallbackResultResponse personalSuccess(
            IdentityConnectionResponse response
    ) {
        return new IntegrationCallbackResultResponse(
                response.provider(),
                IntegrationCallbackFlow.PERSONAL,
                null,
                true,
                response,
                null,
                null,
                null,
                null
        );
    }

    public static IntegrationCallbackResultResponse projectJiraSuccess(
            JiraAuthorizationResponse response
    ) {
        return new IntegrationCallbackResultResponse(
                IntegrationProvider.JIRA,
                IntegrationCallbackFlow.PROJECT,
                response.projectId(),
                true,
                null,
                response,
                null,
                null,
                null
        );
    }

    public static IntegrationCallbackResultResponse projectGitHubSuccess(
            GitHubInstallationResponse response
    ) {
        return new IntegrationCallbackResultResponse(
                IntegrationProvider.GITHUB,
                IntegrationCallbackFlow.PROJECT,
                response.projectId(),
                true,
                null,
                null,
                response,
                null,
                null
        );
    }

    public static IntegrationCallbackResultResponse failure(
            IntegrationProvider provider,
            IntegrationCallbackFlow flow,
            UUID projectId,
            String errorCode,
            String message
    ) {
        return new IntegrationCallbackResultResponse(
                provider,
                flow,
                projectId,
                false,
                null,
                null,
                null,
                errorCode,
                message
        );
    }
}
