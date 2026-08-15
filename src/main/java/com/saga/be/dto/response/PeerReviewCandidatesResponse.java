package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record PeerReviewCandidatesResponse(
        UUID teamId,
        UUID sprintId,
        UUID reviewerId,
        List<PeerReviewCandidateResponse> candidates
) {
}
