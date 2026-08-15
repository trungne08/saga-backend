package com.saga.be.controller;

import com.saga.be.dto.response.GitHubIssueDetailResponse;
import com.saga.be.dto.response.GitHubIssueListResponse;
import com.saga.be.entity.enums.IssueState;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.GitHubIssueReadService;
import com.saga.be.service.GitHubTraceabilityService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.saga.be.dto.response.IssueCommitLinkResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "GitHub",
        description = "Đọc repository, nhánh, commit, Issue và traceability GitHub qua backend."
)
@RequestMapping("/api/projects/{projectId}/github/issues")
public class ProjectGitHubIssueController {

    private final GitHubIssueReadService issueReadService;
    private final GitHubTraceabilityService traceabilityService;

    public ProjectGitHubIssueController(
            GitHubIssueReadService issueReadService,
            GitHubTraceabilityService traceabilityService
    ) {
        this.issueReadService = issueReadService;
        this.traceabilityService = traceabilityService;
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem danh sách GitHub Issue của dự án",
            description = "Đọc snapshot local có filter, counter và phân trang; không gọi GitHub."
    )
    public GitHubIssueListResponse list(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID projectId,
            @RequestParam(required = false) IssueState state,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return issueReadService.list(
                actor,
                projectId,
                state,
                repositoryId,
                keyword,
                assignedToMe,
                page,
                size
        );
    }

    @GetMapping("/{issueId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem chi tiết GitHub Issue",
            description = "Trả metadata an toàn và các Task/PR/Commit đã liên kết từ local DB."
    )
    public GitHubIssueDetailResponse detail(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID issueId
    ) {
        return traceabilityService.issueDetail(actor, projectId, issueId);
    }

    @PostMapping("/{issueId}/commits/{commitId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Liên kết GitHub Issue với Commit",
            description = "Tạo quan hệ truy vết thủ công tường minh trong SAGA; không gọi GitHub và không suy từ commit message."
    )
    public IssueCommitLinkResponse linkCommit(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID commitId
    ) {
        return traceabilityService.linkCommit(actor, projectId, issueId, commitId);
    }

    @DeleteMapping("/{issueId}/commits/{commitId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Gỡ liên kết GitHub Issue khỏi Commit",
            description = "Xóa quan hệ truy vết local; repeated unlink vẫn trả 204."
    )
    public ResponseEntity<Void> unlinkCommit(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID projectId,
            @PathVariable UUID issueId,
            @PathVariable UUID commitId
    ) {
        traceabilityService.unlinkCommit(actor, projectId, issueId, commitId);
        return ResponseEntity.noContent().build();
    }
}
