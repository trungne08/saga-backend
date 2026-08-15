package com.saga.be.controller;

import com.saga.be.dto.request.CreateTeamProjectRequest;
import com.saga.be.dto.response.ProjectResponse;
import com.saga.be.integration.project.TeamProjectService;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dự án", description = "Tạo dự án cho nhóm.")
@RequestMapping("/api/teams/{teamId}/projects")
public class TeamProjectController {

    private final TeamProjectService projectService;

    public TeamProjectController(TeamProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateTeamProjectRequest request,
            HttpServletRequest servletRequest
    ) {
        return projectService.create(
                principal,
                teamId,
                request,
                servletRequest.getRemoteAddr()
        );
    }
}
