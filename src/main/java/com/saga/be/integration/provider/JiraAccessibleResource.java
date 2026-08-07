package com.saga.be.integration.provider;

public record JiraAccessibleResource(
        String cloudId,
        String name,
        String siteUrl,
        java.util.Set<String> scopes
) implements java.io.Serializable {

    public JiraAccessibleResource {
        scopes = scopes == null ? java.util.Set.of() : java.util.Set.copyOf(scopes);
    }

    /** Compatibility constructor for callers that do not need site-scope data. */
    public JiraAccessibleResource(String cloudId, String name, String siteUrl) {
        this(cloudId, name, siteUrl, java.util.Set.of());
    }
}
