package com.saga.be.dto.response;

import java.time.OffsetDateTime;

public record AdminSystemStatsResponse(
        long totalProfiles,
        long totalCourses,
        long totalTeams,
        long totalProjects,
        long activeJiraBoards,
        long activeGitRepositories,
        OffsetDateTime generatedAt
) {
}
