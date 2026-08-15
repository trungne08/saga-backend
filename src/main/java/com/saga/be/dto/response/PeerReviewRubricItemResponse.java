package com.saga.be.dto.response;

import com.saga.be.entity.RubricTemplate;
import java.math.BigDecimal;
import java.util.UUID;

public record PeerReviewRubricItemResponse(
        UUID rubricId,
        String criteriaName,
        BigDecimal weight,
        String description
) {
    public static PeerReviewRubricItemResponse from(RubricTemplate rubricTemplate) {
        return new PeerReviewRubricItemResponse(
                rubricTemplate.getId(),
                rubricTemplate.getCriteriaName(),
                rubricTemplate.getWeight(),
                rubricTemplate.getDescription()
        );
    }
}
