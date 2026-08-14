package com.saga.be.dto.request;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProjectGroupWeightConfigRequest(
        UUID groupId,
        BigDecimal codeWeight,
        BigDecimal documentWeight,
        BigDecimal designWeight,
        @Size(max = 1000) String note
) {
}
