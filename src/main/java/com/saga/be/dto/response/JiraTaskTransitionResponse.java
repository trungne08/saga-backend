package com.saga.be.dto.response;

public record JiraTaskTransitionResponse(
        String transitionId,
        String name,
        String targetStatusId,
        String targetStatusName
) {
}
