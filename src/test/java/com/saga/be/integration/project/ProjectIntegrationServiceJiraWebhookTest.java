package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.saga.be.integration.provider.JiraWriteScope;
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
import com.saga.be.service.ProjectIntegrationNotificationProducer;
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
    void disconnectRetainsBoardMetadataAndHistoryAnchorWhileRetiringCredentials() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        JiraBoard board = JiraBoard.builder()
                .project(project)
                .cloudId("cloud")
                .jiraProjectId("10034")
                .projectKey("SDP")
                .jiraBoardId("99")
                .encryptedAccessToken("old-ciphertext")
                .encryptedRefreshToken("old-refresh-ciphertext")
                .grantedScopes("read:jira-work")
                .connectionStatus(com.saga.be.entity.enums.IntegrationStatus.ACTIVE)
                .build();
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        when(authorization.requireProjectManager(any(), eq(projectId)))
                .thenReturn(project);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(boards.saveAndFlush(board)).thenReturn(board);
        ProjectIntegrationService service = service(
                authorization,
                mock(ProjectIntegrationSessionStore.class),
                mock(JiraProviderClient.class),
                mock(IntegrationUrlResolver.class),
                mock(IntegrationSecretCipher.class),
                boards
        );

        service.disconnectJira(admin(), projectId, "127.0.0.1");

        assertEquals(com.saga.be.entity.enums.IntegrationStatus.DISCONNECTED,
                board.getConnectionStatus());
        assertEquals("10034", board.getJiraProjectId());
        assertEquals("SDP", board.getProjectKey());
        assertEquals("99", board.getJiraBoardId());
        org.junit.jupiter.api.Assertions.assertNull(board.getEncryptedAccessToken());
        org.junit.jupiter.api.Assertions.assertNull(board.getEncryptedRefreshToken());
        org.junit.jupiter.api.Assertions.assertNull(board.getGrantedScopes());
        verify(boards).saveAndFlush(board);
    }

    @Test
    void doesNotRegisterProviderWebhookWhenTheLocalUpsertFails() {
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
                                "cloud", "site", "https://site.test",
                                JiraWriteScope.projectIntegrationScopes()
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

        verify(jira, org.mockito.Mockito.never()).ensureWebhook(
                any(), any(), any(), any(), any()
        );
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
                new JiraCredentialService(cipher, jira, boards),
                mock(JiraBoardResolutionService.class),
                new JiraBoardLinkPersistenceService(
                        boards,
                        new JiraCredentialService(cipher, jira, boards)
                ),
                boards,
                mock(GitHubInstallationRepository.class),
                mock(GitRepoRepository.class),
                mock(SyncJobLogRepository.class),
                mock(StudentRepository.class),
                mock(AutomaticSyncDispatcher.class),
                event -> { },
                mock(IntegrationAttemptLimiter.class),
                mock(AuthenticationAuditService.class),
                mock(ProjectIntegrationNotificationProducer.class)
        );
    }

    private SagaPrincipal admin() {
        return new SagaPrincipal(
                "admin", "admin@test", "Admin", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
