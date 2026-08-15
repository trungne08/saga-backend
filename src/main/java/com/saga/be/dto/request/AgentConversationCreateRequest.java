package com.saga.be.dto.request;

import jakarta.validation.constraints.Size;

public record AgentConversationCreateRequest(@Size(max = 160) String title) {
}

