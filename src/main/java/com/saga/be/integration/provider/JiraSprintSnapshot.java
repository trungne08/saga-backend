package com.saga.be.integration.provider;

import java.time.LocalDateTime;

public record JiraSprintSnapshot(
        String id,
        String name,
        String state,
        String goal,
        LocalDateTime startDate,
        LocalDateTime endDate,
        LocalDateTime completeDate,
        String originBoardId
) {
}
