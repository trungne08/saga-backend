package com.saga.be.dto.request;

import com.saga.be.entity.enums.NotificationBroadcastAudience;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationBroadcastRequest(
        @NotNull NotificationBroadcastAudience audience,
        @NotBlank @Size(max = 160) @Pattern(regexp = "^[^<>]*$") String title,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^[^<>]*$") String message
) {
}
