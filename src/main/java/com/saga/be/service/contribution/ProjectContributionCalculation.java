package com.saga.be.service.contribution;

import java.util.List;
import java.util.UUID;

public record ProjectContributionCalculation(
        UUID projectId,
        List<ContributionBreakdown> students
) {
    public ProjectContributionCalculation {
        students = List.copyOf(students);
    }
}
