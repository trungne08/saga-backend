package com.saga.be.dto.response;

import com.saga.be.entity.FirebaseInstallation;
import java.time.LocalDateTime;
import java.util.UUID;

public record FirebaseInstallationResponse(
        UUID id,
        boolean active,
        LocalDateTime lastRegisteredAt,
        LocalDateTime revokedAt
) {
    public static FirebaseInstallationResponse from(FirebaseInstallation installation) {
        return new FirebaseInstallationResponse(
                installation.getId(),
                installation.isActive(),
                installation.getLastRegisteredAt(),
                installation.getRevokedAt()
        );
    }
}
