package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JiraProjectLinkRequest(
        @NotBlank @Size(max = 255) String cloudId,
        @NotBlank @Size(max = 255) String jiraProjectId
) {
}
