package com.saga.be.controller;

import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.JiraSprintResponse;
import com.saga.be.dto.request.JiraSprintCreateRequest;
import com.saga.be.dto.request.JiraSprintUpdateRequest;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectSprintService;
import com.saga.be.service.JiraSprintWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Jira Sprint", description = "Đọc và thay đổi Sprint đồng bộ với Jira.")
public class ProjectSprintController {

    private final ProjectSprintService projectSprintService;
    private final JiraSprintWriteService sprintWriteService;

    @GetMapping("/projects/{projectId}/sprints/{sprintId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<JiraSprintResponse> detail(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID sprintId) {
        return ResponseEntity.ok(sprintWriteService.detail(principal, projectId, sprintId));
    }

    @PostMapping("/projects/{projectId}/sprints")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<JiraSprintResponse> create(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId,
            @RequestHeader("Idempotency-Key") String key, @jakarta.validation.Valid @RequestBody JiraSprintCreateRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(sprintWriteService.create(principal, projectId, key, request));
    }

    @PutMapping("/projects/{projectId}/sprints/{sprintId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<JiraSprintResponse> update(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestHeader("Idempotency-Key") String key, @RequestBody JiraSprintUpdateRequest request) {
        return ResponseEntity.ok(sprintWriteService.update(principal, projectId, sprintId, key, request));
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<JiraSprintResponse> start(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestHeader("Idempotency-Key") String key) { return ResponseEntity.ok(sprintWriteService.start(principal, projectId, sprintId, key)); }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<JiraSprintResponse> close(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestHeader("Idempotency-Key") String key) { return ResponseEntity.ok(sprintWriteService.close(principal, projectId, sprintId, key)); }

    @DeleteMapping("/projects/{projectId}/sprints/{sprintId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal SagaPrincipal principal, @PathVariable UUID projectId, @PathVariable UUID sprintId,
            @RequestHeader("Idempotency-Key") String key) { sprintWriteService.delete(principal, projectId, sprintId, key); return ResponseEntity.noContent().build(); }

    @GetMapping("/projects/{projectId}/sprints")
    @Operation(summary = "List sprints by project")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sprint list for the project"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to view this project"),
            @ApiResponse(responseCode = "404", description = "Project does not exist")
    })
    public ResponseEntity<SprintListResponse> getProjectSprints(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Parameter(description = "Project UUID") @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(projectSprintService.getByProject(principal, projectId));
    }

    @GetMapping("/teams/{teamId}/sprints")
    @Operation(summary = "List sprints by team")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Danh sách Sprint. state=PROJECT_NOT_CREATED khi Team đã được phép truy cập nhưng chưa có Project; EMPTY khi Project chưa có Sprint; READY khi có Sprint."),
            @ApiResponse(responseCode = "400", description = "teamId không phải UUID hợp lệ."),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to view this team"),
            @ApiResponse(responseCode = "404", description = "Team does not exist (error=TEAM_NOT_FOUND)")
    })
    public ResponseEntity<SprintListResponse> getTeamSprints(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Parameter(description = "Team UUID") @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(projectSprintService.getByTeam(principal, teamId));
    }
}
