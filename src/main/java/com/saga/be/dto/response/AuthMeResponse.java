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
        AccountStatus accountStatus,
        String avatarUrl,
        String studentCode
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
                accountStatus,
                principal.avatarUrl(),
                null
        );
    }

    public static AuthMeResponse from(
            SagaPrincipal principal,
            AccountStatus accountStatus,
            String fullName,
            String avatarUrl,
            String studentCode
    ) {
        return new AuthMeResponse(
                principal.cognitoSub(),
                principal.email(),
                fullName,
                principal.applicationRole(),
                principal.localProfileId(),
                accountStatus,
                avatarUrl,
                studentCode
        );
    }

    public static AuthMeResponse from(
            String cognitoSub,
            String email,
            String fullName,
            ApplicationRole applicationRole,
            UUID localProfileId,
            AccountStatus accountStatus,
            String avatarUrl,
            String studentCode
    ) {
        return new AuthMeResponse(
                cognitoSub,
                email,
                fullName,
                applicationRole,
                localProfileId,
                accountStatus,
                avatarUrl,
                studentCode
        );
    }
}
