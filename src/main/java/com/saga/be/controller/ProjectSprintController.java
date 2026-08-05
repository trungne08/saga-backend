package com.saga.be.controller;

import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectSprintService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Sprints")
public class ProjectSprintController {

    private final ProjectSprintService projectSprintService;

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
            @ApiResponse(responseCode = "200", description = "Sprint list for the team"),
            @ApiResponse(responseCode = "401", description = "Authentication is required"),
            @ApiResponse(responseCode = "403", description = "Not permitted to view this team"),
            @ApiResponse(responseCode = "404", description = "Team does not exist")
    })
    public ResponseEntity<SprintListResponse> getTeamSprints(
            @AuthenticationPrincipal SagaPrincipal principal,
            @Parameter(description = "Team UUID") @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(projectSprintService.getByTeam(principal, teamId));
    }
}
