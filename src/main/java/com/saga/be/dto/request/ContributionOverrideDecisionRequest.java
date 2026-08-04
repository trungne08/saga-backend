package com.saga.be.dto.request;

import java.util.UUID;

public record ContributionOverrideDecisionRequest(
        String decision,
        String note,
        UUID adminId
) {}
