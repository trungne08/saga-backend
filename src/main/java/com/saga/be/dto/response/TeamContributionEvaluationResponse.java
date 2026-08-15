package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TeamContributionEvaluationResponse(
        UUID teamId,
        UUID projectId,
        LocalDateTime evaluatedAt,
        List<TeamContributionMemberResponse> members
) {}
