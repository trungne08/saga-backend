package com.saga.be.integration.sync;

import com.saga.be.entity.Comment;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.PrReview;
import com.saga.be.entity.PullRequest;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.entity.enums.PrReviewStatus;
import com.saga.be.entity.enums.PullRequestStatus;
import com.saga.be.entity.enums.TargetType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.GitHubCommentSnapshot;
import com.saga.be.integration.provider.GitHubCommitSnapshot;
import com.saga.be.integration.provider.GitHubIssueSnapshot;
import com.saga.be.integration.provider.GitHubPullRequestSnapshot;
import com.saga.be.integration.provider.GitHubReviewSnapshot;
import com.saga.be.repository.CommentRepository;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PrReviewRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.service.TeamContributionRefreshService;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubDataUpsertService {

    public enum IssueResult {
        UPSERTED,
        SKIPPED_AS_PULL_REQUEST,
        IGNORED_STALE
    }

    private final GitRepoRepository gitRepoRepository;
    private final GitIssueRepository issueRepository;
    private final CommitDataRepository commitRepository;
    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository reviewRepository;
    private final CommentRepository commentRepository;
    private final IdentityMappingService identityMappingService;
    private final TeamContributionRefreshService teamContributionRefreshService;

    public GitHubDataUpsertService(
            GitRepoRepository gitRepoRepository,
            GitIssueRepository issueRepository,
            CommitDataRepository commitRepository,
            PullRequestRepository pullRequestRepository,
            PrReviewRepository reviewRepository,
            CommentRepository commentRepository,
            IdentityMappingService identityMappingService,
            TeamContributionRefreshService teamContributionRefreshService
    ) {
        this.gitRepoRepository = gitRepoRepository;
        this.issueRepository = issueRepository;
        this.commitRepository = commitRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.reviewRepository = reviewRepository;
        this.commentRepository = commentRepository;
        this.identityMappingService = identityMappingService;
        this.teamContributionRefreshService = teamContributionRefreshService;
    }

    @Transactional
    public IssueResult upsertIssue(UUID repoId, GitHubIssueSnapshot snapshot) {
        if (snapshot.pullRequest()) {
            return IssueResult.SKIPPED_AS_PULL_REQUEST;
        }
        GitRepo repository = requireRepository(repoId);
        GitIssue issue = issueRepository
                .findByRepoIdAndGithubIssueId(repoId, snapshot.id())
                .orElseGet(GitIssue::new);
        if (
            issue.getExternalUpdatedAt() != null
            && snapshot.updatedAt() != null
            && issue.getExternalUpdatedAt().isAfter(snapshot.updatedAt())
        ) {
            return IssueResult.IGNORED_STALE;
        }
        issue.setRepo(repository);
        issue.setGithubIssueId(snapshot.id());
        issue.setNodeId(snapshot.nodeId());
        issue.setIssueNumber(snapshot.number());
        issue.setTitle(snapshot.title());
        issue.setState("closed".equalsIgnoreCase(snapshot.state())
                ? IssueState.CLOSED
                : IssueState.OPEN);
        issue.setClosedAt(snapshot.closedAt());
        issue.setExternalUpdatedAt(snapshot.updatedAt());
        String authorId = stringId(snapshot.authorId());
        issue.setAuthor(attribution(
                issue.getAuthor(),
                issue.getAuthorExternalId(),
                authorId
        ));
        issue.setAuthorExternalId(authorId);
        String assigneeId = stringId(snapshot.assigneeId());
        issue.setAssignee(attribution(
                issue.getAssignee(),
                issue.getAssigneeExternalId(),
                assigneeId
        ));
        issue.setAssigneeExternalId(assigneeId);
        issueRepository.saveAndFlush(issue);
        teamContributionRefreshService.refreshFromRepo(repoId);
        return IssueResult.UPSERTED;
    }

    @Transactional
    public boolean upsertCommit(UUID repoId, GitHubCommitSnapshot snapshot) {
        GitRepo repository = requireRepository(repoId);
        CommitData commit = commitRepository
                .findByRepoIdAndShaHash(repoId, snapshot.sha())
                .orElseGet(CommitData::new);
        if (
            commit.getExternalUpdatedAt() != null
            && snapshot.updatedAt() != null
            && commit.getExternalUpdatedAt().isAfter(snapshot.updatedAt())
        ) {
            return false;
        }
        commit.setRepo(repository);
        commit.setShaHash(snapshot.sha());
        commit.setGithubCommitId(snapshot.sha());
        commit.setMessage(snapshot.message());
        commit.setTimestamp(snapshot.committedAt());
        commit.setAdditions(snapshot.additions());
        commit.setDeletions(snapshot.deletions());
        commit.setFilesChanged(snapshot.filesChanged());
        commit.setExternalUpdatedAt(snapshot.updatedAt());
        String authorId = stringId(snapshot.authorId());
        commit.setAuthor(attribution(
                commit.getAuthor(),
                commit.getAuthorExternalId(),
                authorId
        ));
        commit.setAuthorExternalId(authorId);
        commitRepository.saveAndFlush(commit);
        teamContributionRefreshService.refreshFromRepo(repoId);
        return true;
    }

    @Transactional
    public PullRequest upsertPullRequest(
            UUID repoId,
            GitHubPullRequestSnapshot snapshot
    ) {
        GitRepo repository = requireRepository(repoId);
        PullRequest pull = pullRequestRepository
                .findByRepoIdAndGithubPullRequestId(repoId, snapshot.id())
                .orElseGet(PullRequest::new);
        if (
            pull.getExternalUpdatedAt() != null
            && snapshot.updatedAt() != null
            && pull.getExternalUpdatedAt().isAfter(snapshot.updatedAt())
        ) {
            return pull;
        }
        pull.setRepo(repository);
        pull.setGithubPullRequestId(snapshot.id());
        pull.setNodeId(snapshot.nodeId());
        pull.setPullNumber(snapshot.number());
        pull.setTitle(snapshot.title());
        pull.setStatus(pullStatus(snapshot));
        pull.setMergedAt(snapshot.mergedAt());
        pull.setReviewCount(snapshot.reviewComments());
        pull.setCommentCount(snapshot.comments());
        pull.setExternalUpdatedAt(snapshot.updatedAt());
        String authorId = stringId(snapshot.authorId());
        pull.setAuthor(attribution(
                pull.getAuthor(),
                pull.getAuthorExternalId(),
                authorId
        ));
        pull.setAuthorExternalId(authorId);
        PullRequest savedPullRequest = pullRequestRepository.saveAndFlush(pull);
        teamContributionRefreshService.refreshFromRepo(repoId);
        return savedPullRequest;
    }

    @Transactional
    public boolean upsertReview(UUID repoId, GitHubReviewSnapshot snapshot) {
        PullRequest pull = pullRequestRepository
                .findByRepoIdAndPullNumber(repoId, snapshot.pullNumber())
                .orElseThrow(() -> IntegrationException.conflict(
                        "GITHUB_PULL_REQUEST_MISSING",
                        "A review referenced a pull request that is not synchronized"
                ));
        PrReview review = reviewRepository.findByGithubReviewId(snapshot.id())
                .orElseGet(PrReview::new);
        if (
            review.getExternalUpdatedAt() != null
            && snapshot.updatedAt() != null
            && review.getExternalUpdatedAt().isAfter(snapshot.updatedAt())
        ) {
            return false;
        }
        review.setPullRequest(pull);
        review.setGithubReviewId(snapshot.id());
        review.setStatus(reviewStatus(snapshot.state()));
        review.setTimestamp(snapshot.submittedAt());
        review.setExternalUpdatedAt(snapshot.updatedAt());
        String reviewerId = stringId(snapshot.reviewerId());
        review.setReviewer(attribution(
                review.getReviewer(),
                review.getReviewerExternalId(),
                reviewerId
        ));
        review.setReviewerExternalId(reviewerId);
        reviewRepository.saveAndFlush(review);
        teamContributionRefreshService.refreshFromRepo(repoId);
        return true;
    }

    @Transactional
    public boolean upsertComment(UUID repoId, GitHubCommentSnapshot snapshot) {
        Comment comment = commentRepository
                .findBySourceSystemAndExternalCommentId(
                        "GITHUB",
                        Long.toString(snapshot.id())
                )
                .orElseGet(Comment::new);
        if (
            comment.getExternalUpdatedAt() != null
            && snapshot.updatedAt() != null
            && comment.getExternalUpdatedAt().isAfter(snapshot.updatedAt())
        ) {
            return false;
        }
        comment.setSourceSystem("GITHUB");
        comment.setExternalCommentId(Long.toString(snapshot.id()));
        comment.setBody(snapshot.body());
        comment.setExternalUpdatedAt(snapshot.updatedAt());
        String authorId = stringId(snapshot.authorId());
        comment.setAuthor(attribution(
                comment.getAuthor(),
                comment.getAuthorExternalId(),
                authorId
        ));
        comment.setAuthorExternalId(authorId);

        PullRequest pull = pullRequestRepository
                .findByRepoIdAndPullNumber(repoId, snapshot.targetNumber())
                .orElse(null);
        if (snapshot.pullRequestComment() || pull != null) {
            if (pull == null) {
                throw IntegrationException.conflict(
                        "GITHUB_PULL_REQUEST_MISSING",
                        "A comment referenced a pull request that is not synchronized"
                );
            }
            comment.setPullRequest(pull);
            comment.setGitIssue(null);
            comment.setTargetType(TargetType.PULL_REQUEST);
        } else {
            GitIssue issue = issueRepository
                    .findByRepoIdAndIssueNumber(repoId, snapshot.targetNumber())
                    .orElseThrow(() -> IntegrationException.conflict(
                            "GITHUB_ISSUE_MISSING",
                            "A comment referenced an issue that is not synchronized"
                    ));
            comment.setGitIssue(issue);
            comment.setPullRequest(null);
            comment.setTargetType(TargetType.GITHUB_ISSUE);
        }
        commentRepository.saveAndFlush(comment);
            teamContributionRefreshService.refreshFromRepo(repoId);
            return true;
    }

    private GitRepo requireRepository(UUID repoId) {
        return gitRepoRepository.findById(repoId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "GITHUB_REPOSITORY_NOT_FOUND",
                        "The linked GitHub repository does not exist"
                ));
    }

    private Student attribution(
            Student current,
            String currentExternalId,
            String incomingExternalId
    ) {
        if (Objects.equals(currentExternalId, incomingExternalId)) {
            return current;
        }
        if (incomingExternalId == null) {
            return null;
        }
        return identityMappingService.findActiveStudent(
                IntegrationProvider.GITHUB,
                incomingExternalId
        ).orElse(null);
    }

    private PullRequestStatus pullStatus(GitHubPullRequestSnapshot snapshot) {
        if (snapshot.mergedAt() != null) {
            return PullRequestStatus.MERGED;
        }
        if (snapshot.draft()) {
            return PullRequestStatus.DRAFT;
        }
        return "closed".equalsIgnoreCase(snapshot.state())
                ? PullRequestStatus.CLOSED
                : PullRequestStatus.OPEN;
    }

    private PrReviewStatus reviewStatus(String state) {
        String value = state == null ? "" : state.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "APPROVED" -> PrReviewStatus.APPROVED;
            case "CHANGES_REQUESTED" -> PrReviewStatus.CHANGES_REQUESTED;
            case "COMMENTED", "DISMISSED" -> PrReviewStatus.COMMENTED;
            default -> PrReviewStatus.PENDING;
        };
    }

    private String stringId(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
