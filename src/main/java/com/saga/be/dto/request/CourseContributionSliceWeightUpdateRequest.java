package com.saga.be.dto.request;

public record CourseContributionSliceWeightUpdateRequest(
        Double codeWeight,
        Double documentWeight,
        Double designWeight
) {
}
