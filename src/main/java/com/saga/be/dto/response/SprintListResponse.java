package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record SprintListResponse(
        UUID projectId,
        UUID teamId,
        SprintListState state,
        List<SprintSummaryResponse> sprints
) {
    public static SprintListResponse from(UUID projectId, UUID teamId, List<SprintSummaryResponse> sprints) {
        return new SprintListResponse(
                projectId,
                teamId,
                sprints.isEmpty() ? SprintListState.EMPTY : SprintListState.READY,
                sprints
        );
    }

    public static SprintListResponse projectNotCreated(UUID teamId) {
        return new SprintListResponse(null, teamId, SprintListState.PROJECT_NOT_CREATED, List.of());
    }
}
