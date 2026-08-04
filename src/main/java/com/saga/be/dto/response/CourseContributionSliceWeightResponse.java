package com.saga.be.dto.response;

import java.util.UUID;

public record CourseContributionSliceWeightResponse(
        UUID courseId,
        String courseCode,
        String courseName,
        double codeWeight,
        double documentWeight,
        double designWeight
) {
}
