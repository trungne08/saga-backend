package com.saga.be.dto.response;

import java.util.UUID;

/**
 * One Team's effective Contribution weight source, for the Lecturer "Theo từng Team" menu.
 * {@code source} is {@code COURSE} (Course mode; every Team shares the Course weights),
 * {@code TEAM} (Team mode, this Team has its own override), or {@code TEAM_INCOMPLETE}
 * (Team mode, but this Team has no override yet — weights are {@code null}).
 */
public record CourseTeamContributionWeightResponse(
        UUID teamId,
        String teamName,
        UUID projectId,
        String projectName,
        UUID projectTypeId,
        String projectTypeCode,
        String projectTypeName,
        String source,
        Double codeWeight,
        Double testWeight,
        Double documentWeight,
        Double researchWeight
) {
}
