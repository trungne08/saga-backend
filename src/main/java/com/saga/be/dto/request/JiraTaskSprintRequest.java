package com.saga.be.dto.request;

import java.util.UUID;

public record JiraTaskSprintRequest(UUID sprintId, Boolean backlog) {
    public JiraTaskSprintRequest {
        backlog = Boolean.TRUE.equals(backlog);
    }
}
