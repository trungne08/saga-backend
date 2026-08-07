package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraAccessibleResource;
import com.saga.be.integration.provider.JiraProjectInfo;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWebhookRegistration;
import com.saga.be.integration.provider.JiraWriteScope;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.dao.DataIntegrityViolationException;

class ProjectIntegrationServiceJiraLinkTest {

    @Test
    void linksAccessibleProjectByNumericIdAndPersistsProviderCanonicalValues() {
        Fixture fixture = fixture();

        fixture.service.linkJira(
                fixture.principal,
                fixture.projectId,
                fixture.session,
                new JiraProjectLinkRequest("cloud-a", " 10034 "),
                "127.0.0.1"
        );

        assertCanonicalBoard(fixture);
    }

    @Test
    void linksAccessibleProjectByCaseInsensitiveKeyAndPersistsProviderCanonicalValues() {
        Fixture fixture = fixture();

        fixture.service.linkJira(
                fixture.principal,
                fixture.projectId,
                fixture.session,
                new JiraProjectLinkRequest("cloud-a", " saga "),
                "127.0.0.1"
        );

        assertCanonicalBoard(fixture);
    }

    @Test
    void rejectsUnknownProjectKeyWithControlledConflict() {
        Fixture fixture = fixture();

        assertNotAccessible(fixture, "NOT-A-PROJECT");
    }

    @Test
    void rejectsUnknownProjectIdWithControlledConflict() {
        Fixture fixture = fixture();

        assertNotAccessible(fixture, "99999");
    }

    @Test
    void rejectsAProjectKeyWhenTheSelectedCloudWasNotGranted() {
        Fixture fixture = fixture();

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.linkJira(
                        fixture.principal,
                        fixture.projectId,
                        fixture.session,
                        new JiraProjectLinkRequest("cloud-b", "SAGA"),
                        "127.0.0.1"
                )
        );

