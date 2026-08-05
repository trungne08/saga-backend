package com.saga.be.dto.response;

import com.saga.be.entity.Sprint;
import java.time.LocalDateTime;
import java.util.UUID;

public record SprintSummaryResponse(
        UUID sprintId,
        String sprintName,
        String externalSprintId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String goal
) {
    public static SprintSummaryResponse from(Sprint sprint) {
        return new SprintSummaryResponse(
                sprint.getId(),
                sprint.getName(),
                sprint.getExternalSprintId(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                sprint.getGoal()
        );
    }
}
