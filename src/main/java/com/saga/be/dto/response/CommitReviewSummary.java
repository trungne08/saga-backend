package com.saga.be.dto.response;

import com.saga.be.entity.enums.CommitReviewIntentStatus;
import java.time.Instant;

public record CommitReviewSummary(
        CommitReviewIntentStatus intentStatus,
        String reviewMode,
        Instant startedAt,
        Instant completedAt,
        Result result
) {
    public record Result(
            String traceabilityStatus,
            String messageQuality,
            String codeQuality,
            String taskAlignment,
            boolean verdictEligible,
            String verdict,
            String overallStatus
    ) {
    }
}
