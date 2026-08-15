package com.saga.be.service;

import com.saga.be.dto.response.GitHubIssueDetailResponse;
import com.saga.be.dto.response.GitHubIssueSummaryResponse;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.dto.response.TaskIssueLinkResponse;
import com.saga.be.dto.response.TaskTraceabilityResponse;
import com.saga.be.dto.response.TraceabilityCommitSummaryResponse;
import com.saga.be.dto.response.TraceabilityLinksResponse;
import com.saga.be.dto.response.TraceabilityPullRequestSummaryResponse;
import com.saga.be.dto.response.TraceabilitySourceType;
import com.saga.be.dto.response.TraceabilityTaskSummaryResponse;
import com.saga.be.dto.response.TraceabilityTimelineEventResponse;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.GitIssueCommitLink;
import com.saga.be.entity.GitIssuePullRequestLink;
import com.saga.be.entity.PullRequest;
import com.saga.be.entity.Task;
import com.saga.be.entity.TaskGitIssueLink;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.GitIssueCommitLinkRepository;
import com.saga.be.repository.GitIssuePullRequestLinkRepository;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.PullRequestRepository;
import com.saga.be.repository.TaskGitIssueLinkRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubTraceabilityService {

    private static final int LINK_LIMIT = 100;
    private final ProjectDetailService projects;
    private final ProjectIntegrationAuthorizationService managers;
    private final TaskRepository tasks;
    private final GitIssueRepository issues;
    private final TaskGitIssueLinkRepository taskIssueLinks;
    private final GitIssuePullRequestLinkRepository issuePullLinks;
    private final GitIssueCommitLinkRepository issueCommitLinks;
    private final PullRequestRepository pullRequests;
    private final CommitDataRepository commits;

    public GitHubTraceabilityService(
            ProjectDetailService projects,
            ProjectIntegrationAuthorizationService managers,
            TaskRepository tasks,
            GitIssueRepository issues,
            TaskGitIssueLinkRepository taskIssueLinks,
            GitIssuePullRequestLinkRepository issuePullLinks,
            GitIssueCommitLinkRepository issueCommitLinks,
            PullRequestRepository pullRequests,
            CommitDataRepository commits
    ) {
        this.projects = projects;
        this.managers = managers;
        this.tasks = tasks;
        this.issues = issues;
        this.taskIssueLinks = taskIssueLinks;
        this.issuePullLinks = issuePullLinks;
        this.issueCommitLinks = issueCommitLinks;
        this.pullRequests = pullRequests;
        this.commits = commits;
    }

    @Transactional
    public TaskIssueLinkResponse link(
            SagaPrincipal actor,
            UUID projectId,
            UUID taskId,
            UUID issueId
    ) {
        managers.requireProjectManager(actor, projectId);
        Task task = requireTaskForLink(projectId, taskId);
        GitIssue issue = requireIssue(issueId);
        requireSameProject(projectId, task, issue);
        if (!taskIssueLinks.existsByTaskIdAndGitIssueId(taskId, issueId)) {
            taskIssueLinks.saveAndFlush(TaskGitIssueLink.builder()
                    .task(task)
                    .gitIssue(issue)
                    .build());
        }
        return new TaskIssueLinkResponse(taskId, issueId, true);
    }

    @Transactional
    public void unlink(
            SagaPrincipal actor,
            UUID projectId,
            UUID taskId,
            UUID issueId
    ) {
        managers.requireProjectManager(actor, projectId);
        Task task = requireTaskForLink(projectId, taskId);
        GitIssue issue = requireIssue(issueId);
        requireSameProject(projectId, task, issue);
        taskIssueLinks.findByTaskIdAndGitIssueId(taskId, issueId)
                .ifPresent(taskIssueLinks::delete);
    }

    @Transactional(readOnly = true)
    public GitHubIssueDetailResponse issueDetail(
            SagaPrincipal actor,
            UUID projectId,
            UUID issueId
    ) {
        projects.get(actor, projectId);
        GitIssue issue = issues.findByIdAndRepoProjectId(issueId, projectId)
                .orElseThrow(() -> notFound(
                        "GITHUB_ISSUE_NOT_FOUND",
                        "The GitHub issue does not exist in this project"
                ));
        List<TaskGitIssueLink> taskLinks = taskIssueLinks
                .findByGitIssueIdOrderByTaskExternalKeyAscIdAsc(
                        issueId,
                        PageRequest.of(0, LINK_LIMIT + 1)
                );
        List<GitIssuePullRequestLink> pullLinks = pullLinks(issueId);
        List<GitIssueCommitLink> commitLinks = commitLinks(issueId);
        List<TraceabilityTaskSummaryResponse> linkedTasks = limited(taskLinks).stream()
                .map(link -> TraceabilityTaskSummaryResponse.from(link.getTask()))
                .toList();
        List<TraceabilityPullRequestSummaryResponse> linkedPulls = limited(pullLinks).stream()
                .map(TraceabilityPullRequestSummaryResponse::from)
                .toList();
        List<TraceabilityCommitSummaryResponse> linkedCommits = limited(commitLinks).stream()
                .map(TraceabilityCommitSummaryResponse::from)
                .toList();
        return new GitHubIssueDetailResponse(
                GitHubIssueSummaryResponse.from(issue),
                links(linkedTasks, taskLinks),
                links(linkedPulls, pullLinks),
                links(linkedCommits, commitLinks),
                timelineForIssue(issue, taskLinks, pullLinks, commitLinks)
        );
    }

    @Transactional(readOnly = true)
    public TaskTraceabilityResponse taskTraceability(
            SagaPrincipal actor,
            UUID projectId,
            UUID taskId
    ) {
        projects.get(actor, projectId);
        Task task = tasks.findByIdAndProjectId(taskId, projectId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "The task does not exist"));
        List<TaskGitIssueLink> rawIssueLinks = taskIssueLinks
                .findByTaskIdOrderByGitIssueIssueNumberAscIdAsc(
                        taskId,
                        PageRequest.of(0, LINK_LIMIT + 1)
                );
        List<TaskGitIssueLink> issueLinks = limited(rawIssueLinks);
        List<UUID> issueIds = issueLinks.stream()
                .map(link -> link.getGitIssue().getId())
                .toList();
        List<GitIssuePullRequestLink> pullLinks = issueIds.isEmpty()
                ? List.of()
                : issuePullLinks.findByGitIssueIdInOrderByPullRequestPullNumberAscIdAsc(
                        issueIds,
                        PageRequest.of(0, LINK_LIMIT + 1)
                );
        List<GitIssueCommitLink> commitLinks = issueIds.isEmpty()
                ? List.of()
                : issueCommitLinks.findByGitIssueIdInOrderByCommitTimestampDescIdDesc(
                        issueIds,
                        PageRequest.of(0, LINK_LIMIT + 1)
                );
        Map<UUID, List<GitIssuePullRequestLink>> pullsByIssue = pullLinks.stream()
                .limit(LINK_LIMIT)
                .collect(Collectors.groupingBy(link -> link.getGitIssue().getId()));
        Map<UUID, List<GitIssueCommitLink>> commitsByIssue = commitLinks.stream()
                .limit(LINK_LIMIT)
                .collect(Collectors.groupingBy(link -> link.getGitIssue().getId()));
        List<TaskTraceabilityResponse.ImplementationTrace> implementations = issueLinks.stream()
                .map(link -> implementation(
                        link.getGitIssue(),
                        pullsByIssue.getOrDefault(link.getGitIssue().getId(), List.of()),
                        commitsByIssue.getOrDefault(link.getGitIssue().getId(), List.of()),
                        pullLinks.size() > LINK_LIMIT,
                        commitLinks.size() > LINK_LIMIT
                ))
                .toList();
        List<TraceabilityTimelineEventResponse> timeline = new ArrayList<>();
        add(timeline, taskEvent(task));
        issueLinks.forEach(link -> add(timeline, issueEvent(link.getGitIssue())));
        pullLinks.stream().limit(LINK_LIMIT)
                .map(link -> pullEvent(link.getPullRequest())).forEach(event -> add(timeline, event));
        commitLinks.stream().limit(LINK_LIMIT)
                .map(link -> commitEvent(link.getCommit())).forEach(event -> add(timeline, event));
        return new TaskTraceabilityResponse(
                TraceabilityTaskSummaryResponse.from(task),
                new TraceabilityLinksResponse<>(
                        implementations,
                        rawIssueLinks.size() > LINK_LIMIT
                                || pullLinks.size() > LINK_LIMIT
                                || commitLinks.size() > LINK_LIMIT
                ),
                sorted(timeline, LINK_LIMIT)
        );
    }

    @Transactional(readOnly = true)
    public ProjectTraceabilityResponse projectTimeline(
            SagaPrincipal actor,
            UUID projectId,
            int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw IntegrationException.invalid(
                    "TRACEABILITY_LIMIT_INVALID",
                    "Traceability limit must be between 1 and 100"
            );
        }
        projects.get(actor, projectId);
        PageRequest bound = PageRequest.of(0, limit + 1);
        List<TraceabilityTimelineEventResponse> events = new ArrayList<>();
        tasks.findByProjectIdAndDeletedAtIsNullAndExternalUpdatedAtIsNotNullOrderByExternalUpdatedAtDescIdDesc(
                projectId, bound).stream().map(this::taskEvent).forEach(events::add);
        issues.findByRepoProjectIdAndExternalUpdatedAtIsNotNullOrderByExternalUpdatedAtDescIdDesc(
                projectId, bound).stream().map(this::issueEvent).forEach(events::add);
        pullRequests.findByRepoProjectIdAndExternalUpdatedAtIsNotNullOrderByExternalUpdatedAtDescIdDesc(
                projectId, bound).stream().map(this::pullEvent).forEach(events::add);
        commits.findByRepoProjectIdAndTimestampIsNotNullOrderByTimestampDescIdDesc(
                projectId, bound).stream().map(this::commitEvent).forEach(events::add);
        List<TraceabilityTimelineEventResponse> sorted = sorted(events, limit);
        return new ProjectTraceabilityResponse(
                projectId,
                limit,
                events.size() > limit,
                sorted
        );
    }

    private Task requireTaskForLink(UUID projectId, UUID taskId) {
        return tasks.findForTraceabilityLinkByIdAndProjectId(taskId, projectId)
                .filter(task -> task.getDeletedAt() == null)
                .orElseThrow(() -> notFound("TASK_NOT_FOUND", "The task does not exist"));
    }

    private GitIssue requireIssue(UUID issueId) {
        return issues.findWithRepoProjectById(issueId)
                .orElseThrow(() -> notFound(
                        "GITHUB_ISSUE_NOT_FOUND",
                        "The GitHub issue does not exist"
                ));
    }

    private void requireSameProject(UUID requestedProjectId, Task task, GitIssue issue) {
        UUID taskProjectId = task.getProject().getId();
        UUID issueProjectId = issue.getRepo().getProject().getId();
        if (!requestedProjectId.equals(taskProjectId)
                || !requestedProjectId.equals(issueProjectId)) {
            throw IntegrationException.conflict(
                    "TRACEABILITY_PROJECT_MISMATCH",
                    "Task and GitHub issue must belong to the same project"
            );
        }
    }

    private List<GitIssuePullRequestLink> pullLinks(UUID issueId) {
        return issuePullLinks.findByGitIssueIdOrderByPullRequestPullNumberAscIdAsc(
                issueId,
                PageRequest.of(0, LINK_LIMIT + 1)
        );
    }

    private List<GitIssueCommitLink> commitLinks(UUID issueId) {
        return issueCommitLinks.findByGitIssueIdOrderByCommitTimestampDescIdDesc(
                issueId,
                PageRequest.of(0, LINK_LIMIT + 1)
        );
    }

    private TaskTraceabilityResponse.ImplementationTrace implementation(
            GitIssue issue,
            List<GitIssuePullRequestLink> pullLinks,
            List<GitIssueCommitLink> commitLinks,
            boolean pullsTruncated,
            boolean commitsTruncated
    ) {
        return new TaskTraceabilityResponse.ImplementationTrace(
                GitHubIssueSummaryResponse.from(issue),
                new TraceabilityLinksResponse<>(pullLinks.stream()
                        .map(TraceabilityPullRequestSummaryResponse::from).toList(), pullsTruncated),
                new TraceabilityLinksResponse<>(commitLinks.stream()
                        .map(TraceabilityCommitSummaryResponse::from).toList(), commitsTruncated)
        );
    }

    private List<TraceabilityTimelineEventResponse> timelineForIssue(
            GitIssue issue,
            List<TaskGitIssueLink> taskLinks,
            List<GitIssuePullRequestLink> pullLinks,
            List<GitIssueCommitLink> commitLinks
    ) {
        List<TraceabilityTimelineEventResponse> events = new ArrayList<>();
        add(events, issueEvent(issue));
        limited(taskLinks).stream().map(link -> taskEvent(link.getTask()))
                .forEach(event -> add(events, event));
        limited(pullLinks).stream().map(link -> pullEvent(link.getPullRequest()))
                .forEach(event -> add(events, event));
        limited(commitLinks).stream().map(link -> commitEvent(link.getCommit()))
                .forEach(event -> add(events, event));
        return sorted(events, LINK_LIMIT);
    }

    private TraceabilityTimelineEventResponse taskEvent(Task task) {
        return event(
                TraceabilitySourceType.JIRA_TASK,
                task.getId(),
                task.getExternalKey(),
                task.getTitle(),
                task.getExternalUpdatedAt()
        );
    }

    private TraceabilityTimelineEventResponse issueEvent(GitIssue issue) {
        return event(
                TraceabilitySourceType.GITHUB_ISSUE,
                issue.getId(),
                issue.getIssueNumber() == null ? null : "#" + issue.getIssueNumber(),
                issue.getTitle(),
                issue.getExternalUpdatedAt()
        );
    }

    private TraceabilityTimelineEventResponse pullEvent(PullRequest pull) {
        return event(
                TraceabilitySourceType.GITHUB_PULL_REQUEST,
                pull.getId(),
                pull.getPullNumber() == null ? null : "#" + pull.getPullNumber(),
                pull.getTitle(),
                pull.getExternalUpdatedAt()
        );
    }

    private TraceabilityTimelineEventResponse commitEvent(CommitData commit) {
        return event(
                TraceabilitySourceType.GITHUB_COMMIT,
                commit.getId(),
                commit.getShaHash(),
                commit.getMessage(),
                commit.getTimestamp()
        );
    }

    private TraceabilityTimelineEventResponse event(
            TraceabilitySourceType sourceType,
            UUID id,
            String displayKey,
            String title,
            LocalDateTime timestamp
    ) {
        return timestamp == null
                ? null
                : new TraceabilityTimelineEventResponse(
                        sourceType,
                        id,
                        displayKey,
                        title,
                        timestamp
                );
    }

    private void add(
            List<TraceabilityTimelineEventResponse> events,
            TraceabilityTimelineEventResponse event
    ) {
        if (event != null) {
            events.add(event);
        }
    }

    private List<TraceabilityTimelineEventResponse> sorted(
            List<TraceabilityTimelineEventResponse> events,
            int limit
    ) {
        return events.stream()
                .sorted(Comparator
                        .comparing(TraceabilityTimelineEventResponse::timestamp)
                        .reversed()
                        .thenComparing(TraceabilityTimelineEventResponse::sourceType)
                        .thenComparing(TraceabilityTimelineEventResponse::resourceId))
                .limit(limit)
                .toList();
    }

    private <T> List<T> limited(List<T> values) {
        return values.size() <= LINK_LIMIT
                ? List.copyOf(values)
                : List.copyOf(values.subList(0, LINK_LIMIT));
    }

    private <T> TraceabilityLinksResponse<T> links(List<T> values, List<?> raw) {
        return new TraceabilityLinksResponse<>(values, raw.size() > LINK_LIMIT);
    }

    private IntegrationException notFound(String code, String message) {
        return new IntegrationException(HttpStatus.NOT_FOUND, code, message);
    }
}
