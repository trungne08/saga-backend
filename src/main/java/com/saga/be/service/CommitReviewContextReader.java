package com.saga.be.service;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitIssueCommitLink;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskGitIssueLink;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueCommitLinkRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TaskGitIssueLinkRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommitReviewContextReader {

    static final int TRACEABILITY_LIMIT = 100;

    private final ProjectRepository projects;
    private final GitRepoRepository repositories;
    private final CommitDataRepository commits;
    private final GitIssueCommitLinkRepository issueCommitLinks;
    private final TaskGitIssueLinkRepository taskIssueLinks;

    public CommitReviewContextReader(
            ProjectRepository projects,
            GitRepoRepository repositories,
            CommitDataRepository commits,
            GitIssueCommitLinkRepository issueCommitLinks,
            TaskGitIssueLinkRepository taskIssueLinks
    ) {
        this.projects = projects;
        this.repositories = repositories;
        this.commits = commits;
        this.issueCommitLinks = issueCommitLinks;
        this.taskIssueLinks = taskIssueLinks;
    }

    @Transactional(readOnly = true)
    public SourceSnapshot load(UUID projectId, long providerRepositoryId, String commitSha) {
        if (!projects.existsById(projectId)) {
            throw notFound("SAGA_PROJECT_NOT_FOUND", "The SAGA project does not exist");
        }
        GitRepo repository = repositories
                .findForCommitContextByProjectIdAndRepositoryId(
                        projectId,
                        providerRepositoryId
                )
                .orElseThrow(() -> notFound(
                        "GITHUB_REPOSITORY_NOT_FOUND",
                        "The linked GitHub repository does not exist in this project"
                ));
        if (!"GITHUB".equalsIgnoreCase(repository.getProvider())) {
            throw new IntegrationException(
                    HttpStatus.CONFLICT,
                    "GITHUB_REPOSITORY_PROVIDER_MISMATCH",
                    "The linked repository is not a GitHub repository"
            );
        }
        if (repository.getInstallation() == null
                || repository.getInstallation().getInstallationId() == null) {
            throw IntegrationException.conflict(
                    "GITHUB_RECONNECT_REQUIRES_INSTALLATION",
                    "The GitHub repository needs to be reconnected"
            );
        }
        CommitData commit = commits.findByRepoIdAndShaHash(repository.getId(), commitSha)
                .orElseThrow(() -> notFound(
                        "GITHUB_COMMIT_NOT_FOUND",
                        "The GitHub commit does not exist in this linked repository"
                ));
        List<GitIssueCommitLink> rawIssueLinks = issueCommitLinks
                .findByCommitIdOrderByGitIssueIssueNumberAscIdAsc(
                        commit.getId(),
                        PageRequest.of(0, TRACEABILITY_LIMIT + 1)
                );
        List<GitIssueCommitLink> issueLinks = rawIssueLinks.stream()
                .limit(TRACEABILITY_LIMIT)
                .toList();
        List<UUID> issueIds = issueLinks.stream()
                .map(link -> link.getGitIssue().getId())
                .toList();
        List<TaskGitIssueLink> rawTaskLinks = issueIds.isEmpty()
                ? List.of()
                : taskIssueLinks.findByGitIssueIdInOrderByTaskExternalKeyAscIdAsc(
                        issueIds,
                        PageRequest.of(0, TRACEABILITY_LIMIT + 1)
                );
        List<TaskGitIssueLink> taskLinks = rawTaskLinks.stream()
                .limit(TRACEABILITY_LIMIT)
                .toList();
        validateSameProject(projectId, issueLinks, taskLinks);
        Map<UUID, List<TaskLinkSnapshot>> tasksByIssue = taskLinks.stream()
                .collect(Collectors.groupingBy(
                        link -> link.getGitIssue().getId(),
                        Collectors.mapping(this::task, Collectors.toList())
                ));
        List<IssueLinkSnapshot> issues = issueLinks.stream()
                .map(link -> issue(link, tasksByIssue.getOrDefault(
                        link.getGitIssue().getId(), List.of()
                )))
                .toList();
        return new SourceSnapshot(
                projectId,
                new RepositorySnapshot(
                        repository.getId(),
                        repository.getRepositoryId(),
                        repository.getProvider(),
                        repository.getOwnerLogin(),
                        repository.getName(),
                        repository.getFullName(),
                        repository.getInstallation().getInstallationId()
                ),
                new CommitSnapshot(
                        commit.getId(),
                        commit.getShaHash(),
                        commit.getMessage(),
                        commit.getTimestamp(),
                        commit.getAdditions(),
                        commit.getDeletions(),
                        commit.getFilesChanged()
                ),
                issues,
                rawIssueLinks.size() > TRACEABILITY_LIMIT
                        || rawTaskLinks.size() > TRACEABILITY_LIMIT
        );
    }

    private void validateSameProject(
            UUID projectId,
            List<GitIssueCommitLink> issueLinks,
            List<TaskGitIssueLink> taskLinks
    ) {
        boolean mismatch = issueLinks.stream().map(GitIssueCommitLink::getGitIssue)
                .anyMatch(issue -> !projectId.equals(issue.getRepo().getProject().getId()))
                || taskLinks.stream().map(TaskGitIssueLink::getTask)
                .anyMatch(task -> !projectId.equals(task.getProject().getId()));
        if (mismatch) {
            throw IntegrationException.conflict(
                    "TRACEABILITY_PROJECT_MISMATCH",
                    "Traceability evidence crosses a SAGA project boundary"
            );
        }
    }

    private IssueLinkSnapshot issue(
            GitIssueCommitLink link,
            List<TaskLinkSnapshot> linkedTasks
    ) {
        GitIssue issue = link.getGitIssue();
        return new IssueLinkSnapshot(
                link.getId(),
                issue.getId(),
                issue.getIssueNumber(),
                issue.getTitle(),
                issue.getState() == null ? null : issue.getState().name(),
                link.getRelationType().name(),
                linkedTasks
        );
    }

    private TaskLinkSnapshot task(TaskGitIssueLink link) {
        Task task = link.getTask();
        return new TaskLinkSnapshot(
                link.getId(),
                link.getGitIssue().getId(),
                task.getId(),
                task.getExternalKey(),
                task.getTitle(),
                task.getDescription(),
                task.getType() == null ? null : task.getType().name(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getPriority() == null ? null : task.getPriority().name(),
                task.getStoryPoint()
        );
    }

    private IntegrationException notFound(String code, String message) {
        return new IntegrationException(HttpStatus.NOT_FOUND, code, message);
    }

    public record SourceSnapshot(
            UUID projectId,
            RepositorySnapshot repository,
            CommitSnapshot commit,
            List<IssueLinkSnapshot> linkedIssues,
            boolean traceabilityTruncated
    ) {
        public SourceSnapshot {
            linkedIssues = linkedIssues == null ? List.of() : List.copyOf(linkedIssues);
        }
    }

    public record RepositorySnapshot(
            UUID localRepositoryId,
            Long providerRepositoryId,
            String provider,
            String ownerLogin,
            String name,
            String fullName,
            Long installationId
    ) {
    }

    public record CommitSnapshot(
            UUID commitId,
            String sha,
            String message,
            LocalDateTime committedAt,
            Integer additions,
            Integer deletions,
            Integer filesChanged
    ) {
    }

    public record IssueLinkSnapshot(
            UUID issueCommitLinkId,
            UUID issueId,
            Integer issueNumber,
            String title,
            String state,
            String relationType,
            List<TaskLinkSnapshot> linkedTasks
    ) {
        public IssueLinkSnapshot {
            linkedTasks = linkedTasks == null ? List.of() : List.copyOf(linkedTasks);
        }
    }

    public record TaskLinkSnapshot(
            UUID taskIssueLinkId,
            UUID issueId,
            UUID taskId,
            String externalKey,
            String title,
            String description,
            String type,
            String status,
            String priority,
            Integer storyPoint
    ) {
    }
}
