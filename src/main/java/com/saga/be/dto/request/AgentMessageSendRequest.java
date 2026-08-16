package com.saga.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AgentMessageSendRequest(
        @NotBlank @Size(max = 8000) String content,
        UUID courseId
) {
}

