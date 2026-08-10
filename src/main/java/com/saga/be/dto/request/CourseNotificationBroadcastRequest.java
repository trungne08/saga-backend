package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CourseNotificationBroadcastRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> courseIds,
        @NotBlank @Size(max = 160) @Pattern(regexp = "^[^<>]*$") String title,
        @NotBlank @Size(max = 1000) @Pattern(regexp = "^[^<>]*$") String message
) {
}
