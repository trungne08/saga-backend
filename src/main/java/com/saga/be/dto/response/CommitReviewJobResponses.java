package com.saga.be.dto.response;

import java.util.Map;
import java.util.UUID;

public final class CommitReviewJobResponses {

    private CommitReviewJobResponses() {
    }

    public record Start(
            UUID jobId,
            String status,
            String reviewPolicyVersion,
            String priority
    ) {
    }

    public record Status(
            UUID jobId,
            String status,
            String reviewPolicyVersion,
            String priority,
            String safeErrorCode,
            Map<String, Object> finalResult
    ) {
    }
}
