package com.saga.be.dto.response;

import com.saga.be.entity.ProjectGroupWeightConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectGroupWeightConfigResponse(
        UUID projectId,
        UUID groupId,
        BigDecimal codeWeight,
        BigDecimal testWeight,
        BigDecimal documentWeight,
        BigDecimal researchWeight,
        String note,
        LocalDateTime updatedAt,
        UUID updatedByProfileId
) {
    public static ProjectGroupWeightConfigResponse from(ProjectGroupWeightConfig config) {
        return new ProjectGroupWeightConfigResponse(
                config.getProject().getId(),
                config.getTeam().getId(),
                config.getCodeWeight(),
                config.getTestWeight(),
                config.getDocumentWeight(),
                config.getResearchWeight(),
                config.getNote(),
                config.getUpdatedAt(),
                config.getUpdatedByProfileId()
        );
    }
}
