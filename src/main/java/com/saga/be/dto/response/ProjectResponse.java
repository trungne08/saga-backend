package com.saga.be.dto.response;

import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectType;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID teamId,
        String name,
        ProjectTypeSummary projectType
) {
    public static ProjectResponse from(Project project, UUID teamId) {
        return new ProjectResponse(
                project.getId(),
                teamId,
                project.getName(),
                ProjectTypeSummary.from(project.getProjectType())
        );
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
