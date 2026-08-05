package com.saga.be.integration.provider;

public record JiraTransition(String id, String name, String targetStatusId, String targetStatusName) {
}
