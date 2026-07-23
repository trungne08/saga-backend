package com.saga.be.auth;

import com.saga.be.security.ApplicationRole;

public record AuthenticatedIdentity(
        String cognitoSub,
        String email,
        String fullName,
        ApplicationRole role
) {
}
