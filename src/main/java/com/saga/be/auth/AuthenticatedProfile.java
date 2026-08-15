package com.saga.be.auth;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import java.util.UUID;

public record AuthenticatedProfile(
        String cognitoSub,
        String email,
        String fullName,
        ApplicationRole role,
        UUID localProfileId,
        AccountStatus accountStatus,
        String avatarUrl
) {
    public AuthenticatedProfile(
            String cognitoSub,
            String email,
            String fullName,
            ApplicationRole role,
            UUID localProfileId,
            AccountStatus accountStatus
    ) {
        this(cognitoSub, email, fullName, role, localProfileId, accountStatus, null);
    }
}
