package com.saga.be.integration.sync;

import com.saga.be.config.IntegrationProperties;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.GitHubCommentSnapshot;
import com.saga.be.integration.provider.GitHubCommitSnapshot;
import com.saga.be.integration.provider.GitHubIssueSnapshot;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.GitHubPullRequestSnapshot;
import com.saga.be.integration.provider.GitHubReviewSnapshot;
import com.saga.be.integration.provider.JiraIssuePage;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AutomaticSyncDispatcherImpl implements AutomaticSyncDispatcher {

    private static final Logger log = LoggerFactory.getLogger(
            AutomaticSyncDispatcherImpl.class
    );

    private final JiraBoardRepository jiraBoardRepository;
    private final GitRepoRepository gitRepoRepository;
    private final SyncJobLogRepository jobRepository;
    private final JiraCredentialService jiraCredentialService;
    private final JiraProviderClient jiraClient;
    private final GitHubProviderClient gitHubClient;
    private final JiraIssueUpsertService jiraUpsertService;
    private final GitHubDataUpsertService gitHubUpsertService;
    private final GitHubInitialBackfillJobService initialBackfillJobService;
    private final JiraSyncJobService jiraSyncJobService;
    private final SyncJobFinalizationService syncJobFinalizationService;
    private final JiraBoardStateService jiraBoardStateService;
    private final IntegrationAvailability availability;
    private final Duration overlapWindow;
    private final ZoneId jiraZoneId;

    public AutomaticSyncDispatcherImpl(
            JiraBoardRepository jiraBoardRepository,
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository,
            JiraCredentialService jiraCredentialService,
            JiraProviderClient jiraClient,
            GitHubProviderClient gitHubClient,
            JiraIssueUpsertService jiraUpsertService,
            GitHubDataUpsertService gitHubUpsertService,
            GitHubInitialBackfillJobService initialBackfillJobService,
            JiraSyncJobService jiraSyncJobService,
            SyncJobFinalizationService syncJobFinalizationService,
            JiraBoardStateService jiraBoardStateService,
            IntegrationAvailability availability,
            IntegrationProperties properties,
            JiraTimeZoneProperties jiraTimeZoneProperties
    ) {
        this.jiraBoardRepository = jiraBoardRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.jobRepository = jobRepository;
        this.jiraCredentialService = jiraCredentialService;
        this.jiraClient = jiraClient;
        this.gitHubClient = gitHubClient;
        this.jiraUpsertService = jiraUpsertService;
        this.gitHubUpsertService = gitHubUpsertService;
        this.initialBackfillJobService = initialBackfillJobService;
        this.jiraSyncJobService = jiraSyncJobService;
        this.syncJobFinalizationService = syncJobFinalizationService;
        this.jiraBoardStateService = jiraBoardStateService;
        this.availability = availability;
        this.overlapWindow = properties.overlapWindow() == null
                ? Duration.ofMinutes(5)
                : properties.overlapWindow();
        this.jiraZoneId = ZoneId.of(jiraTimeZoneProperties.timeZone());
    }

    @Override
    @Async
    public void initialJiraBackfill(UUID boardId) {
        syncJira(boardId, SyncJobType.INITIAL_BACKFILL);
    }

    @Override
    @Async
    public void initialGitHubBackfill(UUID repositoryLocalId) {
        syncGitHub(repositoryLocalId, SyncJobType.INITIAL_BACKFILL);
    }

    @Override
    @Async
    public void reconcileJira(UUID boardId) {
        syncJira(boardId, SyncJobType.RECONCILIATION);
    }

    @Override
    @Async
    public void reconcileGitHub(UUID repositoryLocalId) {
        syncGitHub(repositoryLocalId, SyncJobType.RECONCILIATION);
    }

    void syncJira(UUID boardId, SyncJobType jobType) {
        SyncJobLog job = null;
        JiraBoard board = null;
        int processed = 0;
        int failed = 0;
        JiraSyncStage stage = JiraSyncStage.CLAIM_JOB;
        try {
            if (!availability.jiraEnabled()) {
                return;
            }
            job = jiraSyncJobService.claim(boardId, jobType).orElse(null);
            if (job == null) {
                return;
            }
            stage = JiraSyncStage.LOAD_BOARD;
            board = jiraBoardRepository.findById(boardId).orElse(null);
            if (board == null || board.getConnectionStatus()
                    == IntegrationStatus.DISCONNECTED) {
                finalizeJiraJob(
                        job,
                        SyncJobStatus.FAILED,
                        processed,
                        failed,
                        null,
                        "JIRA_BOARD_UNAVAILABLE",
                        JiraSyncStage.LOAD_BOARD
                );
                return;
            }
            Instant cursorBefore = board.getSyncCursor() == null
                    ? null
                    : board.getSyncCursor().toInstant(ZoneOffset.UTC);
            Instant capturedUpperBound = Instant.now();
            Instant effectiveLowerBound = JiraSyncWindow.effectiveLowerBound(
                    cursorBefore,
                    capturedUpperBound,
                    overlapWindow
            );
            LocalDateTime lowerBoundForJql =
                    JiraSyncWindow.lowerBoundForJql(
                            effectiveLowerBound,
                            jiraZoneId
                    );
            LocalDateTime upperBoundExclusiveForJql =
                    JiraSyncWindow.upperBoundExclusiveForJql(
                            capturedUpperBound,
                            jiraZoneId
                    );
            stage = board.getTokenExpiresAt() != null
                    && !board.getTokenExpiresAt().isAfter(
                            LocalDateTime.now().plusMinutes(1)
                    )
                    ? JiraSyncStage.REFRESH_TOKEN
                    : JiraSyncStage.LOAD_CREDENTIAL;
            String token = jiraCredentialService.validAccessToken(board);
            String nextPageToken = null;
            boolean lastPage;
            Set<String> seenPageTokens = new HashSet<>();
            int issuesFetchedFromProvider = 0;
            int issuesAfterUpperBoundFilter = 0;
            do {
                stage = JiraSyncStage.SEARCH_ISSUES;
                JiraIssuePage page = jiraClient.searchIssues(
                        token,
                        board.getCloudId(),
                        board.getProjectKey(),
                        effectiveLowerBound,
                        capturedUpperBound,
                        nextPageToken
                );
                issuesFetchedFromProvider += page.issues().size();
                for (JiraIssueSnapshot issue : page.issues()) {
                    if (!JiraSyncWindow.isWithinCapturedUpperBound(
                            issue.updatedAtUtc(),
                            capturedUpperBound
                    )) {
                        continue;
                    }
                    issuesAfterUpperBoundFilter++;
                    try {
                        stage = JiraSyncStage.UPSERT_ISSUES;
                        jiraUpsertService.upsert(boardId, issue);
                        processed++;
                    } catch (RuntimeException exception) {
                        failed++;
                        logJiraFailure(
                                "Jira issue upsert failed",
                                boardId,
                                job,
                                jobType,
                                JiraSyncStage.UPSERT_ISSUES,
                                categoryOf(exception),
                                exception
                        );
                    }
                }
                nextPageToken = page.nextPageToken();
                lastPage = page.last();
                log.info("Jira search completed: boardId={}, jobId={}, jobType={}, endpoint=/rest/api/3/search/jql, projectKey={}, jiraZoneId={}, cursorBeforeUtc={}, lowerBoundUtc={}, lowerBoundForJql={}, upperBoundUtc={}, upperBoundExclusiveForJql={}, sanitizedJql={}, httpStatus=200, issuesFetchedFromProvider={}, issuesAfterUpperBoundFilter={}, itemsProcessed={}, itemsFailed={}, isLast={}, hasNextPageToken={}",
                        boardId, job.getId(), jobType, board.getProjectKey(),
                        jiraZoneId, cursorBefore, effectiveLowerBound, lowerBoundForJql,
                        capturedUpperBound, upperBoundExclusiveForJql,
                        jiraSearchJql(
                                board.getProjectKey(),
                                lowerBoundForJql,
                                upperBoundExclusiveForJql
                        ),
                        issuesFetchedFromProvider, issuesAfterUpperBoundFilter,
                        processed, failed, page.last(),
                        nextPageToken != null && !nextPageToken.isBlank());
                if (
                    nextPageToken != null
                    && !seenPageTokens.add(nextPageToken)
                ) {
                    throw IntegrationException.unavailable(
                            "JIRA_PAGINATION_LOOP"
                    );
                }
                if (!lastPage && (nextPageToken == null || nextPageToken.isBlank())) {
                    throw IntegrationException.unavailable("JIRA_PAGINATION_TOKEN_MISSING");
                }
            } while (!lastPage && nextPageToken != null && !nextPageToken.isBlank());

            if (failed == 0) {
                LocalDateTime cursorAfter = LocalDateTime.ofInstant(
                        capturedUpperBound,
                        ZoneOffset.UTC
                );
                stage = JiraSyncStage.COMPLETE_BOARD;
                completeJira(boardId, cursorAfter);
                stage = JiraSyncStage.FINALIZE_JOB;
                finalizeJiraJob(
                        job,
                        SyncJobStatus.COMPLETED,
                        processed,
                        0,
                        cursorAfter,
                        null
                );
            } else {
                degradeJiraSafely(boardId, job, jobType, stage);
                stage = JiraSyncStage.FINALIZE_JOB;
                finalizeJiraJob(
                        job,
                        SyncJobStatus.PARTIAL_FAILURE,
                        processed,
                        failed,
                        null,
                        "ITEM_UPSERT_FAILED",
                        JiraSyncStage.UPSERT_ISSUES
                );
            }
        } catch (IntegrationException exception) {
            finalizeJiraFailure(
                    boardId,
                    job,
                    jobType,
                    processed,
                    failed,
                    exception.getCode(),
                    stage,
                    exception
            );
        } catch (RuntimeException exception) {
            finalizeJiraFailure(
                    boardId,
                    job,
                    jobType,
                    processed,
                    failed,
                    categoryOf(exception),
                    stage,
                    exception
            );
        }
    }

    void syncGitHub(UUID repositoryLocalId, SyncJobType jobType) {
        if (!availability.gitHubEnabled()) {
            return;
        }
        SyncJobLog claimedJob = null;
        if (jobType == SyncJobType.INITIAL_BACKFILL) {
            claimedJob = initialBackfillJobService.claim(repositoryLocalId)
                    .orElse(null);
            if (claimedJob == null) {
                return;
            }
        }
        GitRepo repository = gitRepoRepository
                .findForSyncById(repositoryLocalId)
                .orElse(null);
        if (
            repository == null
            || repository.getConnectionStatus() == IntegrationStatus.DISCONNECTED
        ) {
            if (claimedJob != null) {
                completeJob(
                        claimedJob,
                        SyncJobStatus.FAILED,
                        0,
                        0,
                        null,
                        "GITHUB_REPOSITORY_UNAVAILABLE"
                );
            }
            return;
        }
        LocalDateTime cursorBefore = repository.getSyncCursor();
        SyncJobLog job = claimedJob == null
                ? startJob(
                        "GITHUB",
                        repositoryLocalId,
                        jobType,
                        cursorBefore
                )
                : claimedJob;
        int processed = 0;
        int failed = 0;
        LocalDateTime maxUpdated = cursorBefore;
        try {
            if (
                repository.getInstallation().getInstallationStatus()
                        != GitHubInstallationStatus.ACTIVE
            ) {
                throw IntegrationException.conflict(
                        "GITHUB_INSTALLATION_INACTIVE",
                        "The GitHub App installation is not active"
                );
            }
            long installationId =
                    repository.getInstallation().getInstallationId();
            LocalDateTime since = cursorBefore == null
                    ? null
                    : cursorBefore.minus(overlapWindow);
            String owner = repository.getOwnerLogin();
            String name = repository.getName();

            List<GitHubPullRequestSnapshot> pulls = gitHubClient.pullRequests(
                    installationId,
                    owner,
                    name,
                    since
            );
            for (GitHubPullRequestSnapshot pull : pulls) {
                try {
                    gitHubUpsertService.upsertPullRequest(
                            repositoryLocalId,
                            pull
                    );
                    processed++;
                    maxUpdated = later(maxUpdated, pull.updatedAt());
                    for (GitHubReviewSnapshot review : gitHubClient.reviews(
                            installationId,
                            owner,
                            name,
                            pull.number()
                    )) {
                        gitHubUpsertService.upsertReview(
                                repositoryLocalId,
                                review
                        );
                        processed++;
                        maxUpdated = later(maxUpdated, review.updatedAt());
                    }
                } catch (RuntimeException exception) {
                    failed++;
                }
            }

            for (GitHubIssueSnapshot issue : gitHubClient.issues(
                    installationId,
                    owner,
                    name,
                    since
            )) {
                try {
                    GitHubDataUpsertService.IssueResult result =
                            gitHubUpsertService.upsertIssue(
                                    repositoryLocalId,
                                    issue
                            );
                    if (result != GitHubDataUpsertService.IssueResult
                            .SKIPPED_AS_PULL_REQUEST) {
                        processed++;
                    }
                    maxUpdated = later(maxUpdated, issue.updatedAt());
                } catch (RuntimeException exception) {
                    failed++;
                }
            }

            for (GitHubCommitSnapshot commit : gitHubClient.commits(
                    installationId,
                    owner,
                    name,
                    since
            )) {
                try {
                    gitHubUpsertService.upsertCommit(
                            repositoryLocalId,
                            commit
                    );
                    processed++;
                    maxUpdated = later(maxUpdated, commit.updatedAt());
                } catch (RuntimeException exception) {
                    failed++;
                }
            }

            List<GitHubCommentSnapshot> comments = new java.util.ArrayList<>();
            comments.addAll(gitHubClient.issueComments(
                    installationId,
                    owner,
                    name,
                    since
            ));
            comments.addAll(gitHubClient.reviewComments(
                    installationId,
                    owner,
                    name,
                    since
            ));
            for (GitHubCommentSnapshot comment : comments) {
                try {
                    gitHubUpsertService.upsertComment(
                            repositoryLocalId,
                            comment
                    );
                    processed++;
                    maxUpdated = later(maxUpdated, comment.updatedAt());
                } catch (RuntimeException exception) {
                    failed++;
                }
            }

            if (failed == 0) {
                LocalDateTime cursorAfter = maxUpdated == null
                        ? LocalDateTime.now()
                        : maxUpdated;
                completeGitHub(repository, cursorAfter);
                completeJob(
                        job,
                        SyncJobStatus.COMPLETED,
                        processed,
                        0,
                        cursorAfter,
                        null
                );
            } else {
                degradeGitHub(repository);
                completeJob(
                        job,
                        SyncJobStatus.PARTIAL_FAILURE,
                        processed,
                        failed,
                        null,
                        "ITEM_UPSERT_FAILED"
                );
            }
        } catch (IntegrationException exception) {
            degradeGitHub(repository);
            completeJob(
                    job,
                    SyncJobStatus.FAILED,
                    processed,
                    failed,
                    null,
                    exception.getCode()
            );
        } catch (RuntimeException exception) {
            degradeGitHub(repository);
            completeJob(
                    job,
                    SyncJobStatus.FAILED,
                    processed,
                    failed,
                    null,
                    "UNEXPECTED_SYNC_FAILURE"
            );
        }
    }

    private SyncJobLog startJob(
            String targetSystem,
            UUID targetId,
            SyncJobType type,
            LocalDateTime cursorBefore
    ) {
        return jobRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem(targetSystem)
                .targetId(targetId)
                .jobType(type)
                .status(SyncJobStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .cursorBefore(cursorBefore)
                .build());
    }

    private void completeJob(
            SyncJobLog job,
            SyncJobStatus status,
            int processed,
            int failed,
            LocalDateTime cursorAfter,
            String safeErrorCategory
    ) {
        job.setStatus(status);
        job.setItemsProcessed(processed);
        job.setItemsFailed(failed);
        job.setCursorAfter(cursorAfter);
        job.setErrorMessage(safeErrorCategory);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.saveAndFlush(job);
    }

    private void finalizeJiraFailure(
            UUID boardId,
            SyncJobLog job,
            SyncJobType jobType,
            int processed,
            int failed,
            String errorCategory,
            JiraSyncStage stage,
            RuntimeException exception
    ) {
        degradeJiraSafely(boardId, job, jobType, stage);
        finalizeJiraJob(
                job,
                SyncJobStatus.FAILED,
                processed,
                failed,
                null,
                errorCategory,
                stage
        );
        logJiraFailure(
                "Jira sync failed",
                boardId,
                job,
                jobType,
                stage,
                errorCategory,
                exception
        );
    }

    private void finalizeJiraJob(
            SyncJobLog job,
            SyncJobStatus status,
            int processed,
            int failed,
            LocalDateTime cursorAfter,
            String safeErrorCategory
    ) {
        finalizeJiraJob(
                job,
                status,
                processed,
                failed,
                cursorAfter,
                safeErrorCategory,
                null
        );
    }

    private void finalizeJiraJob(
            SyncJobLog job,
            SyncJobStatus status,
            int processed,
            int failed,
            LocalDateTime cursorAfter,
            String safeErrorCategory,
            JiraSyncStage failureStage
    ) {
        if (job == null || job.getId() == null) {
            return;
        }
        try {
            syncJobFinalizationService.finalizeJob(
                    job.getId(),
                    status,
                    processed,
                    failed,
                    cursorAfter,
                    safeErrorCategory,
                    failureStage == null ? null : failureStage.name()
            );
        } catch (RuntimeException exception) {
            logJiraFailure(
                    "Jira sync finalization deferred",
                    null,
                    job,
                    job.getJobType(),
                    JiraSyncStage.FINALIZE_JOB,
                    categoryOf(exception),
                    exception
            );
        }
    }

    private void completeJira(UUID boardId, LocalDateTime cursor) {
        jiraBoardStateService.complete(boardId, cursor);
    }

    private void degradeJiraSafely(
            UUID boardId,
            SyncJobLog job,
            SyncJobType jobType,
            JiraSyncStage originalStage
    ) {
        try {
            jiraBoardStateService.degrade(boardId);
        } catch (RuntimeException exception) {
            logJiraFailure(
                    "Jira sync degradation failed",
                    boardId,
                    job,
                    jobType,
                    JiraSyncStage.DEGRADE_BOARD,
                    categoryOf(exception),
                    exception
            );
        }
    }

    private void completeGitHub(GitRepo repository, LocalDateTime cursor) {
        repository.setSyncCursor(cursor);
        repository.setLastSyncedAt(LocalDateTime.now());
        repository.setConnectionStatus(IntegrationStatus.ACTIVE);
        repository.setConsecutiveFailures(0);
        gitRepoRepository.saveAndFlush(repository);
    }

    private void degradeGitHub(GitRepo repository) {
        repository.setConsecutiveFailures(
                repository.getConsecutiveFailures() + 1
        );
        repository.setConnectionStatus(IntegrationStatus.DEGRADED);
        gitRepoRepository.saveAndFlush(repository);
    }

    private String jiraSearchJql(
            String projectKey,
            LocalDateTime lowerBoundForJql,
            LocalDateTime upperBoundExclusiveForJql
    ) {
        StringBuilder jql = new StringBuilder("project = ")
                .append(projectKey.replaceAll("[^A-Za-z0-9_-]", ""));
        if (lowerBoundForJql != null) {
            jql.append(" AND updated >= \"")
                    .append(java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm")
                            .format(lowerBoundForJql))
                    .append("\"");
        }
        if (upperBoundExclusiveForJql != null) {
            jql.append(" AND updated < \"")
                    .append(java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm")
                            .format(upperBoundExclusiveForJql))
                    .append("\"");
        }
        return jql.append(" ORDER BY updated ASC, id ASC").toString();
    }

    private LocalDateTime later(
            LocalDateTime current,
            LocalDateTime candidate
    ) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current)
                ? candidate
                : current;
    }

    private String categoryOf(RuntimeException exception) {
        if (exception instanceof IntegrationException integrationException) {
            return integrationException.getCode();
        }
        if (exception instanceof OptimisticLockingFailureException) {
            return "JIRA_BOARD_CONCURRENT_UPDATE";
        }
        return "UNEXPECTED_SYNC_FAILURE";
    }

    private void logJiraFailure(
            String message,
            UUID boardId,
            SyncJobLog job,
            SyncJobType jobType,
            JiraSyncStage stage,
            String errorCategory,
            RuntimeException exception
    ) {
        Throwable root = rootCause(exception);
        log.error(
                "{}: boardId={}, jobId={}, jobType={}, stage={}, "
                        + "errorCategory={}, exceptionClass={}, rootCauseClass={}",
                message,
                boardId,
                job == null ? null : job.getId(),
                jobType,
                stage,
                errorCategory,
                exception.getClass().getName(),
                root.getClass().getName(),
                safeThrowable(exception)
        );
    }

    private RuntimeException safeThrowable(RuntimeException exception) {
        RuntimeException safe = new RuntimeException(
                "Sanitized Jira sync exception; inspect exceptionClass and rootCauseClass"
        );
        safe.setStackTrace(exception.getStackTrace());
        return safe;
    }

    private Throwable rootCause(Throwable exception) {
        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private enum JiraSyncStage {
        CLAIM_JOB,
        LOAD_BOARD,
        LOAD_CREDENTIAL,
        REFRESH_TOKEN,
        SEARCH_ISSUES,
        UPSERT_ISSUES,
        COMPLETE_BOARD,
        DEGRADE_BOARD,
        FINALIZE_JOB
    }
}
