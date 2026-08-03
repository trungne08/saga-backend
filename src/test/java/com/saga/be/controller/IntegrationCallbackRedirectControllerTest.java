package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationCallbackProperties;
import com.saga.be.dto.response.IntegrationCallbackFlow;
import com.saga.be.dto.response.IntegrationCallbackResultResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.integration.callback.IntegrationCallbackRedirectService;
import com.saga.be.integration.callback.IntegrationCallbackResultStore;
import com.saga.be.integration.callback.JiraOAuthCallbackService;
import com.saga.be.integration.identity.PersonalIntegrationService;
import com.saga.be.integration.project.ProjectIntegrationService;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

class IntegrationCallbackRedirectControllerTest {

    private final IntegrationCallbackResultStore store =
            new IntegrationCallbackResultStore(
                    new IntegrationCallbackProperties(
                            "https://frontend.example/integrations/callback",
                            Duration.ofMinutes(5)
                    )
            );
    private final IntegrationCallbackRedirectService redirects =
            new IntegrationCallbackRedirectService(
                    new IntegrationCallbackProperties(
                            "https://frontend.example/integrations/callback",
                            Duration.ofMinutes(5)
                    )
            );

    @Test
    void allCompletionCallbackRoutesRedirectWithOnlyResultId() {
        SagaPrincipal principal = principal();
        MockHttpSession session = new MockHttpSession();
        IntegrationAvailability availability = mock(IntegrationAvailability.class);
        IntegrationCallbackResultResponse result = failure();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        JiraOAuthCallbackService jira = mock(JiraOAuthCallbackService.class);
        when(jira.complete(principal, session, "state", "code", null, "127.0.0.1"))
                .thenReturn(result);
        assertRedirect(new JiraIntegrationCallbackController(
                jira, availability, store, redirects
        ).callback(principal, session, "state", "code", null, request));

        PersonalIntegrationService personal = mock(PersonalIntegrationService.class);
        when(personal.finishGitHubCallback(
                principal, session, "state", "code", null, "127.0.0.1"
        )).thenReturn(result);
        assertRedirect(new PersonalIntegrationController(
                personal, availability, store, redirects
        ).githubCallback(principal, session, "state", "code", null, request));

        ProjectIntegrationService project = mock(ProjectIntegrationService.class);
        UUID projectId = UUID.randomUUID();
        when(project.finishGitHubInstallationCallback(
                principal, projectId, session, "state", "code", null
        )).thenReturn(result);
        assertRedirect(new ProjectIntegrationController(
                project, availability, store, redirects
        ).githubCallback(principal, projectId, session, "state", "code", null));

        when(project.finishGitHubInstallationFromProviderCallback(
                principal, session, "state", "code", null
        )).thenReturn(result);
        assertRedirect(new ProjectIntegrationCallbackController(
                project, availability, store, redirects
        ).githubCallback(principal, session, "state", "code", null));
    }

    private void assertRedirect(ResponseEntity<Void> response) {
        assertEquals(302, response.getStatusCode().value());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.startsWith(
                "https://frontend.example/integrations/callback?resultId="
        ));
        assertEquals(1, location.split("\\?", -1).length - 1);
    }

    private IntegrationCallbackResultResponse failure() {
        return IntegrationCallbackResultResponse.failure(
                IntegrationProvider.GITHUB,
                IntegrationCallbackFlow.PERSONAL,
                null,
                "OAUTH_CONSENT_DENIED",
                "Provider authorization was denied or cancelled"
        );
    }

    private SagaPrincipal principal() {
        return new SagaPrincipal(
                "student-sub",
                "student@example.test",
                "Student",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
    }
}
