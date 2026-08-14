package com.saga.be.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminGraphProcessingReportResponse(
        OffsetDateTime generatedAt,
        int periodDays,
        boolean historySupported,
        List<AdminGraphProcessingPointResponse> points
) {
    public AdminGraphProcessingReportResponse {
        points = points == null ? List.of() : List.copyOf(points);
    }

    /**
     * Reserved day-bucket shape for a future persisted graph-processing history.
     * V1 always returns an empty {@code points} array because history is unsupported.
     */
    public record AdminGraphProcessingPointResponse(
            LocalDate date,
            long nodesCreated,
            long nodesUpdated,
            long edgesCreated,
            long edgesUpdated
    ) {
    }
}
