package com.saga.be.controller;

import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectTaskReadService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
public class ProjectTaskReadController {

    private final ProjectTaskReadService taskReadService;

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
}
