package com.saga.be.dto.request;

import java.util.UUID;

public record CourseContributionSliceWeightRequest(
        Double codeWeight,
        Double documentWeight,
        Double designWeight,
        String reason,
        UUID lecturerId
) {
}
