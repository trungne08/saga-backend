package com.saga.be.controller;

import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.dto.request.JiraTaskTransitionRequest;
import com.saga.be.dto.request.JiraTaskAssigneeRequest;
import com.saga.be.dto.request.JiraTaskSprintRequest;
import com.saga.be.dto.request.JiraTaskEstimationRequest;
import com.saga.be.dto.response.JiraTaskTransitionResponse;
import com.saga.be.dto.response.TaskIssueLinkResponse;
import com.saga.be.dto.response.TaskTraceabilityResponse;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectTaskReadService;
import com.saga.be.service.JiraTaskWriteService;
import com.saga.be.service.GitHubTraceabilityService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Jira Task", description = "Đọc và thay đổi task đồng bộ với Jira.")
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
public class ProjectTaskReadController {

    private final ProjectTaskReadService taskReadService;
    private final JiraTaskWriteService taskWriteService;
    private final GitHubTraceabilityService traceabilityService;

    @PostMapping
    public ResponseEntity<TaskReadResponse> createTask(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @jakarta.validation.Valid @RequestBody JiraTaskCreateRequest request
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(
                taskWriteService.create(principal, projectId, idempotencyKey, request)
        );
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskReadResponse> updateTask(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key, @RequestBody JiraTaskUpdateRequest request) {
        return ResponseEntity.ok(taskWriteService.update(principal, projectId, taskId, key, request));
    }

    @GetMapping("/{taskId}/transitions")
    public ResponseEntity<java.util.List<JiraTaskTransitionResponse>> transitions(@AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId, @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskWriteService.transitions(principal, projectId, taskId));
    }

    @PostMapping("/{taskId}/transitions")
    public ResponseEntity<TaskReadResponse> transition(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key, @jakarta.validation.Valid @RequestBody JiraTaskTransitionRequest request) {
        return ResponseEntity.ok(taskWriteService.transition(principal, projectId, taskId, key, request.transitionId()));
    }

    @PutMapping("/{taskId}/assignee")
    public ResponseEntity<TaskReadResponse> assignee(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key, @RequestBody JiraTaskAssigneeRequest request) {
        return ResponseEntity.ok(taskWriteService.assign(principal, projectId, taskId, key, request));
    }

    @PutMapping("/{taskId}/sprint")
    public ResponseEntity<TaskReadResponse> sprint(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key, @RequestBody JiraTaskSprintRequest request) {
        return ResponseEntity.ok(taskWriteService.sprint(principal, projectId, taskId, key, request));
    }

    @PutMapping("/{taskId}/estimation")
    public ResponseEntity<TaskReadResponse> estimate(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key, @jakarta.validation.Valid @RequestBody JiraTaskEstimationRequest request) {
        return ResponseEntity.ok(taskWriteService.estimate(principal, projectId, taskId, key, request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping(path = "/{taskId}/attachments", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<com.saga.be.dto.response.JiraTaskAttachmentsResponse> attach(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String key,
            @RequestParam(value = "files", required = false) java.util.List<MultipartFile> files,
            @RequestParam(value = "link", required = false) String link
    ) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(
                taskWriteService.attach(principal, projectId, taskId, key, files, link)
        );
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @RequestHeader("Idempotency-Key") String key) {
        taskWriteService.delete(principal, projectId, taskId, key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<TaskReadResponse>> getTasks(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "externalKey") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(taskReadService.getTasks(
                principal,
                projectId,
                keyword,
                sprintId,
                assigneeId,
                status,
                sortBy,
                sortDirection,
                page,
                size
        ));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskReadResponse> getTask(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID taskId
    ) {
        return ResponseEntity.ok(taskReadService.getTask(principal, projectId, taskId));
    }

    @PostMapping("/{taskId}/github-issues/{issueId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Liên kết Jira Task với GitHub Issue",
            description = "Tạo quan hệ truy vết chỉ trong SAGA; không sửa Jira hoặc GitHub."
    )
    public TaskIssueLinkResponse linkGitHubIssue(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID issueId
    ) {
        return traceabilityService.link(principal, projectId, taskId, issueId);
    }

    @DeleteMapping("/{taskId}/github-issues/{issueId}")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Gỡ liên kết Jira Task khỏi GitHub Issue",
            description = "Xóa quan hệ truy vết local; repeated unlink vẫn trả 204."
    )
    public ResponseEntity<Void> unlinkGitHubIssue(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID issueId
    ) {
        traceabilityService.unlink(principal, projectId, taskId, issueId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{taskId}/traceability")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem chuỗi truy vết Task đến code",
            description = "Đọc snapshot local Task → GitHub Issue → Pull Request/Commit, không gọi provider."
    )
    public TaskTraceabilityResponse traceability(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID taskId
    ) {
        return traceabilityService.taskTraceability(principal, projectId, taskId);
    }
}
