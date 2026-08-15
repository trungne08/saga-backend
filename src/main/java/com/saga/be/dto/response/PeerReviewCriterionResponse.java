package com.saga.be.dto.response;

import com.saga.be.entity.PeerReviewDetail;
import java.util.UUID;

public record PeerReviewCriterionResponse(
        UUID rubricId,
        String criteriaName,
        Integer starRating
) {
    public static PeerReviewCriterionResponse from(PeerReviewDetail detail) {
        return new PeerReviewCriterionResponse(
                detail.getRubricTemplate() != null ? detail.getRubricTemplate().getId() : null,
                detail.getCriteriaName(),
                detail.getStarRating()
        );
    }
}
