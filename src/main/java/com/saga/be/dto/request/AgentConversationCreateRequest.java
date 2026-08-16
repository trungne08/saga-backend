package com.saga.be.dto.request;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AgentConversationCreateRequest(
        @Size(max = 160) String title,
        UUID courseId
) {
}

