package com.saga.be.dto.response;

import com.saga.be.integration.provider.JiraAccessibleResource;

public record JiraSiteResponse(
        String cloudId,
        String name,
        String siteUrl
) {
    public static JiraSiteResponse from(JiraAccessibleResource resource) {
        return new JiraSiteResponse(
                resource.cloudId(),
                resource.name(),
                resource.siteUrl()
        );
    }
}
