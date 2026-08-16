package com.saga.be.integration.project;

import com.saga.be.dto.response.CommitReviewSummary;
import com.saga.be.dto.response.GitHubRepositoryBranchesResponse;
import com.saga.be.dto.response.GitHubRepositoryCommitsResponse;
import com.saga.be.entity.GitRepo;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubBranchCommitInfo;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CommitReviewSummaryResolver;
import com.saga.be.service.ProjectDetailService;
import com.saga.be.repository.GitRepoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubProjectReadService {
    private final ProjectDetailService projects;
    private final GitRepoRepository repositories;
    private final GitHubProviderClient provider;
    private final CommitReviewSummaryResolver reviewSummaries;
    public GitHubProjectReadService(
            ProjectDetailService projects,
            GitRepoRepository repositories,
            GitHubProviderClient provider,
            CommitReviewSummaryResolver reviewSummaries
    ) {
        this.projects = projects; this.repositories = repositories; this.provider = provider;
        this.reviewSummaries = reviewSummaries;
    }
    @Transactional(readOnly = true)
    public GitHubRepositoryBranchesResponse branches(SagaPrincipal actor, UUID projectId, Long externalId, int page, int size) {
        projects.get(actor, projectId);
        GitRepo repo = repository(projectId, externalId);
        List<GitHubRepositoryBranchesResponse.Branch> all = provider.branches(installation(repo), repo.getOwnerLogin(), repo.getName())
                .stream().map(v -> new GitHubRepositoryBranchesResponse.Branch(v.name(), v.headSha(), v.protectedBranch())).toList();
        int from = Math.min(page * size, all.size()), to = Math.min(from + size, all.size());
        return new GitHubRepositoryBranchesResponse(repo.getId(), repo.getFullName(),
                new GitHubRepositoryBranchesResponse.Page(all.subList(from, to), page, size, to < all.size()));
    }
    @Transactional(readOnly = true)
    public GitHubRepositoryCommitsResponse commits(SagaPrincipal actor, UUID projectId, Long externalId, String branch, int page, int size) {
        projects.get(actor, projectId);
        if (branch == null || branch.trim().isEmpty()) throw IntegrationException.invalid("GITHUB_BRANCH_INVALID", "A GitHub branch is required");
        GitRepo repo = repository(projectId, externalId);
        List<GitHubBranchCommitInfo> all = provider.branchCommits(installation(repo), repo.getOwnerLogin(), repo.getName(), branch.trim());
        int from = Math.min(page * size, all.size()), to = Math.min(from + size, all.size());
        List<GitHubBranchCommitInfo> pageSlice = all.subList(from, to);
        // Review summaries are resolved once for the whole page slice, not per commit, so this
        // stays a bounded number of queries regardless of page size (see CommitReviewSummaryResolver).
        Map<String, CommitReviewSummary> reviews = reviewSummaries.resolve(
                repo.getId(),
                pageSlice.stream().map(GitHubBranchCommitInfo::sha).toList()
        );
        List<GitHubRepositoryCommitsResponse.Commit> content = pageSlice.stream()
                .map(v -> new GitHubRepositoryCommitsResponse.Commit(v.sha(), v.message(), v.authorName(), v.authorLogin(),
                        v.authoredAt(), v.committedAt(), v.url(), reviews.get(v.sha())))
                .toList();
        return new GitHubRepositoryCommitsResponse(repo.getId(), branch.trim(),
                new GitHubRepositoryCommitsResponse.Page(content, page, size, to < all.size()));
    }
    private GitRepo repository(UUID projectId, Long externalId) {
        return repositories.findByProjectIdAndRepositoryId(projectId, externalId).orElseThrow(() ->
                new IntegrationException(org.springframework.http.HttpStatus.NOT_FOUND, "GITHUB_REPOSITORY_NOT_FOUND", "The linked GitHub repository does not exist"));
    }
    private long installation(GitRepo repo) {
        if (repo.getInstallation() == null) throw IntegrationException.conflict("GITHUB_RECONNECT_REQUIRES_INSTALLATION", "The GitHub repository needs to be reconnected");
        return repo.getInstallation().getInstallationId();
    }
}
