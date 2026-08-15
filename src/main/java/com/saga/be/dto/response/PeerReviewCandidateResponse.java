package com.saga.be.dto.response;

import java.util.UUID;

public record PeerReviewCandidateResponse(
        UUID studentId,
        String fullName,
        String studentCode,
        boolean alreadyReviewed,
        UUID existingReviewId,
        Integer existingTotalStarRating
) {
}
