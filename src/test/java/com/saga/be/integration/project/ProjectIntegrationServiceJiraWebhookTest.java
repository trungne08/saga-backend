package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.GitHubIntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationUrlResolver;
import com.saga.be.config.JiraIntegrationProperties;
import com.saga.be.dto.request.JiraProjectLinkRequest;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraProjectInfo;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWebhookRegistration;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ProjectIntegrationServiceJiraWebhookTest {

    @Test
    void compensatesProviderWebhookWhenTheBoardWriteFailsAfterCreation() {
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraProviderClient jira = mock(JiraProviderClient.class);
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectIntegrationSessionStore sessions = mock(
                ProjectIntegrationSessionStore.class
        );
        IntegrationSecretCipher cipher = mock(IntegrationSecretCipher.class);
        IntegrationUrlResolver urls = mock(IntegrationUrlResolver.class);
        when(authorization.requireProjectManager(any(), eq(projectId)))
                .thenReturn(project);
        when(sessions.requireJiraGrant(any(), eq(projectId))).thenReturn(
                new ProjectIntegrationSessionStore.ResolvedJiraGrant(
                        "ACCESS_TOKEN_SECRET", "REFRESH_TOKEN_SECRET",
                        Instant.now().plusSeconds(3600),
                        Set.of("read:jira-work", "manage:jira-webhook"),
                        List.of(new JiraAccessibleResource(
                                "cloud", "site", "https://site.test"
                        ))
                )
        );
        when(jira.projects("ACCESS_TOKEN_SECRET", "cloud")).thenReturn(
                List.of(new JiraProjectInfo("10034", "SDP", "Software"))
        );
        when(urls.jiraWebhookPublicUrl()).thenReturn(
                "https://tunnel.test/api/webhooks/jira"
        );
        when(cipher.encrypt(any(), any())).thenReturn("encrypted");
        when(jira.ensureWebhook(any(), any(), any(), any(), any()))
                .thenReturn(new JiraWebhookRegistration("99", true));
        when(boards.findByProjectId(projectId)).thenReturn(Optional.empty());
        AtomicInteger saves = new AtomicInteger();
        when(boards.saveAndFlush(any(JiraBoard.class))).thenAnswer(call -> {
            JiraBoard board = call.getArgument(0);
            if (saves.incrementAndGet() == 1) {
                board.setId(boardId);
                return board;
            }
            throw new IllegalStateException("simulated database rollback");
        });

        ProjectIntegrationService service = service(
                authorization, sessions, jira, urls, cipher, boards
        );

        assertThrows(IllegalStateException.class, () -> service.linkJira(
                admin(), projectId, new MockHttpSession(),
                new JiraProjectLinkRequest("cloud", "10034"), "127.0.0.1"
        ));

        verify(jira).deleteWebhook("ACCESS_TOKEN_SECRET", "cloud", "99");
    }

    private ProjectIntegrationService service(
            ProjectIntegrationAuthorizationService authorization,
            ProjectIntegrationSessionStore sessions,
            JiraProviderClient jira,
            IntegrationUrlResolver urls,
            IntegrationSecretCipher cipher,
            JiraBoardRepository boards
    ) {
        return new ProjectIntegrationService(
                authorization,
                mock(OAuthStateService.class),
                sessions,
                jira,
                mock(GitHubProviderClient.class),
                mock(IntegrationAvailability.class),
                new JiraIntegrationProperties(
                        true, "id", "secret", "", "", "", "", "",
                        "read:jira-work manage:jira-webhook"
                ),
                new GitHubIntegrationProperties(
                        true, "", "", "", "", "", "", "", "", "", "", "", ""
                ),
                urls,
                cipher,
                mock(JiraCredentialService.class),
                mock(JiraBoardResolutionService.class),
                boards,
                mock(GitHubInstallationRepository.class),
                mock(GitRepoRepository.class),
                mock(SyncJobLogRepository.class),
                mock(StudentRepository.class),
                mock(AutomaticSyncDispatcher.class),
                event -> { },
                mock(IntegrationAttemptLimiter.class),
                mock(AuthenticationAuditService.class)
        );
    }

    private SagaPrincipal admin() {
        return new SagaPrincipal(
                "admin", "admin@test", "Admin", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
