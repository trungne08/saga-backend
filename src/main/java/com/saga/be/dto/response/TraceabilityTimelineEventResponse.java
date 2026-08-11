package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record TraceabilityTimelineEventResponse(
        TraceabilitySourceType sourceType,
        UUID resourceId,
        String displayKey,
        String title,
        LocalDateTime timestamp
) {
}
