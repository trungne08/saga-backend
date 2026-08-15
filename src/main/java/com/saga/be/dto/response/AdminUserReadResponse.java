package com.saga.be.dto.response;

import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import java.util.UUID;

/** Safe, local-profile-only view for the global administrator user directory. */
public record AdminUserReadResponse(
        UUID localProfileId,
        ApplicationRole role,
        String fullName,
        String email,
        AccountStatus accountStatus,
        String studentCode
) {
}
