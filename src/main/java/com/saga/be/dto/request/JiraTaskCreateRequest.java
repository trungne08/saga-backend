package com.saga.be.dto.request;

import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JiraTaskCreateRequest(
        @NotBlank String title,
        TaskType type,
        String issueTypeId,
        Priority priority,
        String priorityId,
        String description,
        LocalDate dueDate,
        List<String> labels,
        List<String> componentIds,
        UUID assigneeId
) {

    /**
     * Backward-compatible constructor for clients/tests that still send Jira
     * provider IDs as the second and fourth arguments.
     */
    public JiraTaskCreateRequest(
            String title,
            String issueTypeId,
            String description,
            String priorityId,
            LocalDate dueDate,
            List<String> labels,
            List<String> componentIds,
            UUID assigneeId
    ) {
        this(title, null, issueTypeId, null, priorityId, description, dueDate, labels, componentIds, assigneeId);
    }
}
