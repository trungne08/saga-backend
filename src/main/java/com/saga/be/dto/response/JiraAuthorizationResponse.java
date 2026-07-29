package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record JiraAuthorizationResponse(
        UUID projectId,
        List<JiraSiteResponse> sites
) {
}
