package com.saga.be.dto.response;

import com.saga.be.entity.enums.AdminAnomalySignalType;
import com.saga.be.entity.enums.AdminReportSupportStatus;
import java.util.List;
import java.time.OffsetDateTime;

public record AdminAnomaliesReportResponse(
        OffsetDateTime generatedAt,
        List<AdminAnomalySignalResponse> signals
) {
    public AdminAnomaliesReportResponse {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public record AdminAnomalySignalResponse(
            AdminAnomalySignalType type,
            AdminReportSupportStatus supportStatus,
            Long count
    ) {
    }
}
