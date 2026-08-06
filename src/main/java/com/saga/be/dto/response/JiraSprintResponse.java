package com.saga.be.dto.response;

import com.saga.be.entity.Sprint;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

public record JiraSprintResponse(UUID id, String externalSprintId, String name, String state, String goal,
        Instant startDate, Instant endDate, Instant completeDate) {
    public static JiraSprintResponse from(Sprint sprint) {
        return new JiraSprintResponse(sprint.getId(), sprint.getExternalSprintId(), sprint.getName(), sprint.getState(),
                sprint.getGoal(), instant(sprint.getStartDate()), instant(sprint.getEndDate()), instant(sprint.getCompleteDate()));
    }
    private static Instant instant(java.time.LocalDateTime value) { return value == null ? null : value.toInstant(ZoneOffset.UTC); }
}
