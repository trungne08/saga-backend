package com.saga.be.dto.response;

import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import java.time.LocalDateTime;

public record IdentityConnectionResponse(
        IntegrationProvider provider,
        IdentityMappingStatus status,
        String displayName,
        String email,
        LocalDateTime verifiedAt,
        LocalDateTime disconnectedAt
) {
    public static IdentityConnectionResponse from(IdentityMap mapping) {
        return new IdentityConnectionResponse(
                mapping.getProvider(),
                mapping.getMappingStatus(),
                mapping.getExternalUsername(),
                mapping.getExternalEmail(),
                mapping.getVerifiedAt(),
                mapping.getDisconnectedAt()
        );
    }
}
