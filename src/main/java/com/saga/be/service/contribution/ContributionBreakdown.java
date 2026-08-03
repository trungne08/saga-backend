package com.saga.be.service.contribution;

import java.math.BigDecimal;
import java.util.UUID;

public record ContributionBreakdown(
        UUID studentId,
        BigDecimal codeScore,
        BigDecimal documentScore,
        BigDecimal designScore,
        BigDecimal adjustedSprintScore,
        BigDecimal peerCoefficient,
        BigDecimal codeContributionPercent,
        BigDecimal documentContributionPercent,
        BigDecimal designContributionPercent,
        BigDecimal taskContributionPercent,
        BigDecimal rawContribution,
        BigDecimal adjustedContribution,
        BigDecimal finalContribution
) {
}
