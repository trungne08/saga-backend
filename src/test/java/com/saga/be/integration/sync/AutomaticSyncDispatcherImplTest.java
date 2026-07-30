package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraIssuePage;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AutomaticSyncDispatcherImplTest {

    private JiraBoardRepository boardRepository;
    private SyncJobLogRepository jobRepository;
    private JiraCredentialService credentialService;
    private JiraProviderClient jiraClient;
    private JiraIssueUpsertService jiraUpsertService;
    private GitRepoRepository gitRepoRepository;
    private GitHubProviderClient gitHubClient;
    private GitHubInitialBackfillJobService initialBackfillJobService;
    private IntegrationAvailability availability;
    private AutomaticSyncDispatcherImpl dispatcher;
    private UUID boardId;
    private JiraBoard board;

    @BeforeEach
    void setUp() {
        boardRepository = mock(JiraBoardRepository.class);
        jobRepository = mock(SyncJobLogRepository.class);
        credentialService = mock(JiraCredentialService.class);
        jiraClient = mock(JiraProviderClient.class);
        jiraUpsertService = mock(JiraIssueUpsertService.class);
        gitRepoRepository = mock(GitRepoRepository.class);
        gitHubClient = mock(GitHubProviderClient.class);
        initialBackfillJobService = mock(
                GitHubInitialBackfillJobService.class
        );
        availability = mock(IntegrationAvailability.class);
        when(availability.jiraEnabled()).thenReturn(true);
        when(availability.gitHubEnabled()).thenReturn(true);
        dispatcher = new AutomaticSyncDispatcherImpl(
                boardRepository,
                gitRepoRepository,
                jobRepository,
                credentialService,
                jiraClient,
                gitHubClient,
                jiraUpsertService,
                mock(GitHubDataUpsertService.class),
                initialBackfillJobService,
                availability,
                new IntegrationProperties(
                        null,
                        null,
                        null,
                        Duration.ofMinutes(10),
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(10),
                        true,
                        Duration.ofMinutes(5)
                )
        );
        boardId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(UUID.randomUUID());
        board = JiraBoard.builder()
                .project(project)
                .cloudId("cloud-id")
                .projectKey("SAGA")
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build();
        board.setId(boardId);
        when(boardRepository.findById(boardId))
                .thenReturn(Optional.of(board));
        when(credentialService.validAccessToken(board)).thenReturn("token");
        when(jobRepository.saveAndFlush(any(SyncJobLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void jiraBackfillConsumesEveryPageAndAdvancesCursorAfterSuccess() {
        JiraIssueSnapshot first = issue(
                "1",
                LocalDateTime.parse("2026-07-29T10:00:00")
        );
        JiraIssueSnapshot second = issue(
                "2",
                LocalDateTime.parse("2026-07-29T11:00:00")
        );
        when(jiraClient.searchIssues(
                eq("token"),
                eq("cloud-id"),
                eq("SAGA"),
                any(),
                eq(null)
        )).thenReturn(new JiraIssuePage(List.of(first), "page-2", false));
        when(jiraClient.searchIssues(
                "token",
                "cloud-id",
                "SAGA",
                null,
                "page-2"
        )).thenReturn(new JiraIssuePage(List.of(second), null, true));
        when(jiraUpsertService.upsert(any(), any())).thenReturn(true);

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(jiraClient, times(2)).searchIssues(
                eq("token"),
                eq("cloud-id"),
                eq("SAGA"),
                eq(null),
                any()
        );
        assertEquals(
                LocalDateTime.parse("2026-07-29T11:00:00"),
                board.getSyncCursor()
        );
        assertEquals(IntegrationStatus.ACTIVE, board.getConnectionStatus());
        assertEquals(0, board.getConsecutiveFailures());
        ArgumentCaptor<SyncJobLog> jobs =
                ArgumentCaptor.forClass(SyncJobLog.class);
        verify(jobRepository, times(2)).saveAndFlush(jobs.capture());
        assertEquals(
                SyncJobStatus.COMPLETED,
                jobs.getAllValues().get(1).getStatus()
        );
        assertEquals(2, jobs.getAllValues().get(1).getItemsProcessed());
    }

    @Test
    void partialFailureDegradesConnectionWithoutAdvancingCursor() {
        LocalDateTime originalCursor =
                LocalDateTime.parse("2026-07-29T09:00:00");
        board.setSyncCursor(originalCursor);
        JiraIssueSnapshot issue = issue(
                "1",
                LocalDateTime.parse("2026-07-29T10:00:00")
        );
        when(jiraClient.searchIssues(
                eq("token"),
                eq("cloud-id"),
                eq("SAGA"),
                any(),
                eq(null)
        )).thenReturn(new JiraIssuePage(List.of(issue), null, true));
        when(jiraUpsertService.upsert(boardId, issue))
                .thenThrow(new IllegalStateException("database unavailable"));

        dispatcher.syncJira(boardId, SyncJobType.RECONCILIATION);

        assertEquals(originalCursor, board.getSyncCursor());
        assertEquals(IntegrationStatus.DEGRADED, board.getConnectionStatus());
        assertEquals(1, board.getConsecutiveFailures());
        ArgumentCaptor<SyncJobLog> jobs =
                ArgumentCaptor.forClass(SyncJobLog.class);
        verify(jobRepository, times(2)).saveAndFlush(jobs.capture());
        SyncJobLog completed = jobs.getAllValues().get(1);
        assertEquals(SyncJobStatus.PARTIAL_FAILURE, completed.getStatus());
        assertEquals(1, completed.getItemsFailed());
        assertNull(completed.getCursorAfter());
        assertEquals("ITEM_UPSERT_FAILED", completed.getErrorMessage());
    }

    @Test
    void githubInitialBackfillCreatesAndCompletesClaimedJob() {
        UUID repositoryId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(UUID.randomUUID());
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(123L)
                .installationStatus(GitHubInstallationStatus.ACTIVE)
                .build();
        GitRepo repository = GitRepo.builder()
                .project(project)
                .installation(installation)
                .ownerLogin("saga")
                .name("backend")
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build();
        repository.setId(repositoryId);
        SyncJobLog job = SyncJobLog.builder()
                .targetSystem("GITHUB")
                .targetId(repositoryId)
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        job.setId(UUID.randomUUID());
        when(initialBackfillJobService.claim(repositoryId))
                .thenReturn(Optional.of(job));
        when(gitRepoRepository.findForSyncById(repositoryId))
                .thenReturn(Optional.of(repository));
        when(gitHubClient.pullRequests(
                123L,
                "saga",
                "backend",
                null
        )).thenReturn(List.of());
        when(gitHubClient.issues(
                123L,
                "saga",
                "backend",
                null
        )).thenReturn(List.of());
        when(gitHubClient.commits(
                123L,
                "saga",
                "backend",
                null
        )).thenReturn(List.of());
        when(gitHubClient.issueComments(
                123L,
                "saga",
                "backend",
                null
        )).thenReturn(List.of());
        when(gitHubClient.reviewComments(
                123L,
                "saga",
                "backend",
                null
        )).thenReturn(List.of());

        dispatcher.syncGitHub(repositoryId, SyncJobType.INITIAL_BACKFILL);

        assertEquals(IntegrationStatus.ACTIVE, repository.getConnectionStatus());
        assertNotNull(repository.getLastSyncedAt());
        assertNotNull(repository.getSyncCursor());
        assertEquals(SyncJobStatus.COMPLETED, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals(0, job.getItemsProcessed());
        assertEquals(0, job.getItemsFailed());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void disabledGitHubIntegrationDoesNotClaimOrCallProvider() {
        when(availability.gitHubEnabled()).thenReturn(false);
        clearInvocations(
                gitRepoRepository,
                gitHubClient,
                initialBackfillJobService
        );

        dispatcher.syncGitHub(
                UUID.randomUUID(),
                SyncJobType.INITIAL_BACKFILL
        );

        verifyNoInteractions(
                gitRepoRepository,
                gitHubClient,
                initialBackfillJobService
        );
    }

    @Test
    void runningInitialBackfillClaimDoesNotCallRepositoryOrProviderAgain() {
        UUID repositoryId = UUID.randomUUID();
        when(initialBackfillJobService.claim(repositoryId))
                .thenReturn(Optional.empty());
        clearInvocations(gitRepoRepository, gitHubClient);

        dispatcher.syncGitHub(
                repositoryId,
                SyncJobType.INITIAL_BACKFILL
        );

        verifyNoInteractions(gitRepoRepository, gitHubClient);
    }

    @Test
    void githubProviderFailureDegradesRepositoryAndCompletesJobSafely() {
        UUID repositoryId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build();
        project.setId(UUID.randomUUID());
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(123L)
                .installationStatus(GitHubInstallationStatus.ACTIVE)
                .build();
        GitRepo repository = GitRepo.builder()
                .project(project)
                .installation(installation)
                .ownerLogin("saga")
                .name("backend")
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build();
        repository.setId(repositoryId);
        SyncJobLog job = SyncJobLog.builder()
                .targetSystem("GITHUB")
                .targetId(repositoryId)
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        when(initialBackfillJobService.claim(repositoryId))
                .thenReturn(Optional.of(job));
        when(gitRepoRepository.findForSyncById(repositoryId))
                .thenReturn(Optional.of(repository));
        when(gitHubClient.pullRequests(
                123L,
                "saga",
                "backend",
                null
        )).thenThrow(new IllegalStateException("provider secret response"));

        dispatcher.syncGitHub(
                repositoryId,
                SyncJobType.INITIAL_BACKFILL
        );

        assertEquals(
                IntegrationStatus.DEGRADED,
                repository.getConnectionStatus()
        );
        assertEquals(1, repository.getConsecutiveFailures());
        assertEquals(SyncJobStatus.FAILED, job.getStatus());
        assertEquals("UNEXPECTED_SYNC_FAILURE", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        verify(jobRepository).saveAndFlush(job);
    }

    private JiraIssueSnapshot issue(String id, LocalDateTime updatedAt) {
        return new JiraIssueSnapshot(
                id,
                "SAGA-" + id,
                "Issue " + id,
                "Task",
                "To Do",
                "Medium",
                null,
                null,
                null,
                null,
                updatedAt.minusDays(1),
                updatedAt,
                null,
                null,
                null,
                null
        );
    }
}
