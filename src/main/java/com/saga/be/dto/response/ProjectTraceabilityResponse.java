package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record ProjectTraceabilityResponse(
        UUID projectId,
        int limit,
        boolean truncated,
        List<TraceabilityTimelineEventResponse> timeline
) {

    public ProjectTraceabilityResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
}
