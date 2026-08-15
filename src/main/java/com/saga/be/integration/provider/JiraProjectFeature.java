package com.saga.be.integration.provider;

/** Safe, machine-readable subset of a Jira project feature. */
public record JiraProjectFeature(String feature, String state) {
}
