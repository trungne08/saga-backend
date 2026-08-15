package com.saga.be.dto.request;

import java.util.UUID;

public record ContributionOverrideRequest(
        UUID studentId,
        Double proposedPercentage,
        String reason,
        UUID lecturerId
) {}
