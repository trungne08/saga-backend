package com.saga.be.dto.response;

import com.saga.be.entity.enums.IntegrationStatus;
import java.util.UUID;

public record AdminProjectReadResponse(
        UUID id,
        String name,
        String description,
        CourseSummary course,
        JiraSummary jira,
        GitHubSummary gitHub
) {
    public record CourseSummary(UUID id, String courseCode, String name) {
    }

    public record JiraSummary(IntegrationStatus connectionStatus) {
    }

    public record GitHubSummary(long repositoryCount, long activeRepositoryCount) {
    }
}
