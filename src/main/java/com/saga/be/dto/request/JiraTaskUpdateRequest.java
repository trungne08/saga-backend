package com.saga.be.dto.request;

import java.time.LocalDate;
import java.util.List;

public record JiraTaskUpdateRequest(
        String title,
        String description,
        String priorityId,
        LocalDate dueDate,
        List<String> labels,
        List<String> componentIds
) {
}
