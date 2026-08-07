package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationUrlResolver;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.security.IntegrationAttemptLimiter;
import com.saga.be.integration.security.IntegrationSecretCipher;
import com.saga.be.integration.security.OAuthStateService;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SyncJobLogRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ProjectIntegrationServiceSecurityTest {

    @Test
    void spoofedInstallationIdIsRejectedWithoutPersistingConnection() {
        ProjectIntegrationSessionStore sessionStore =
                mock(ProjectIntegrationSessionStore.class);
        GitHubProviderClient gitHubClient = mock(GitHubProviderClient.class);
        GitHubInstallationRepository installationRepository =
                mock(GitHubInstallationRepository.class);
        ProjectIntegrationService service = new ProjectIntegrationService(
                mock(ProjectIntegrationAuthorizationService.class),
                mock(OAuthStateService.class),
                sessionStore,
                mock(JiraProviderClient.class),
                gitHubClient,
                mock(IntegrationAvailability.class),
                new JiraIntegrationProperties(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new GitHubIntegrationProperties(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "https://api.github.com",
                        "https://github.com",
                        "https://saga.example/api/me/integrations/github/callback",
                        "https://saga.example/api/integrations/github/project/callback",
                        "https://saga.example/api/webhooks/github"
                ),
                mock(IntegrationUrlResolver.class),
                mock(IntegrationSecretCipher.class),
                mock(JiraCredentialService.class),
                mock(JiraBoardResolutionService.class),
                mock(JiraBoardLinkPersistenceService.class),
                mock(JiraBoardRepository.class),
                installationRepository,
                mock(GitRepoRepository.class),
                mock(SyncJobLogRepository.class),
                mock(StudentRepository.class),
                mock(AutomaticSyncDispatcher.class),
                event -> { },
                mock(IntegrationAttemptLimiter.class),
                mock(AuthenticationAuditService.class)
        );
        UUID projectId = UUID.randomUUID();
        SagaPrincipal principal = new SagaPrincipal(
                "leader-sub",
                "leader@example.com",
                "Leader",
                ApplicationRole.STUDENT,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        MockHttpSession session = new MockHttpSession();
        when(sessionStore.requireGitHubInstallation(session, projectId))
                .thenReturn(98765L);
        when(gitHubClient.exchangeUserCode(
                "code",
                "https://saga.example/api/integrations/github/project/callback"
        )).thenReturn("short-lived-user-token");
        when(gitHubClient.userCanAccessInstallation(
                "short-lived-user-token",
                98765L
        )).thenReturn(false);

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> service.finishGitHubInstallation(
                        principal,
                        projectId,
                        session,
                        "state",
                        "code",
                        null
                )
        );

        assertEquals("INTEGRATION_FORBIDDEN", exception.getCode());
        verify(sessionStore).removeGitHubInstallation(session, projectId);
        verify(installationRepository, never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(
                        GitHubInstallation.class
                ));
    }
}
