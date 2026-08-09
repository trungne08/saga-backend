package com.saga.be.dto.response;

import java.time.LocalDateTime;

/** Deliberately excludes actor identity, IP address and old/new raw audit payloads. */
public record AdminAuditLogResponse(
        String id,
        String action,
        String targetEntity,
        LocalDateTime timestamp
) {
}
