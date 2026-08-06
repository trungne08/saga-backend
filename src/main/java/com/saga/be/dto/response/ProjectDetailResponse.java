package com.saga.be.dto.response;

import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID projectId,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        TeamSummary team
) {
    public static ProjectDetailResponse from(Project project, Team team) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                TeamSummary.from(team)
        );
    }

    public record TeamSummary(UUID teamId, String teamName) {
        private static TeamSummary from(Team team) {
            return new TeamSummary(team.getId(), team.getName());
        }
    }
}
