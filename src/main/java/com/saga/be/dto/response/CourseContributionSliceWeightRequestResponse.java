package com.saga.be.dto.response;

import com.saga.be.entity.enums.PolicyOverrideStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record CourseContributionSliceWeightRequestResponse(
        UUID requestId,
        UUID courseId,
        String courseCode,
        String courseName,
        UUID lecturerId,
        String lecturerName,
        double proposedCodeWeight,
        double proposedDocumentWeight,
        double proposedDesignWeight,
        String reason,
        PolicyOverrideStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
