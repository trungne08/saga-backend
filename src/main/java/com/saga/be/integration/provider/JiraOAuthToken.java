package com.saga.be.integration.provider;

import java.time.Instant;
import java.util.Set;

public record JiraOAuthToken(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        Set<String> scopes
) {
}
