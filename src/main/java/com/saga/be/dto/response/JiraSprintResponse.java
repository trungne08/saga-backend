package com.saga.be.dto.response;

import com.saga.be.entity.Sprint;
import java.time.LocalDateTime;
import java.util.UUID;

public record JiraSprintResponse(UUID id, String externalSprintId, String name, String state, String goal,
        LocalDateTime startDate, LocalDateTime endDate, LocalDateTime completeDate) {
    public static JiraSprintResponse from(Sprint sprint) {
        return new JiraSprintResponse(sprint.getId(), sprint.getExternalSprintId(), sprint.getName(), sprint.getState(),
                sprint.getGoal(), sprint.getStartDate(), sprint.getEndDate(), sprint.getCompleteDate());
    }
}
