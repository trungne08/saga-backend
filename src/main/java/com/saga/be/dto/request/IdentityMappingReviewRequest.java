package com.saga.be.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IdentityMappingReviewRequest(
        @NotNull Action action,
        UUID correctedStudentId
) {
    public enum Action {
        APPROVE,
        REJECT,
        CORRECT
    }
}
