package com.saga.be.repository;

import com.saga.be.entity.AiAgentDelegationContext;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAgentDelegationContextRepository
        extends JpaRepository<AiAgentDelegationContext, UUID> {

    Optional<AiAgentDelegationContext> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBefore(LocalDateTime threshold);
}
