package com.saga.be.integration.provider;

public record JiraAccessibleResource(
        String cloudId,
        String name,
        String siteUrl
) implements java.io.Serializable {
}
