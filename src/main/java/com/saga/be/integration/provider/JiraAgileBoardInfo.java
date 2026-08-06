package com.saga.be.integration.provider;

/** Safe, provider-neutral representation of a Jira Agile board. */
public record JiraAgileBoardInfo(String boardId, String name, String type) {
}
