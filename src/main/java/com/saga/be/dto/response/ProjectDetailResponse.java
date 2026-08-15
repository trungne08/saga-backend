package com.saga.be.dto.response;

import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectType;
import com.saga.be.entity.Team;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectDetailResponse(
        UUID projectId,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        TeamSummary team,
        ProjectTypeSummary projectType
) {
    public static ProjectDetailResponse from(Project project, Team team) {
        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                TeamSummary.from(team),
                ProjectTypeSummary.from(project.getProjectType())
        );
    }

    public record TeamSummary(UUID teamId, String teamName) {
        private static TeamSummary from(Team team) {
            return new TeamSummary(team.getId(), team.getName());
        }
    }

    public record ProjectTypeSummary(UUID projectTypeId, String code, String name) {
        private static ProjectTypeSummary from(ProjectType projectType) {
            if (projectType == null) {
                return null;
            }
            return new ProjectTypeSummary(projectType.getId(), projectType.getCode(), projectType.getName());
        }
    }
}
