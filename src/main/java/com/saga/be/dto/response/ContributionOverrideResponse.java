package com.saga.be.dto.response;

import com.saga.be.entity.enums.PolicyOverrideStatus;
import java.util.UUID;

public record ContributionOverrideResponse(
        UUID requestId,
        UUID studentId,
        double proposedPercentage,
        PolicyOverrideStatus status,
        String message
) {}
