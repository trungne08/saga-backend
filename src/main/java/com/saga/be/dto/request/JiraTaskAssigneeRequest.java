package com.saga.be.dto.request;

import java.util.UUID;

public record JiraTaskAssigneeRequest(UUID assigneeId, Boolean unassign) {

    public JiraTaskAssigneeRequest {
        unassign = Boolean.TRUE.equals(unassign);
    }
}
