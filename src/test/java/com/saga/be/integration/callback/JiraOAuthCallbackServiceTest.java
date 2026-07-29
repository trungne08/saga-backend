package com.saga.be.integration.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.identity.PersonalIntegrationService;
import com.saga.be.integration.project.ProjectIntegrationService;
import com.saga.be.integration.security.OAuthFlow;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class JiraOAuthCallbackServiceTest {

    private OAuthStateService stateService;
    private PersonalIntegrationService personalService;
    private ProjectIntegrationService projectService;
    private JiraOAuthCallbackService callbackService;
    private SagaPrincipal principal;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        stateService = mock(OAuthStateService.class);
        personalService = mock(PersonalIntegrationService.class);
        projectService = mock(ProjectIntegrationService.class);
        callbackService = new JiraOAuthCallbackService(
                stateService,
                personalService,
                projectService
        );
        principal = new SagaPrincipal(
                "student-sub",
                "student@example.com",
                "Student",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        session = new MockHttpSession();
    }

    @Test
    void dispatchesPersonalFlowOnlyFromConsumedServerState() {
        when(stateService.consumeAndResolve(session, principal, "state"))
                .thenReturn(binding(OAuthFlow.PERSONAL_JIRA, null));

        callbackService.complete(
                principal,
                session,
                "state",
                "code",
                null,
                "127.0.0.1"
        );

        verify(personalService).completeJiraCallback(
                principal,
                "code",
                null,
                "127.0.0.1"
        );
        verify(projectService, never()).completeJiraCallback(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void dispatchesProjectAndTargetOnlyFromConsumedServerState() {
        UUID projectId = UUID.randomUUID();
        when(stateService.consumeAndResolve(session, principal, "state"))
                .thenReturn(binding(OAuthFlow.PROJECT_JIRA, projectId));

        callbackService.complete(
                principal,
                session,
                "state",
                "code",
                null,
                "127.0.0.1"
        );

        verify(projectService).completeJiraCallback(
                principal,
                projectId,
                session,
                "code",
                null
        );
        verify(personalService, never()).completeJiraCallback(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsMalformedFlowBindingWithoutCallingEitherProviderFlow() {
        when(stateService.consumeAndResolve(session, principal, "state"))
                .thenReturn(binding(
                        OAuthFlow.PERSONAL_JIRA,
                        UUID.randomUUID()
                ));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> callbackService.complete(
                        principal,
                        session,
                        "state",
                        "code",
                        null,
                        "127.0.0.1"
                )
        );

        assertEquals("OAUTH_STATE_INVALID", exception.getCode());
        verify(personalService, never()).completeJiraCallback(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(projectService, never()).completeJiraCallback(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private OAuthStateService.StateBinding binding(
            OAuthFlow flow,
            UUID targetId
    ) {
        return new OAuthStateService.StateBinding(
                principal.cognitoSub(),
                principal.localProfileId(),
                flow,
                targetId,
                Instant.now().plusSeconds(600)
        );
    }
}
