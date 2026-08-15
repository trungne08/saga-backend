package com.saga.be.controller;

import com.saga.be.dto.request.ProjectGroupWeightConfigRequest;
import com.saga.be.dto.response.ProjectGroupWeightConfigResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ProjectGroupWeightConfigService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dự án", description = "Cấu hình trọng số theo từng Team khi Course dùng TEAM mode.")
@RequestMapping("/api/projects/{projectId}/group-weights")
public class ProjectGroupWeightConfigController {

    private final ProjectGroupWeightConfigService projectGroupWeightConfigService;

    public ProjectGroupWeightConfigController(ProjectGroupWeightConfigService projectGroupWeightConfigService) {
        this.projectGroupWeightConfigService = projectGroupWeightConfigService;
    }

    @PutMapping
    public ProjectGroupWeightConfigResponse update(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectGroupWeightConfigRequest request
    ) {
        return projectGroupWeightConfigService.update(principal, projectId, request);
    }
}
