package com.saga.be.repository;

import com.saga.be.entity.AiAgentConversationScope;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAgentConversationScopeRepository
        extends JpaRepository<AiAgentConversationScope, UUID> {

    Optional<AiAgentConversationScope> findByConversationId(UUID conversationId);

    List<AiAgentConversationScope> findByConversationIdIn(Collection<UUID> conversationIds);
}
