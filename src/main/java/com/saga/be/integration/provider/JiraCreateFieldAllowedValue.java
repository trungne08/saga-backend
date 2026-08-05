package com.saga.be.integration.provider;

/**
 * The non-sensitive identity and display attributes exposed by Jira create
 * metadata. Raw provider objects are intentionally not retained.
 */
public record JiraCreateFieldAllowedValue(
        String id,
        String value,
        String name
) {
}
