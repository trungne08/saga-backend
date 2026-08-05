package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JiraTaskCreateRequest(
        @NotBlank String title,
        @NotBlank String issueTypeId,
        String description,
        String priorityId,
        LocalDate dueDate,
        List<String> labels,
        List<String> componentIds,
        UUID assigneeId
) {
}
