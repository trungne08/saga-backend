package com.saga.be.controller;

import com.saga.be.dto.response.GitHubRepositoryBranchesResponse;
import com.saga.be.dto.response.GitHubRepositoryCommitsResponse;
import com.saga.be.integration.project.GitHubProjectReadService;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/github/repositories")
public class ProjectGitHubReadController {
    private final GitHubProjectReadService service;
    public ProjectGitHubReadController(GitHubProjectReadService service) { this.service = service; }
    @GetMapping("/{repositoryId}/branches")
    public GitHubRepositoryBranchesResponse branches(@AuthenticationPrincipal SagaPrincipal actor, @PathVariable UUID projectId,
            @PathVariable Long repositoryId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        validate(page, size); return service.branches(actor, projectId, repositoryId, page, size);
    }
    @GetMapping("/{repositoryId}/commits")
    public GitHubRepositoryCommitsResponse commits(@AuthenticationPrincipal SagaPrincipal actor, @PathVariable UUID projectId,
            @PathVariable Long repositoryId, @RequestParam String branch, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        validate(page, size); return service.commits(actor, projectId, repositoryId, branch, page, size);
    }
    private void validate(int page, int size) { if (page < 0 || size < 1 || size > 100) throw com.saga.be.exception.IntegrationException.invalid("GITHUB_PAGE_INVALID", "Pagination is invalid"); }
}
