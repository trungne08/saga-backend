package com.saga.be.dto.response;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;

public record AuthMeResponse(
        String cognitoSub,
        String email,
        String fullName,
        ApplicationRole applicationRole,
        UUID localProfileId,
        AccountStatus accountStatus
) {
    public static AuthMeResponse from(SagaPrincipal principal) {
        return from(principal, principal.accountStatus());
    }

    public static AuthMeResponse from(SagaPrincipal principal, AccountStatus accountStatus) {
        return new AuthMeResponse(
                principal.cognitoSub(),
                principal.email(),
                principal.fullName(),
                principal.applicationRole(),
                principal.localProfileId(),
                accountStatus
        );
    }
}
