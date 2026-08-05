package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record JiraSprintCreateRequest(@NotBlank String name, String goal, LocalDateTime startDate, LocalDateTime endDate) {
}
