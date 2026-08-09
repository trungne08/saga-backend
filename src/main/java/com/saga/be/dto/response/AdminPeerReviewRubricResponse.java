package com.saga.be.dto.response;

import com.saga.be.entity.RubricTemplate;
import java.math.BigDecimal;
import java.util.UUID;

public record AdminPeerReviewRubricResponse(
        UUID rubricId,
        String criteriaName,
        BigDecimal weight,
        String description
) {
    public static AdminPeerReviewRubricResponse from(RubricTemplate rubric) {
        return new AdminPeerReviewRubricResponse(
                rubric.getId(),
                rubric.getCriteriaName(),
                rubric.getWeight(),
                rubric.getDescription()
        );
    }
}
