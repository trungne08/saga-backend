package com.saga.be.dto.request;

import java.util.UUID;

public record JiraTaskAssigneeRequest(UUID assigneeId, boolean unassign) {
}
