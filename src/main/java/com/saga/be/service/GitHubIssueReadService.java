package com.saga.be.service;

import com.saga.be.dto.response.GitHubIssueListResponse;
import com.saga.be.dto.response.GitHubIssueSummaryResponse;
import com.saga.be.entity.GitIssue;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.GitIssueRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubIssueReadService {

    private final ProjectDetailService projects;
    private final GitRepoRepository repositories;
    private final GitIssueRepository issues;

    public GitHubIssueReadService(
            ProjectDetailService projects,
            GitRepoRepository repositories,
            GitIssueRepository issues
    ) {
        this.projects = projects;
        this.repositories = repositories;
        this.issues = issues;
    }

    @Transactional(readOnly = true)
    public GitHubIssueListResponse list(
            SagaPrincipal actor,
            UUID projectId,
            IssueState state,
            Long repositoryId,
            String keyword,
            boolean assignedToMe,
            int page,
            int size
    ) {
        validatePage(page, size);
        projects.get(actor, projectId);
        if (repositoryId != null && repositories
                .findByProjectIdAndRepositoryId(projectId, repositoryId)
                .isEmpty()) {
            throw notFound(
                    "GITHUB_REPOSITORY_NOT_FOUND",
                    "The linked GitHub repository does not exist"
            );
        }

        Specification<GitIssue> summaryScope = projectScope(projectId);
        if (repositoryId != null) {
            summaryScope = summaryScope.and(repositoryScope(repositoryId));
        }
        Specification<GitIssue> query = summaryScope;
        if (state != null) {
            query = query.and((root, ignored, builder) ->
                    builder.equal(root.get("state"), state));
        }
        if (keyword != null && !keyword.isBlank()) {
            query = query.and(keyword(keyword));
        }
        if (assignedToMe) {
            if (actor.applicationRole() != ApplicationRole.STUDENT
                    || actor.localProfileId() == null) {
                throw IntegrationException.invalid(
                        "GITHUB_ASSIGNED_TO_ME_UNAVAILABLE",
                        "Assigned-to-me filtering is available to Student profiles"
                );
            }
            query = query.and(assignee(actor.localProfileId()));
        }

        Page<GitIssue> result = issues.findAll(
                query,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("externalUpdatedAt").nullsLast(),
                        Sort.Order.desc("issueNumber").nullsLast(),
                        Sort.Order.desc("id")
                ))
        );
        long assignedCount = actor.applicationRole() == ApplicationRole.STUDENT
                && actor.localProfileId() != null
                ? issues.count(summaryScope.and(assignee(actor.localProfileId())))
                : 0L;
        return new GitHubIssueListResponse(
                result.getContent().stream().map(GitHubIssueSummaryResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                new GitHubIssueListResponse.Summary(
                        issues.count(summaryScope.and(state(IssueState.OPEN))),
                        issues.count(summaryScope.and(state(IssueState.CLOSED))),
                        assignedCount,
                        issues.count(summaryScope.and((root, ignored, builder) ->
                                builder.isNull(root.get("assignee"))))
                )
        );
    }

    private Specification<GitIssue> projectScope(UUID projectId) {
        return (root, ignored, builder) -> builder.equal(
                root.get("repo").get("project").get("id"),
                projectId
        );
    }

    private Specification<GitIssue> repositoryScope(Long repositoryId) {
        return (root, ignored, builder) -> builder.equal(
                root.get("repo").get("repositoryId"),
                repositoryId
        );
    }

    private Specification<GitIssue> state(IssueState state) {
        return (root, ignored, builder) -> builder.equal(root.get("state"), state);
    }

    private Specification<GitIssue> assignee(UUID studentId) {
        return (root, ignored, builder) -> builder.equal(
                root.get("assignee").get("id"),
                studentId
        );
    }

    private Specification<GitIssue> keyword(String keyword) {
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        Integer number = parseIssueNumber(normalized);
        return (root, ignored, builder) -> number == null
                ? builder.like(builder.lower(root.get("title")), "%" + normalized + "%")
                : builder.or(
                        builder.like(builder.lower(root.get("title")), "%" + normalized + "%"),
                        builder.equal(root.get("issueNumber"), number)
                );
    }

    private Integer parseIssueNumber(String value) {
        String candidate = value.startsWith("#") ? value.substring(1) : value;
        try {
            return candidate.isBlank() ? null : Integer.valueOf(candidate);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw IntegrationException.invalid(
                    "GITHUB_ISSUE_PAGE_INVALID",
                    "Pagination is invalid"
            );
        }
    }

    private IntegrationException notFound(String code, String message) {
        return new IntegrationException(org.springframework.http.HttpStatus.NOT_FOUND, code, message);
    }
}
