package com.saga.be.repository;

import com.saga.be.entity.NotificationBroadcast;
import com.saga.be.security.ApplicationRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationBroadcastRepository extends JpaRepository<NotificationBroadcast, UUID> {

    Optional<NotificationBroadcast> findBySenderProfileIdAndSenderRoleAndIdempotencyKey(
            UUID senderProfileId,
            ApplicationRole senderRole,
            String idempotencyKey
    );
}
