package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JiraTaskTransitionRequest(@NotBlank String transitionId) {
}
