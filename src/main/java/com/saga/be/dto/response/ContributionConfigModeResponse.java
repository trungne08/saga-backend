package com.saga.be.dto.response;

import com.saga.be.entity.enums.ContributionConfigMode;
import java.util.UUID;

public record ContributionConfigModeResponse(
        UUID courseId,
        ContributionConfigMode mode
) {
}
