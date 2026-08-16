package com.saga.be.dto.response;

import com.saga.be.entity.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record ProjectTypeResponse(
        UUID projectTypeId,
        @Schema(allowableValues = {
                ProjectType.CODE_DESIGN_ARCHITECTURE,
                ProjectType.CODE_RESEARCH,
                ProjectType.CODE_TESTER,
                ProjectType.CODE_DOCUMENT
        })
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
