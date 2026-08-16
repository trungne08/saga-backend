package com.saga.be.service;

import com.saga.be.dto.response.NotificationResponse;
import com.saga.be.dto.response.NotificationUnreadCountResponse;
import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.entity.Notification;
import com.saga.be.entity.NotificationDelivery;
import com.saga.be.entity.NotificationBroadcast;
import com.saga.be.entity.enums.NotificationDeliveryStatus;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.FirebaseInstallationRepository;
import com.saga.be.repository.NotificationDeliveryRepository;
import com.saga.be.repository.NotificationRepository;
import com.saga.be.repository.NotificationBroadcastRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_ACTION_URL_LENGTH = 500;

    private final NotificationRepository notificationRepository;
    private final FirebaseInstallationRepository installationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationBroadcastRepository broadcastRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(SagaPrincipal principal, int page, int size) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return notificationRepository.findByRecipientProfileIdAndRecipientRole(
                principal.localProfileId(),
                principal.applicationRole(),
                pageable
        ).map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse unreadCount(SagaPrincipal principal) {
        return new NotificationUnreadCountResponse(
                notificationRepository.countByRecipientProfileIdAndRecipientRoleAndReadAtIsNull(
                        principal.localProfileId(),
                        principal.applicationRole()
                )
        );
    }

    @Transactional
    public NotificationResponse markRead(SagaPrincipal principal, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientProfileIdAndRecipientRole(
                        notificationId,
                        principal.localProfileId(),
                        principal.applicationRole()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"
                ));
        if (notification.getReadAt() == null) {
            notification.setReadAt(nowUtc());
            notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public Notification create(
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            NotificationType type,
            String title,
            String message,
            String actionUrl
    ) {
        return createInternal(
                recipientProfileId,
                recipientRole,
                type,
                title,
                message,
                actionUrl,
                null,
                null
        );
    }

    @Transactional
    public Notification createForBroadcast(
            UUID broadcastId,
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            NotificationType type,
            String title,
            String message,
            String actionUrl
    ) {
        Notification existing = notificationRepository
                .findByBroadcastIdAndRecipientProfileIdAndRecipientRole(
                        broadcastId,
                        recipientProfileId,
                        recipientRole
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        return createInternal(
                recipientProfileId,
                recipientRole,
                type,
                title,
                message,
                actionUrl,
                broadcastRepository.getReferenceById(broadcastId),
                null
        );
    }

    @Transactional
    public Notification createOnceForEvent(
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            NotificationType type,
            String title,
            String message,
            String eventKey
    ) {
        String normalizedEventKey = requireEventKey(eventKey);
        Notification existing = notificationRepository
                .findByRecipientProfileIdAndRecipientRoleAndEventKey(
                        recipientProfileId,
                        recipientRole,
                        normalizedEventKey
                )
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        return createInternal(
                recipientProfileId,
                recipientRole,
                type,
                title,
                message,
                null,
                null,
                normalizedEventKey
        );
    }

    private Notification createInternal(
            UUID recipientProfileId,
            ApplicationRole recipientRole,
            NotificationType type,
            String title,
            String message,
            String actionUrl,
            NotificationBroadcast broadcast,
            String eventKey
    ) {
        Notification notification = Notification.builder()
                .recipientProfileId(recipientProfileId)
                .recipientRole(recipientRole)
                .notificationType(type)
                .title(requireBounded(title, MAX_TITLE_LENGTH, "title"))
                .message(requireBounded(message, MAX_MESSAGE_LENGTH, "message"))
                .actionUrl(optionalBounded(actionUrl, MAX_ACTION_URL_LENGTH, "actionUrl"))
                .broadcast(broadcast)
                .eventKey(eventKey)
                .build();
        notification = notificationRepository.saveAndFlush(notification);

        List<FirebaseInstallation> installations = installationRepository
                .findByOwnerProfileIdAndOwnerRoleAndActiveTrue(
                        recipientProfileId,
                        recipientRole
                );
        for (FirebaseInstallation installation : installations) {
            NotificationDelivery delivery = deliveryRepository.saveAndFlush(
                    NotificationDelivery.builder()
                            .notification(notification)
                            .installation(installation)
                            .deliveryStatus(NotificationDeliveryStatus.PENDING)
                            .attemptCount(0)
                            .build()
            );
            eventPublisher.publishEvent(new NotificationDeliveryQueued(delivery.getId()));
        }
        return notification;
    }

    private String requireBounded(String value, int maxLength, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private String optionalBounded(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private String requireEventKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException("eventKey is invalid");
        }
        return normalized;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
