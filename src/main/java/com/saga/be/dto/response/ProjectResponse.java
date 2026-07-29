package com.saga.be.dto.response;

import com.saga.be.entity.Project;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID teamId,
        String name
) {
    public static ProjectResponse from(Project project, UUID teamId) {
        return new ProjectResponse(project.getId(), teamId, project.getName());
    }
}
