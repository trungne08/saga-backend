package com.saga.be.integration.provider;

public record JiraUserIdentity(
        String accountId,
        String displayName,
        String email
) {
}
