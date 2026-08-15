package com.saga.be.integration.callback;

import com.saga.be.exception.IntegrationException;
import com.saga.be.dto.response.IntegrationCallbackFlow;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.integration.identity.PersonalIntegrationService;
import com.saga.be.integration.project.ProjectIntegrationService;
import com.saga.be.integration.security.OAuthFlow;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class JiraOAuthCallbackService {

    private final OAuthStateService stateService;
    private final PersonalIntegrationService personalIntegrationService;
    private final ProjectIntegrationService projectIntegrationService;

    public JiraOAuthCallbackService(
            OAuthStateService stateService,
            PersonalIntegrationService personalIntegrationService,
            ProjectIntegrationService projectIntegrationService
    ) {
        this.stateService = stateService;
        this.personalIntegrationService = personalIntegrationService;
        this.projectIntegrationService = projectIntegrationService;
    }

    public IntegrationCallbackResultResponse complete(
            SagaPrincipal principal,
            HttpSession session,
            String state,
            String code,
            String oauthError,
            String remoteAddress
    ) {
        OAuthStateService.StateBinding binding = stateService.consumeAndResolve(
                session,
                principal,
                state
        );
        if (
            binding.flow() == OAuthFlow.PERSONAL_JIRA
            && binding.targetId() == null
        ) {
            try {
                return IntegrationCallbackResultResponse.personalSuccess(
                        personalIntegrationService.completeJiraCallback(
                                principal,
                                code,
                                oauthError,
                                remoteAddress
                        )
                );
            } catch (IntegrationException exception) {
                return failure(
                        IntegrationCallbackFlow.PERSONAL,
                        null,
                        exception
                );
            }
        }
        if (
            binding.flow() == OAuthFlow.PROJECT_JIRA
            && binding.targetId() != null
        ) {
            try {
                return IntegrationCallbackResultResponse.projectJiraSuccess(
                        projectIntegrationService.completeJiraCallback(
                                principal,
                                binding.targetId(),
                                session,
                                code,
                                oauthError
                        )
                );
            } catch (IntegrationException exception) {
                return failure(
                        IntegrationCallbackFlow.PROJECT,
                        binding.targetId(),
                        exception
                );
            }
        }
        throw IntegrationException.invalid(
                "OAUTH_STATE_INVALID",
                "The integration authorization state is invalid or expired"
        );
    }

    private IntegrationCallbackResultResponse failure(
            IntegrationCallbackFlow flow,
            java.util.UUID projectId,
            IntegrationException exception
    ) {
        return IntegrationCallbackResultResponse.failure(
                com.saga.be.entity.enums.IntegrationProvider.JIRA,
                flow,
                projectId,
                exception.getCode(),
                exception.getMessage()
        );
    }
}
