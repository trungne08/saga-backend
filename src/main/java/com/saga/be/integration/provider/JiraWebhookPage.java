package com.saga.be.integration.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Jira's PageBeanWebhook response from GET /rest/api/3/webhook. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraWebhookPage(
        Integer startAt,
        Integer maxResults,
        Integer total,
        Boolean isLast,
        List<JiraWebhook> values
) {
}
