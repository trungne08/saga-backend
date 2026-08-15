package com.saga.be.controller;

import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.GitHubTraceabilityService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(
        name = "GitHub",
        description = "Đọc repository, nhánh, commit, Issue và traceability GitHub qua backend."
)
@RequestMapping("/api/projects/{projectId}/traceability")
public class ProjectTraceabilityController {

    private final GitHubTraceabilityService traceabilityService;

    public ProjectTraceabilityController(GitHubTraceabilityService traceabilityService) {
        this.traceabilityService = traceabilityService;
    }

    @GetMapping
    @io.swagger.v3.oas.annotations.Operation(
            summary = "Xem timeline truy vết của dự án",
            description = "Timeline local có giới hạn rõ ràng; không dùng thời điểm persist thay cho thời điểm provider."
    )
    public ProjectTraceabilityResponse timeline(
            @AuthenticationPrincipal SagaPrincipal actor,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return traceabilityService.projectTimeline(actor, projectId, limit);
    }
}
