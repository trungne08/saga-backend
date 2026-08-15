package com.saga.be.dto.response;

import com.saga.be.entity.enums.ContributionConfigMode;
import java.util.List;
import java.util.UUID;

public record CourseTeamContributionWeightsResponse(
        UUID courseId,
        ContributionConfigMode mode,
        List<CourseTeamContributionWeightResponse> teams
) {
}
