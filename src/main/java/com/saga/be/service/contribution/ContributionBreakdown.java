package com.saga.be.service.contribution;

import java.math.BigDecimal;
import java.util.UUID;

public record ContributionBreakdown(
        UUID studentId,
        BigDecimal codeScore,
        BigDecimal testScore,
        BigDecimal documentScore,
        BigDecimal researchScore,
        BigDecimal adjustedSprintScore,
        BigDecimal peerCoefficient,
        BigDecimal codeContributionPercent,
        BigDecimal testContributionPercent,
        BigDecimal documentContributionPercent,
        BigDecimal researchContributionPercent,
        BigDecimal taskContributionPercent,
        BigDecimal rawContribution,
        BigDecimal adjustedContribution,
        BigDecimal finalContribution
) {
}
