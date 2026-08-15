package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record TeamContributionMemberResponse(
        UUID studentId,
        String fullName,
        String studentCode,
        double codeContributionScore,
        double documentContributionScore,
        double codeContributionPercentage,
        double documentContributionPercentage,
        double testContributionScore,
        double testContributionPercentage,
        double researchContributionScore,
        double researchContributionPercentage,
        double peerReviewScore,
        double taskContributionScore,
        double taskContributionPercentage,
        double sliceScore,
        double sliceContributionPercentage,
        double finalContributionPercentage,
        int evidenceCount,
        List<SprintContributionBreakdown> sprintBreakdowns,
        List<ContributionWarning> warnings
) {
    public record SprintContributionBreakdown(
            UUID sprintId,
            String sprintName,
            double taskScore,
            double retrospectiveMultiplier,
            double adjustedTaskScore,
            int peerReviewCount,
            double sliceScore,
            double sliceContributionPercentage,
            double contributionPercentage
    ) {}
}
