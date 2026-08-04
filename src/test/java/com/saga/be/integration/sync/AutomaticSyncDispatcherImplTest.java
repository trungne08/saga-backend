package com.saga.be.integration.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Project;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraIssuePage;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class AutomaticSyncDispatcherImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:13:49Z"),
            ZoneOffset.UTC
    );

    private JiraBoardRepository boardRepository;
    private SyncJobLogRepository jobRepository;
    private JiraCredentialService credentialService;
    private JiraProviderClient jiraClient;
    private JiraIssueUpsertService jiraUpsertService;
    private GitRepoRepository gitRepoRepository;
    private GitHubProviderClient gitHubClient;
    private GitHubInitialBackfillJobService initialBackfillJobService;
    private JiraSyncJobService jiraSyncJobService;
    private SyncJobFinalizationService syncJobFinalizationService;
    private JiraBoardStateService jiraBoardStateService;
    private IntegrationAvailability availability;
    private AutomaticSyncDispatcherImpl dispatcher;
    private UUID boardId;
    private JiraBoard board;
    private SyncJobLog jiraJob;

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
        jiraSyncJobService = mock(JiraSyncJobService.class);
        syncJobFinalizationService = mock(SyncJobFinalizationService.class);
        jiraBoardStateService = mock(JiraBoardStateService.class);
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
                jiraSyncJobService,
                syncJobFinalizationService,
                jiraBoardStateService,
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
                ),
                new JiraTimeZoneProperties("UTC"),
                FIXED_CLOCK
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
        jiraJob = SyncJobLog.builder()
                .targetSystem("JIRA")
                .targetId(boardId)
                .jobType(SyncJobType.INITIAL_BACKFILL)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
        jiraJob.setId(UUID.randomUUID());
        when(jiraSyncJobService.claim(eq(boardId), any()))
                .thenReturn(Optional.of(jiraJob));
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
                any(),
                eq(null)
        )).thenReturn(new JiraIssuePage(List.of(first), "page-2", false));
        when(jiraClient.searchIssues(
                eq("token"),
                eq("cloud-id"),
                eq("SAGA"),
                isNull(),
                any(),
                eq("page-2")
        )).thenReturn(new JiraIssuePage(List.of(second), null, true));
        when(jiraUpsertService.upsert(any(), any())).thenReturn(true);

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(jiraClient, times(2)).searchIssues(
                eq("token"),
                eq("cloud-id"),
                eq("SAGA"),
                eq(null),
                any(),
                any()
        );
        verify(jiraBoardStateService).complete(eq(boardId), any());
        verify(syncJobFinalizationService).finalizeJob(
                eq(jiraJob.getId()),
                eq(SyncJobStatus.COMPLETED),
                eq(2),
                eq(0),
                any(),
                isNull(),
                isNull()
        );
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
                any(),
                eq(null)
        )).thenReturn(new JiraIssuePage(List.of(issue), null, true));
        when(jiraUpsertService.upsert(boardId, issue))
                .thenThrow(new IllegalStateException("database unavailable"));

        dispatcher.syncJira(boardId, SyncJobType.RECONCILIATION);

        assertEquals(originalCursor, board.getSyncCursor());
        verify(jiraBoardStateService, never()).complete(any(), any());
        verify(jiraBoardStateService).degrade(boardId);
        verify(syncJobFinalizationService).finalizeJob(
                jiraJob.getId(),
                SyncJobStatus.PARTIAL_FAILURE,
                0,
                1,
                null,
                "ITEM_UPSERT_FAILED",
                "UPSERT_ISSUES"
        );
    }

    @Test
    void jiraBackfillWithNoIssuesCompletesWithZeroCounts() {
        when(jiraClient.searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"), any(), any(), eq(null)
        )).thenReturn(new JiraIssuePage(List.of(), null, true));

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(syncJobFinalizationService).finalizeJob(
                eq(jiraJob.getId()),
                eq(SyncJobStatus.COMPLETED),
                eq(0),
                eq(0),
                any(),
                isNull(),
                isNull()
        );
    }

    @Test
    void reconciliationReadsOverlapWindowAndCountsIssueUpdatedBeforeJobStart() {
        LocalDateTime issueUpdated = LocalDateTime.ofInstant(
                Instant.now().minusSeconds(4),
                ZoneOffset.UTC
        );
        LocalDateTime committedCursor = issueUpdated.plusSeconds(1);
        board.setSyncCursor(committedCursor);
        JiraIssueSnapshot sdpOne = new JiraIssueSnapshot(
                "10452", "SDP-1", "SAGA WEBHOOK TEST 01 - UPDATED",
                "Task", "In Progress", null, null, null, null, null,
                issueUpdated.minusDays(1), issueUpdated, null, null, null, null
        );
        when(jiraClient.searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"), any(), any(), eq(null)
        )).thenReturn(new JiraIssuePage(List.of(sdpOne), null, true));

        dispatcher.syncJira(boardId, SyncJobType.RECONCILIATION);

        ArgumentCaptor<Instant> lowerBound = ArgumentCaptor.forClass(
                Instant.class
        );
        ArgumentCaptor<Instant> upperBound = ArgumentCaptor.forClass(
                Instant.class
        );
        verify(jiraClient).searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"),
                lowerBound.capture(), upperBound.capture(), eq(null)
        );
        assertEquals(committedCursor.minusMinutes(5).toInstant(ZoneOffset.UTC),
                lowerBound.getValue());
        assertThat(upperBound.getValue()).isAfter(
                issueUpdated.toInstant(ZoneOffset.UTC)
        );
        verify(jiraUpsertService).upsert(boardId, sdpOne);
        ArgumentCaptor<LocalDateTime> committedUpperBound =
                ArgumentCaptor.forClass(LocalDateTime.class);
        verify(jiraBoardStateService).complete(
                eq(boardId), committedUpperBound.capture()
        );
        assertEquals(
                LocalDateTime.ofInstant(upperBound.getValue(), ZoneOffset.UTC)
                        .withSecond(0).withNano(0),
                committedUpperBound.getValue().withSecond(0).withNano(0)
        );
        verify(syncJobFinalizationService).finalizeJob(
                eq(jiraJob.getId()),
                eq(SyncJobStatus.COMPLETED),
                eq(1),
                eq(0),
                any(),
                isNull(),
                isNull()
        );
    }

    @Test
    void reconciliationDoesNotProcessIssueNewerThanCapturedUpperBound() {
        JiraIssueSnapshot futureIssue = issue(
                "future",
                LocalDateTime.ofInstant(
                        Instant.now().plus(Duration.ofMinutes(1)),
                        ZoneOffset.UTC
                )
        );
        when(jiraClient.searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"), any(), any(), eq(null)
        )).thenReturn(new JiraIssuePage(List.of(futureIssue), null, true));

        dispatcher.syncJira(boardId, SyncJobType.RECONCILIATION);

        verify(jiraUpsertService, never()).upsert(any(), any());
        verify(syncJobFinalizationService).finalizeJob(
                eq(jiraJob.getId()),
                eq(SyncJobStatus.COMPLETED),
                eq(0),
                eq(0),
                any(),
                isNull(),
                isNull()
        );
    }

    @Test
    void jiraSearchFailureFinalizesJobAsFailed() {
        when(jiraClient.searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"), any(), any(), eq(null)
        )).thenThrow(IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE"));

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(syncJobFinalizationService).finalizeJob(
                jiraJob.getId(),
                SyncJobStatus.FAILED,
                0,
                0,
                null,
                "JIRA_PROVIDER_UNAVAILABLE",
                "SEARCH_ISSUES"
        );
    }

    @Test
    void jiraCredentialFailureFinalizesJobAsFailed() {
        when(credentialService.validAccessToken(board))
                .thenThrow(new IllegalStateException("decrypt failed"));

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(syncJobFinalizationService).finalizeJob(
                jiraJob.getId(),
                SyncJobStatus.FAILED,
                0,
                0,
                null,
                "UNEXPECTED_SYNC_FAILURE",
                "LOAD_CREDENTIAL"
        );
    }

    @Test
    void jiraFailureLogsThrowableWithoutLeakingCredentialText() {
        when(credentialService.validAccessToken(board))
                .thenThrow(new IllegalStateException("ACCESS_TOKEN_SECRET"));
        Logger logger = (Logger) LoggerFactory.getLogger(
                AutomaticSyncDispatcherImpl.class
        );
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);
            ILoggingEvent event = appender.list.stream()
                    .filter(value -> value.getFormattedMessage()
                            .contains("Jira sync failed"))
                    .findFirst()
                    .orElseThrow();
            assertNotNull(event.getThrowableProxy());
            assertEquals(true, event.getFormattedMessage()
                    .contains("stage=LOAD_CREDENTIAL"));
            assertEquals(false, event.getFormattedMessage()
                    .contains("ACCESS_TOKEN_SECRET"));
            assertEquals(false, event.getThrowableProxy().getMessage()
                    .contains("ACCESS_TOKEN_SECRET"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void jiraDegradationFailureDoesNotPreventFailedFinalization() {
        when(jiraClient.searchIssues(
                eq("token"), eq("cloud-id"), eq("SAGA"), any(), any(), eq(null)
        )).thenThrow(new IllegalStateException("provider failed"));
        doThrow(new IllegalStateException("board persistence failed"))
                .when(jiraBoardStateService).degrade(boardId);

        dispatcher.syncJira(boardId, SyncJobType.INITIAL_BACKFILL);

        verify(syncJobFinalizationService).finalizeJob(
                jiraJob.getId(),
                SyncJobStatus.FAILED,
                0,
                0,
                null,
                "UNEXPECTED_SYNC_FAILURE",
                "SEARCH_ISSUES"
        );
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
        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                job.getCompletedAt()
        );
        assertEquals(0, job.getItemsProcessed());
        assertEquals(0, job.getItemsFailed());
        verify(jobRepository).saveAndFlush(job);
    }

    @Test
    void githubReconciliationWritesStartAndCompletionUsingFixedUtcClock() {
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
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build();
        repository.setId(repositoryId);
        when(gitRepoRepository.findForSyncById(repositoryId))
                .thenReturn(Optional.of(repository));
        when(gitHubClient.pullRequests(123L, "saga", "backend", null))
                .thenReturn(List.of());
        when(gitHubClient.issues(123L, "saga", "backend", null))
                .thenReturn(List.of());
        when(gitHubClient.commits(123L, "saga", "backend", null))
                .thenReturn(List.of());
        when(gitHubClient.issueComments(123L, "saga", "backend", null))
                .thenReturn(List.of());
        when(gitHubClient.reviewComments(123L, "saga", "backend", null))
                .thenReturn(List.of());

        dispatcher.syncGitHub(repositoryId, SyncJobType.RECONCILIATION);

        ArgumentCaptor<SyncJobLog> saved = ArgumentCaptor.forClass(
                SyncJobLog.class
        );
        verify(jobRepository, times(2)).saveAndFlush(saved.capture());
        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                saved.getAllValues().get(0).getStartedAt()
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                saved.getAllValues().get(1).getCompletedAt()
        );
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
