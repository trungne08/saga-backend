package com.saga.be.security;

import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.enums.AccountStatus;
import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

public record SagaPrincipal(
        String cognitoSub,
        String email,
        String fullName,
        ApplicationRole applicationRole,
        UUID localProfileId,
        AccountStatus accountStatus
) implements Principal, Serializable {

    public static SagaPrincipal from(AuthenticatedProfile profile) {
        return new SagaPrincipal(
                profile.cognitoSub(),
                profile.email(),
                profile.fullName(),
                profile.role(),
                profile.localProfileId(),
                profile.accountStatus()
        );
    }

    @Override
    public String getName() {
        return cognitoSub;
    }
}
