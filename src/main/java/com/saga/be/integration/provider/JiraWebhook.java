package com.saga.be.integration.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A dynamic webhook owned by the current OAuth application. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraWebhook(
        String id,
        String url,
        String jqlFilter,
        List<String> events,
        // Jira uses offsets such as +0000, so retain this provider value as
        // text instead of coupling webhook discovery to date parsing.
        String expirationDate
) {
}
