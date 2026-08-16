package com.saga.be.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminGraphProcessingReportResponse(
        OffsetDateTime generatedAt,
        int periodDays,
        boolean historySupported,
        OffsetDateTime coverageStart,
        List<AdminGraphProcessingPointResponse> points
) {
    public AdminGraphProcessingReportResponse {
        points = points == null ? List.of() : List.copyOf(points);
    }

    public record AdminGraphProcessingPointResponse(
            LocalDate date,
            long nodesBuilt,
            long edgesBuilt,
            long runCount
    ) {
    }
}
