package com.saga.be.dto.response;

import java.util.List;

public record PersonalIntegrationsResponse(
        List<IdentityConnectionResponse> connections
) {
}