        assertEquals("JIRA_SITE_NOT_AUTHORIZED", exception.getCode());
        verify(fixture.jira, never()).projects(any(), any());
    }

    @Test
    void rejectsMissingAgileBoardScopeBeforeCallingTheProvider() {
        Fixture fixture = fixture(Set.of("read:jira-work", "manage:jira-webhook"));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.linkJira(
                        fixture.principal,
                        fixture.projectId,
                        fixture.session,
                        new JiraProjectLinkRequest("cloud-a", "SAGA"),
                        "127.0.0.1"
                )
        );

        assertEquals("JIRA_SCOPE_INSUFFICIENT", exception.getCode());
        verify(fixture.jira, never()).projects(any(), any());
    }

    @Test
    void rejectsMissingBoardFeaturesScopeBeforeCallingTheProvider() {
        Fixture fixture = fixture(Set.of(
                "read:jira-work",
                "manage:jira-webhook",
                "read:board-scope:jira-software",
                "read:project:jira"
        ));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.linkJira(
                        fixture.principal,
                        fixture.projectId,
                        fixture.session,
                        new JiraProjectLinkRequest("cloud-a", "SAGA"),
                        "127.0.0.1"
                )
        );

        assertEquals("JIRA_SCOPE_INSUFFICIENT", exception.getCode());
        verify(fixture.jira, never()).projects(any(), any());
    }

    @Test
    void rejectsMissingReadSprintScopeBeforeCallingTheProvider() {
        Fixture fixture = fixture(Set.of(
                "read:jira-work",
                "manage:jira-webhook",
                "read:board-scope:jira-software",
                "read:board-scope.admin:jira-software",
                "read:project:jira"
        ));

        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.linkJira(
                        fixture.principal,
                        fixture.projectId,
                        fixture.session,
                        new JiraProjectLinkRequest("cloud-a", "SAGA"),
                        "127.0.0.1"
                )
        );

        assertEquals("JIRA_SCOPE_INSUFFICIENT", exception.getCode());
        verify(fixture.jira, never()).projects(any(), any());
    }

    @Test
    void relinkUpdatesTheRetainedDisconnectedRowUsingTheFreshSessionGrant() {
        Fixture fixture = fixture();
        JiraBoard retained = new JiraBoard();
        UUID retainedId = UUID.randomUUID();
        retained.setId(retainedId);
        retained.setConnectionStatus(com.saga.be.entity.enums.IntegrationStatus.DISCONNECTED);
        retained.setEncryptedAccessToken("old-ciphertext");
        retained.setEncryptedRefreshToken("old-refresh-ciphertext");
        when(fixture.boardRepository.findForLinkByProjectId(fixture.projectId))
                .thenReturn(Optional.of(retained));

        fixture.service.linkJira(
                fixture.principal,
                fixture.projectId,
                fixture.session,
                new JiraProjectLinkRequest("cloud-a", "10034"),
                "127.0.0.1"
        );

        assertEquals(retainedId, retained.getId());
        assertEquals("encrypted", retained.getEncryptedAccessToken());
        assertEquals("encrypted", retained.getEncryptedRefreshToken());
        verify(fixture.credentialService).encryptAccess(
                retained, "test-access-token"
        );
        verify(fixture.credentialService).encryptRefresh(
                retained, "test-refresh-token"
        );
        verify(fixture.jira).projects("test-access-token", "cloud-a");
        verify(fixture.jira).ensureWebhook(
                eq("test-access-token"), eq("cloud-a"), eq("SAGA"), any(), any()
        );
    }

    @Test
    void linksWhenWriteOnlyRuntimeScopesAreMissing() {
        Fixture fixture = fixture(JiraWriteScope.linkScopes());

        fixture.service.linkJira(
                fixture.principal,
                fixture.projectId,
                fixture.session,
                new JiraProjectLinkRequest("cloud-a", "SAGA"),
                "127.0.0.1"
        );

        verify(fixture.jira).projects("test-access-token", "cloud-a");
        verify(fixture.jira).ensureWebhook(
                eq("test-access-token"), eq("cloud-a"), eq("SAGA"), any(), any()
        );
    }

    @Test
    void dataIntegrityRaceIsRetriedWithoutLeakingDatabaseDetails() {
        JiraBoardLinkPersistenceService persistence = org.mockito.Mockito.mock(
                JiraBoardLinkPersistenceService.class
        );
        Fixture fixture = fixture(JiraWriteScope.projectIntegrationScopes(), persistence);
        JiraBoard board = new JiraBoard();
        board.setId(UUID.randomUUID());
        Project project = new Project();
        project.setId(fixture.projectId());
        board.setProject(project);
        board.setCloudId("cloud-a");
        board.setJiraProjectId("10034");
        board.setConnectionStatus(com.saga.be.entity.enums.IntegrationStatus.BACKFILLING);
        when(persistence.upsert(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenReturn(board);
        when(persistence.complete(any(), eq("webhook-1"), eq(true), any()))
                .thenReturn(board);
        when(fixture.boardRepository.findByProjectId(fixture.projectId))
                .thenReturn(Optional.of(board));

        fixture.service.linkJira(
                fixture.principal,
                fixture.projectId,
                fixture.session,
                new JiraProjectLinkRequest("cloud-a", "10034"),
                "127.0.0.1"
        );

        verify(persistence, times(2)).upsert(any());
        verify(persistence).complete(any(), eq("webhook-1"), eq(true), any());
    }

    private void assertNotAccessible(Fixture fixture, String input) {
        IntegrationException exception = assertThrows(
                IntegrationException.class,
                () -> fixture.service.linkJira(
                        fixture.principal,
                        fixture.projectId,
                        fixture.session,
                        new JiraProjectLinkRequest("cloud-a", input),
                        "127.0.0.1"
                )
        );

        assertEquals("JIRA_PROJECT_NOT_ACCESSIBLE", exception.getCode());
    }

    private void assertCanonicalBoard(Fixture fixture) {
        ArgumentCaptor<JiraBoard> boards = ArgumentCaptor.forClass(JiraBoard.class);
        verify(fixture.boardRepository, org.mockito.Mockito.atLeastOnce())
                .saveAndFlush(boards.capture());
        JiraBoard board = boards.getValue();
        assertEquals("10034", board.getJiraProjectId());
        assertEquals("SAGA", board.getProjectKey());
        assertEquals("99", board.getJiraBoardId());
        verify(fixture.jira).ensureWebhook(
                eq("test-access-token"),
                eq("cloud-a"),
                eq("SAGA"),
                any(),
                any()
        );
    }

    private Fixture fixture() {
        return fixture(JiraWriteScope.projectIntegrationScopes());
    }

    private Fixture fixture(Set<String> resourceScopes) {
        return fixture(resourceScopes, null);
    }

    private Fixture fixture(
            Set<String> resourceScopes,
            JiraBoardLinkPersistenceService persistenceOverride
    ) {
        UUID projectId = UUID.randomUUID();
        Project project = new Project();
        project.setId(projectId);
        SagaPrincipal principal = new SagaPrincipal(
                "admin-sub", "admin@example.test", "Admin",
                ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        MockHttpSession session = new MockHttpSession();
        ProjectIntegrationAuthorizationService authorization = org.mockito.Mockito.mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectIntegrationSessionStore sessionStore = org.mockito.Mockito.mock(
                ProjectIntegrationSessionStore.class
        );
        JiraProviderClient jira = org.mockito.Mockito.mock(JiraProviderClient.class);
        JiraBoardRepository boardRepository = org.mockito.Mockito.mock(
                JiraBoardRepository.class
        );
        GitRepoRepository gitRepoRepository = org.mockito.Mockito.mock(
                GitRepoRepository.class
        );
        JiraCredentialService credentialService = org.mockito.Mockito.mock(
                JiraCredentialService.class
        );
        JiraBoardResolutionService boardResolver = org.mockito.Mockito.mock(
                JiraBoardResolutionService.class
        );
        IntegrationUrlResolver urls = org.mockito.Mockito.mock(
                IntegrationUrlResolver.class
        );
        AtomicReference<JiraBoard> persistedBoard = new AtomicReference<>();

        when(authorization.requireProjectManager(principal, projectId))
                .thenReturn(project);
        when(sessionStore.requireJiraGrant(session, projectId)).thenReturn(
                new ProjectIntegrationSessionStore.ResolvedJiraGrant(
                        "test-access-token",
                        "test-refresh-token",
                        Instant.now().plusSeconds(3600),
                        Set.of("read:jira-work", "manage:jira-webhook"),
                        List.of(new JiraAccessibleResource(
                                "cloud-a", "SAGA site", "https://site.example", resourceScopes
                        ))
                )
        );
        when(jira.projects("test-access-token", "cloud-a")).thenReturn(
                List.of(new JiraProjectInfo("10034", "SAGA", "SAGA Project"))
        );
        when(boardRepository.findForLinkByProjectId(projectId)).thenAnswer(
                invocation -> Optional.ofNullable(persistedBoard.get())
        );
        when(boardRepository.findByProjectId(projectId)).thenAnswer(
                invocation -> Optional.ofNullable(persistedBoard.get())
        );
        when(boardRepository.saveAndFlush(any(JiraBoard.class))).thenAnswer(invocation -> {
            JiraBoard saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            persistedBoard.set(saved);
            return saved;
        });
        when(credentialService.encryptAccess(any(), any())).thenReturn("encrypted");
        when(credentialService.encryptRefresh(any(), any())).thenReturn("encrypted");
        when(boardResolver.resolveForLinking(any(JiraBoard.class), eq("test-access-token")))
                .thenAnswer(invocation -> {
                    ((JiraBoard) invocation.getArgument(0)).setJiraBoardId("99");
                    return "99";
                });
        when(urls.jiraWebhookPublicUrl()).thenReturn(
                "https://saga.example/api/webhooks/jira"
        );
        when(jira.ensureWebhook(any(), any(), any(), any(), any()))
                .thenReturn(new JiraWebhookRegistration("webhook-1", true));
        when(gitRepoRepository.findByProjectIdOrderByFullName(projectId))
                .thenReturn(List.of());

        ProjectIntegrationService service = new ProjectIntegrationService(
                authorization,
                org.mockito.Mockito.mock(OAuthStateService.class),
                sessionStore,
                jira,
                org.mockito.Mockito.mock(GitHubProviderClient.class),
                org.mockito.Mockito.mock(IntegrationAvailability.class),
                new JiraIntegrationProperties(
                        true, "id", "secret", "", "", "", "", "",
                        "read:jira-work manage:jira-webhook"
                ),
                new GitHubIntegrationProperties(
                        true, "", "", "", "", "", "", "", "", "", "", "", ""
                ),
                urls,
                org.mockito.Mockito.mock(IntegrationSecretCipher.class),
                credentialService,
                boardResolver,
                persistenceOverride == null
                        ? new JiraBoardLinkPersistenceService(boardRepository, credentialService)
                        : persistenceOverride,
                boardRepository,
                org.mockito.Mockito.mock(GitHubInstallationRepository.class),
                gitRepoRepository,
                org.mockito.Mockito.mock(SyncJobLogRepository.class),
                org.mockito.Mockito.mock(StudentRepository.class),
                org.mockito.Mockito.mock(AutomaticSyncDispatcher.class),
                event -> { },
                org.mockito.Mockito.mock(IntegrationAttemptLimiter.class),
                org.mockito.Mockito.mock(AuthenticationAuditService.class)
        );
        return new Fixture(
                service, principal, projectId, session, jira, boardRepository,
                credentialService
        );
    }

    private record Fixture(
            ProjectIntegrationService service,
            SagaPrincipal principal,
            UUID projectId,
            MockHttpSession session,
            JiraProviderClient jira,
            JiraBoardRepository boardRepository,
            JiraCredentialService credentialService
    ) {
    }
}
