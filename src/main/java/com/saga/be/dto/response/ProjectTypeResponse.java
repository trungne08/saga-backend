package com.saga.be.dto.response;

import com.saga.be.entity.ProjectType;
import java.util.UUID;

public record ProjectTypeResponse(
        UUID projectTypeId,
        String code,
        String name,
        String description,
        String criteriaConfig
) {
    public static ProjectTypeResponse from(ProjectType projectType) {
        return new ProjectTypeResponse(
                projectType.getId(),
                projectType.getCode(),
                projectType.getName(),
                projectType.getDescription(),
                projectType.getCriteriaConfig()
        );
    }
}
