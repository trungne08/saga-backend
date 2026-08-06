package com.saga.be.controller;

import com.saga.be.dto.request.ProjectUpdateRequest;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.dto.response.ProjectDashboardStatsResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectDetailService;
import com.saga.be.service.ProjectDashboardStatsService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectDetailController {

    private final ProjectDetailService projectDetailService;
    private final ProjectDashboardStatsService dashboardStats;

    public ProjectDetailController(ProjectDetailService projectDetailService, ProjectDashboardStatsService dashboardStats) {
        this.projectDetailService = projectDetailService;
        this.dashboardStats = dashboardStats;
    }

    @GetMapping("/{projectId}/dashboard-stats")
    public ProjectDashboardStatsResponse dashboardStats(@AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId) {
        return dashboardStats.get(principal, projectId);
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse get(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return projectDetailService.get(principal, projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectDetailResponse update(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectUpdateRequest request
    ) {
        return projectDetailService.update(principal, projectId, request);
    }
}
