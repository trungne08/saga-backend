package com.saga.be.integration.sync;

import com.saga.be.config.IntegrationProperties;
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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AutomaticSyncDispatcherImpl implements AutomaticSyncDispatcher {

    private final JiraBoardRepository jiraBoardRepository;
    private final GitRepoRepository gitRepoRepository;
    private final SyncJobLogRepository jobRepository;
    private final JiraCredentialService jiraCredentialService;
    private final JiraProviderClient jiraClient;
    private final GitHubProviderClient gitHubClient;
    private final JiraIssueUpsertService jiraUpsertService;
    private final GitHubDataUpsertService gitHubUpsertService;
    private final Duration overlapWindow;

    public AutomaticSyncDispatcherImpl(
            JiraBoardRepository jiraBoardRepository,
            GitRepoRepository gitRepoRepository,
            SyncJobLogRepository jobRepository,
            JiraCredentialService jiraCredentialService,
            JiraProviderClient jiraClient,
            GitHubProviderClient gitHubClient,
            JiraIssueUpsertService jiraUpsertService,
            GitHubDataUpsertService gitHubUpsertService,
            IntegrationProperties properties
    ) {
        this.jiraBoardRepository = jiraBoardRepository;
        this.gitRepoRepository = gitRepoRepository;
        this.jobRepository = jobRepository;
        this.jiraCredentialService = jiraCredentialService;
        this.jiraClient = jiraClient;
        this.gitHubClient = gitHubClient;
        this.jiraUpsertService = jiraUpsertService;
        this.gitHubUpsertService = gitHubUpsertService;
        this.overlapWindow = properties.overlapWindow() == null
                ? Duration.ofMinutes(5)
                : properties.overlapWindow();
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
        JiraBoard board = jiraBoardRepository.findById(boardId).orElse(null);
        if (board == null || board.getConnectionStatus()
                == IntegrationStatus.DISCONNECTED) {
            return;
        }
        LocalDateTime cursorBefore = board.getSyncCursor();
        SyncJobLog job = startJob(
                "JIRA",
                board.getProject().getId(),
                jobType,
                cursorBefore
        );
        int processed = 0;
        int failed = 0;
        LocalDateTime maxUpdated = cursorBefore;
        try {
            String token = jiraCredentialService.validAccessToken(board);
            LocalDateTime updatedAfter = cursorBefore == null
                    ? null
                    : cursorBefore.minus(overlapWindow);
            String nextPageToken = null;
            Set<String> seenPageTokens = new HashSet<>();
            do {
                JiraIssuePage page = jiraClient.searchIssues(
                        token,
                        board.getCloudId(),
                        board.getProjectKey(),
                        updatedAfter,
                        nextPageToken
                );
                for (JiraIssueSnapshot issue : page.issues()) {
                    try {
                        jiraUpsertService.upsert(boardId, issue);
                        processed++;
                        maxUpdated = later(maxUpdated, issue.updatedAt());
                    } catch (RuntimeException exception) {
                        failed++;
                    }
                }
                nextPageToken = page.nextPageToken();
                if (
                    nextPageToken != null
                    && !seenPageTokens.add(nextPageToken)
                ) {
                    throw IntegrationException.unavailable(
                            "JIRA_PAGINATION_LOOP"
                    );
                }
            } while (nextPageToken != null && !nextPageToken.isBlank());

            if (failed == 0) {
                LocalDateTime cursorAfter = maxUpdated == null
                        ? LocalDateTime.now()
                        : maxUpdated;
                completeJira(board, cursorAfter);
                completeJob(
                        job,
                        SyncJobStatus.COMPLETED,
                        processed,
                        0,
                        cursorAfter,
                        null
                );
            } else {
                degradeJira(board);
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
            degradeJira(board);
            completeJob(
                    job,
                    SyncJobStatus.FAILED,
                    processed,
                    failed,
                    null,
                    exception.getCode()
            );
        } catch (RuntimeException exception) {
            degradeJira(board);
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

    void syncGitHub(UUID repositoryLocalId, SyncJobType jobType) {
        GitRepo repository = gitRepoRepository.findById(repositoryLocalId)
                .orElse(null);
        if (
            repository == null
            || repository.getConnectionStatus() == IntegrationStatus.DISCONNECTED
        ) {
            return;
        }
        LocalDateTime cursorBefore = repository.getSyncCursor();
        SyncJobLog job = startJob(
                "GITHUB",
                repository.getProject().getId(),
                jobType,
                cursorBefore
        );
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
            UUID projectId,
            SyncJobType type,
            LocalDateTime cursorBefore
    ) {
        return jobRepository.saveAndFlush(SyncJobLog.builder()
                .targetSystem(targetSystem)
                .targetId(projectId)
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

    private void completeJira(JiraBoard board, LocalDateTime cursor) {
        board.setSyncCursor(cursor);
        board.setLastSyncedAt(LocalDateTime.now());
        board.setConnectionStatus(IntegrationStatus.ACTIVE);
        board.setConsecutiveFailures(0);
        jiraBoardRepository.saveAndFlush(board);
    }

    private void degradeJira(JiraBoard board) {
        board.setConsecutiveFailures(board.getConsecutiveFailures() + 1);
        board.setConnectionStatus(IntegrationStatus.DEGRADED);
        jiraBoardRepository.saveAndFlush(board);
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
}
