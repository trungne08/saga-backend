package com.saga.be.repository;

import com.saga.be.entity.Notification;
import com.saga.be.security.ApplicationRole;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientProfileIdAndRecipientRole(
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            Pageable pageable
    );

    long countByRecipientProfileIdAndRecipientRoleAndReadAtIsNull(
            UUID recipientProfileId,
            ApplicationRole recipientRole
    );

    Optional<Notification> findByIdAndRecipientProfileIdAndRecipientRole(
            UUID id,
            UUID recipientProfileId,
            ApplicationRole recipientRole
    );

    Optional<Notification> findByBroadcastIdAndRecipientProfileIdAndRecipientRole(
            UUID broadcastId,
            UUID recipientProfileId,
            ApplicationRole recipientRole
    );

    long countByBroadcastId(UUID broadcastId);

    Optional<Notification> findByRecipientProfileIdAndRecipientRoleAndEventKey(
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            String eventKey
    );
}
