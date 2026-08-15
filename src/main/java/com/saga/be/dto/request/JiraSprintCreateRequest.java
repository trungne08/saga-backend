package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record JiraSprintCreateRequest(@NotBlank String name, String goal, Instant startDate, Instant endDate) {
}
