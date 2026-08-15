package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record PeerReviewRubricResponse(
        UUID teamId,
        UUID subjectId,
        List<PeerReviewRubricItemResponse> criteria
) {
}
