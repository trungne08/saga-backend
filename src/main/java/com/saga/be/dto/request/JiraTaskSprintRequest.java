package com.saga.be.dto.request;

import java.util.UUID;

public record JiraTaskSprintRequest(UUID sprintId, boolean backlog) {
}
