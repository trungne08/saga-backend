package com.saga.be.dto.request;

import java.time.Instant;

public record JiraSprintUpdateRequest(String name, String goal, Instant startDate, Instant endDate) {
}
