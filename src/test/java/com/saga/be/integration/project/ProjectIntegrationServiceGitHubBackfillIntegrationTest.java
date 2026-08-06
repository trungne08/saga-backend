package com.saga.be.integration.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.dto.request.GitHubRepositoriesLinkRequest;
import com.saga.be.dto.response.SyncStatusResponse;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubRepositoryInfo;
import com.saga.be.integration.security.IntegrationAttemptLimiter;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.AutomaticSyncDispatcher;
import com.saga.be.integration.sync.GitHubInitialBackfillJobService;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SyncJobLogRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class ProjectIntegrationServiceGitHubBackfillIntegrationTest {

    @Autowired
    private ProjectIntegrationService service;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private GitHubInstallationRepository installationRepository;

    @Autowired
    private GitRepoRepository gitRepoRepository;

    @Autowired
    private SyncJobLogRepository syncJobLogRepository;

    @Autowired
    private GitHubInitialBackfillJobService initialBackfillJobService;

    @Autowired
    private IntegrationProperties integrationProperties;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private ProjectIntegrationAuthorizationService authorization;

    @MockitoBean
    private GitHubProviderClient gitHubClient;

    @MockitoBean
    private AutomaticSyncDispatcher syncDispatcher;

    @MockitoBean
    private IntegrationAvailability availability;

    @MockitoBean
    private IntegrationAttemptLimiter attemptLimiter;

    @MockitoBean
    private AuthenticationAuditService auditService;

    private Project project;
    private GitHubInstallation installation;
    private SagaPrincipal principal;
    private GitHubRepositoriesLinkRequest request;
    private long installationId;
    private long repositoryId;

    @BeforeEach
    void setUp() {
        installationId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        repositoryId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        project = projectRepository.saveAndFlush(Project.builder()
                .name("Initial backfill project")
                .createdByCognitoSub("admin-sub")
                .build());
        installation = installationRepository.saveAndFlush(
                GitHubInstallation.builder()
                        .installationId(installationId)
                        .installedByCognitoSub("admin-sub")
                        .accountLogin("saga")
                        .accountType("Organization")
                        .installationStatus(GitHubInstallationStatus.ACTIVE)
                        .build()
        );
        principal = new SagaPrincipal(
                "admin-sub",
                "admin@saga.test",
                "Admin",
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        request = new GitHubRepositoriesLinkRequest(
                installationId,
                List.of(repositoryId)
        );
        when(availability.gitHubEnabled()).thenReturn(true);
        when(authorization.requireProjectManager(
                principal,
                project.getId()
        )).thenReturn(project);
        when(gitHubClient.installationRepositories(installationId))
                .thenReturn(List.of(repositoryInfo()));
    }

    @Test
    void newRepositoryDispatchesInitialBackfillAfterCommitEvenWhenReconciliationIsDisabled() {
        assertFalse(integrationProperties.reconciliationEnabled());

        service.linkGitHubRepositories(
                principal,
                project.getId(),
                request,
                "127.0.0.1"
        );

        GitRepo saved = gitRepoRepository.findByRepositoryId(repositoryId)
                .orElseThrow();
        assertEquals(
                IntegrationStatus.BACKFILLING,
                saved.getConnectionStatus()
        );
        verify(syncDispatcher).initialGitHubBackfill(saved.getId());
    }

    @Test
    void relinkingStuckBackfillingRepositoryRequestsRecoveryWithoutDuplicateRow() {
        GitRepo stuck = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .url("https://github.test/saga/backend")
                .defaultBranch("main")
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build());

        service.linkGitHubRepositories(
                principal,
                project.getId(),
                request,
                "127.0.0.1"
        );

        verify(syncDispatcher).initialGitHubBackfill(stuck.getId());
        assertEquals(
                1,
                gitRepoRepository.findByProjectIdOrderByFullName(
                        project.getId()
                ).size()
        );
    }

    @Test
    void relinkingAlreadySyncedActiveRepositoryDoesNotBackfillOrDuplicate() {
        LocalDateTime syncedAt = LocalDateTime.now()
                .minusMinutes(5)
                .truncatedTo(ChronoUnit.MICROS);
        GitRepo active = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .url("https://github.test/saga/backend")
                .defaultBranch("main")
                .connectionStatus(IntegrationStatus.ACTIVE)
                .lastSyncedAt(syncedAt)
                .syncCursor(syncedAt)
                .build());
        clearInvocations(syncDispatcher);

        service.linkGitHubRepositories(
                principal,
                project.getId(),
                request,
                "127.0.0.1"
        );

        verify(syncDispatcher, never()).initialGitHubBackfill(active.getId());
        List<GitRepo> repositories =
                gitRepoRepository.findByProjectIdOrderByFullName(
                        project.getId()
                );
        assertEquals(1, repositories.size());
        assertEquals(
                IntegrationStatus.ACTIVE,
                repositories.get(0).getConnectionStatus()
        );
        assertEquals(syncedAt, repositories.get(0).getLastSyncedAt());
    }

    @Test
    void reconnectRequiresDisconnectedStateThenUsesTheSharedInitialBackfillClaim() {
        GitRepo repository = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .url("https://github.test/saga/backend")
                .defaultBranch("main")
                .connectionStatus(IntegrationStatus.DISCONNECTED)
                .build());

        service.reconnectGitHubRepository(
                principal,
                project.getId(),
                repositoryId,
                "127.0.0.1"
        );

        assertEquals(IntegrationStatus.BACKFILLING, gitRepoRepository
                .findById(repository.getId()).orElseThrow().getConnectionStatus());
        verify(syncDispatcher).initialGitHubBackfill(repository.getId());
        IntegrationException duplicate = assertThrows(IntegrationException.class,
                () -> service.reconnectGitHubRepository(principal, project.getId(), repositoryId, "127.0.0.1"));
        assertEquals("GITHUB_RECONNECT_NOT_REQUIRED", duplicate.getCode());
    }

    @Test
    void reconnectRequiresInstallationStillContainingTheRepository() {
        GitRepo repository = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .connectionStatus(IntegrationStatus.DISCONNECTED)
                .build());
        when(gitHubClient.installationRepositories(installationId)).thenReturn(List.of());

        IntegrationException exception = assertThrows(IntegrationException.class,
                () -> service.reconnectGitHubRepository(principal, project.getId(), repositoryId, "127.0.0.1"));

        assertEquals("GITHUB_RECONNECT_REQUIRES_INSTALLATION", exception.getCode());
        assertEquals(IntegrationStatus.DISCONNECTED, gitRepoRepository
                .findById(repository.getId()).orElseThrow().getConnectionStatus());
        verify(syncDispatcher, never()).initialGitHubBackfill(repository.getId());
    }

    @Test
    void disabledGitHubIntegrationDoesNotCallProvider() {
        doThrow(IntegrationException.notConfigured("GitHub"))
                .when(availability)
                .requireGitHub();

        assertThrows(
                IntegrationException.class,
                () -> service.linkGitHubRepositories(
                        principal,
                        project.getId(),
                        request,
                        "127.0.0.1"
                )
        );

        verify(gitHubClient, never())
                .installationRepositories(installationId);
    }

    @Test
    void transactionalEventDispatchesAfterCommitButNotBeforeOrAfterRollback() {
        reset(syncDispatcher);
        UUID committedRepositoryId = UUID.randomUUID();
        TransactionTemplate transactions = new TransactionTemplate(
                transactionManager
        );

        transactions.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new GitHubInitialBackfillRequested(
                            committedRepositoryId
                    )
            );
            verify(syncDispatcher, never()).initialGitHubBackfill(
                    committedRepositoryId
            );
        });

        verify(syncDispatcher).initialGitHubBackfill(committedRepositoryId);

        UUID rolledBackRepositoryId = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            eventPublisher.publishEvent(
                    new GitHubInitialBackfillRequested(
                            rolledBackRepositoryId
                    )
            );
            status.setRollbackOnly();
        });

        verify(syncDispatcher, never()).initialGitHubBackfill(
                rolledBackRepositoryId
        );
    }

    @Test
    void syncStatusIncludesInitialBackfillJobTargetingRepository() {
        GitRepo repository = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .url("https://github.test/saga/backend")
                .defaultBranch("main")
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build());
        SyncJobLog job = syncJobLogRepository.saveAndFlush(
                SyncJobLog.builder()
                        .targetSystem("GITHUB")
                        .targetId(repository.getId())
                        .jobType(SyncJobType.INITIAL_BACKFILL)
                        .status(SyncJobStatus.IN_PROGRESS)
                        .startedAt(LocalDateTime.now())
                        .build()
        );

        SyncStatusResponse response = service.syncStatus(
                principal,
                project.getId()
        );

        verify(authorization).requireProjectManager(principal, project.getId());
        assertEquals(1, response.recentJobs().size());
        assertEquals(job.getId(), response.recentJobs().get(0).id());
        assertEquals(
                SyncJobType.INITIAL_BACKFILL,
                response.recentJobs().get(0).type()
        );
    }

    @Test
    void syncHistoryPaginatesAndFiltersJobsScopedToProjectTargets() {
        GitRepo repository = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project).installation(installation).provider("GITHUB")
                .repositoryId(repositoryId).ownerLogin("saga").name("backend")
                .fullName("saga/backend").connectionStatus(IntegrationStatus.ACTIVE).build());
        syncJobLogRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("GITHUB").targetId(repository.getId())
                .jobType(SyncJobType.RECONCILIATION).status(SyncJobStatus.COMPLETED)
                .startedAt(LocalDateTime.now().minusMinutes(1)).build());
        syncJobLogRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("GITHUB").targetId(repository.getId())
                .jobType(SyncJobType.INITIAL_BACKFILL).status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now()).build());
        syncJobLogRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem("JIRA").targetId(UUID.randomUUID())
                .jobType(SyncJobType.RECONCILIATION).status(SyncJobStatus.COMPLETED)
                .startedAt(LocalDateTime.now()).build());

        var response = service.syncHistory(principal, project.getId(), 0, 1,
                "github", null, null);

        assertEquals(2, response.jobs().totalElements());
        assertEquals(1, response.jobs().content().size());
        assertFalse(response.jobs().content().get(0).targetSystem().equals("JIRA"));
        assertTrue(response.jobs().hasNext());
    }

    @Test
    void initialBackfillClaimCreatesOneRunningJobAndAllowsRetryAfterFailure() {
        GitRepo repository = gitRepoRepository.saveAndFlush(GitRepo.builder()
                .project(project)
                .installation(installation)
                .provider("GITHUB")
                .repositoryId(repositoryId)
                .ownerLogin("saga")
                .name("backend")
                .fullName("saga/backend")
                .url("https://github.test/saga/backend")
                .defaultBranch("main")
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build());

        SyncJobLog first = initialBackfillJobService
                .claim(repository.getId())
                .orElseThrow();
        assertEquals(SyncJobStatus.IN_PROGRESS, first.getStatus());
        assertEquals(SyncJobType.INITIAL_BACKFILL, first.getJobType());
        assertFalse(
                initialBackfillJobService.claim(repository.getId()).isPresent()
        );

        first.setStatus(SyncJobStatus.FAILED);
        first.setCompletedAt(LocalDateTime.now());
        first.setErrorMessage("UNEXPECTED_SYNC_FAILURE");
        syncJobLogRepository.saveAndFlush(first);

        SyncJobLog retry = initialBackfillJobService
                .claim(repository.getId())
                .orElseThrow();
        assertEquals(SyncJobStatus.IN_PROGRESS, retry.getStatus());
        assertFalse(first.getId().equals(retry.getId()));
    }

    private GitHubRepositoryInfo repositoryInfo() {
        return new GitHubRepositoryInfo(
                repositoryId,
                "saga",
                "backend",
                "saga/backend",
                "https://github.test/saga/backend",
                "main"
        );
    }
}
