package com.saga.be.security;

import com.saga.be.entity.enums.AccountStatus;
import java.util.UUID;

/** Internal after-commit signal that a Student or Lecturer is no longer ACTIVE. */
public record AccountDisabledEvent(
        ApplicationRole applicationRole,
        UUID localProfileId,
        AccountStatus accountStatus
) {
}
