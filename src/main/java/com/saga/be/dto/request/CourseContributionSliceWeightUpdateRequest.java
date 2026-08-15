package com.saga.be.dto.request;

public record CourseContributionSliceWeightUpdateRequest(
        Double codeWeight,
        Double testWeight,
        Double documentWeight,
        Double researchWeight
) {
}
