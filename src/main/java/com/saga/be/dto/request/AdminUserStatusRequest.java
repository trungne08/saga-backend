package com.saga.be.dto.request;

import com.saga.be.entity.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AdminUserStatusRequest(@NotNull AccountStatus status) {
}
