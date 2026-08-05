package com.saga.be.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record JiraTaskEstimationRequest(@NotNull @PositiveOrZero Integer value) {
}
