package com.saga.be.dto.request;

import com.saga.be.entity.enums.Priority;
import java.time.LocalDate;
import java.util.List;

public record JiraTaskUpdateRequest(
        String title,
        String description,
        Priority priority,
        String priorityId,
        LocalDate dueDate,
        List<String> labels,
        List<String> componentIds
) {

    /**
     * Backward-compatible constructor for existing callers that still use a
     * Jira provider priority id as the third argument.
     */
    public JiraTaskUpdateRequest(
            String title,
            String description,
            String priorityId,
            LocalDate dueDate,
            List<String> labels,
            List<String> componentIds
    ) {
        this(title, description, null, priorityId, dueDate, labels, componentIds);
    }
}
