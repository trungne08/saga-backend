package com.saga.be.service;

import com.saga.be.entity.NotificationBroadcast;
import com.saga.be.entity.enums.NotificationBroadcastAudience;
import com.saga.be.entity.enums.NotificationBroadcastStatus;
import com.saga.be.repository.NotificationBroadcastRepository;
import com.saga.be.repository.NotificationDeliveryRepository;
import com.saga.be.repository.NotificationRepository;
import com.saga.be.security.ApplicationRole;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastPersistenceService {

    private final NotificationBroadcastRepository broadcastRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationBroadcast claim(
            UUID senderProfileId,
            ApplicationRole senderRole,
            NotificationBroadcastAudience audience,
            String title,
            String message,
            String idempotencyKey,
            String fingerprint
    ) {
        NotificationBroadcast existing = broadcastRepository
                .findBySenderProfileIdAndSenderRoleAndIdempotencyKey(
                        senderProfileId, senderRole, idempotencyKey
                )
                .orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency-Key was already used for a different notification broadcast"
                );
            }
            return existing;
        }
        return broadcastRepository.saveAndFlush(NotificationBroadcast.builder()
                .senderProfileId(senderProfileId)
                .senderRole(senderRole)
                .audience(audience)
                .title(title)
                .message(message)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .status(NotificationBroadcastStatus.PROCESSING)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationBroadcast complete(UUID broadcastId) {
        NotificationBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new IllegalStateException("Notification broadcast disappeared"));
        long notifications = notificationRepository.countByBroadcastId(broadcastId);
        long deliveries = deliveryRepository.countByNotificationBroadcastId(broadcastId);
        broadcast.setRecipientCount(Math.toIntExact(notifications));
        broadcast.setNotificationCount(Math.toIntExact(notifications));
        broadcast.setDeliveryQueuedCount(Math.toIntExact(deliveries));
        broadcast.setStatus(NotificationBroadcastStatus.COMPLETED);
        broadcast.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        return broadcastRepository.saveAndFlush(broadcast);
    }
}
