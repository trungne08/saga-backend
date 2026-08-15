package com.saga.be.dto.response;

import java.util.List;

public record PeerReviewDefaultRubricResponse(
        List<PeerReviewRubricItemResponse> criteria
) {
}
