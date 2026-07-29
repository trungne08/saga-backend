package com.saga.be.integration.provider;

public record GitHubUserIdentity(
        long id,
        String login,
        String name,
        String email
) {
}
