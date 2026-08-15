package com.saga.be.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProjectDashboardStatsResponse(
        UUID projectId, Instant generatedAt, Tasks tasks, GitHub github
) {
    public record Tasks(long total, long completed, long incomplete, BigDecimal completionPercentage) {}
    public record GitHub(long repositoryCount, long commitCount, long pullRequestCount) {}
}
