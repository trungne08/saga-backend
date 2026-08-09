package com.saga.be.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActiveSemesterSettingRequest(@NotNull UUID semesterId) {
}
