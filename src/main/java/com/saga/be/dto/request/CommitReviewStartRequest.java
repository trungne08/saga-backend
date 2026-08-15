package com.saga.be.dto.request;

import java.util.UUID;

public record CommitReviewStartRequest(
        UUID projectId,
        long providerRepositoryId,
        String commitSha,
        String reviewPolicyVersion,
        String priority
) {
}
