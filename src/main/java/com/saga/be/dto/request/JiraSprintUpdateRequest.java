package com.saga.be.dto.request;

import java.time.LocalDateTime;

public record JiraSprintUpdateRequest(String name, String goal, LocalDateTime startDate, LocalDateTime endDate) {
}
