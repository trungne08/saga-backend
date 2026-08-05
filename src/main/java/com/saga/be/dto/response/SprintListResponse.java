package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record SprintListResponse(
        UUID projectId,
        UUID teamId,
        List<SprintSummaryResponse> sprints
) {
    public static SprintListResponse from(UUID projectId, UUID teamId, List<SprintSummaryResponse> sprints) {
        return new SprintListResponse(projectId, teamId, sprints);
    }
}
