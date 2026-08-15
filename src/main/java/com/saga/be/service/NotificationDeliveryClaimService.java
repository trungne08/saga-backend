package com.saga.be.service;

import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.entity.Notification;
import com.saga.be.entity.NotificationDelivery;
import com.saga.be.entity.enums.NotificationDeliveryStatus;
import com.saga.be.repository.FirebaseInstallationRepository;
import com.saga.be.repository.NotificationDeliveryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryClaimService {

    private static final int MAX_ATTEMPTS = 5;

    private final NotificationDeliveryRepository deliveryRepository;
    private final FirebaseInstallationRepository installationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FirebaseNotificationMessage> claim(UUID deliveryId) {
        NotificationDelivery delivery = deliveryRepository.findForUpdateById(deliveryId)
                .orElse(null);
        if (delivery == null
                || delivery.getDeliveryStatus() == NotificationDeliveryStatus.SENT
                || delivery.getDeliveryStatus() == NotificationDeliveryStatus.PROCESSING
                || delivery.getAttemptCount() >= MAX_ATTEMPTS
                || !delivery.getInstallation().isActive()) {
            return Optional.empty();
        }
        LocalDateTime claimedAt = nowUtc();
        delivery.setDeliveryStatus(NotificationDeliveryStatus.PROCESSING);
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastAttemptAt(claimedAt);
        delivery.setProcessingStartedAt(claimedAt);
        delivery.setFailureCode(null);
        deliveryRepository.saveAndFlush(delivery);

        Notification notification = delivery.getNotification();
        return Optional.of(new FirebaseNotificationMessage(
                notification.getId(),
                delivery.getInstallation().getFirebaseInstallationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                delivery.getAttemptCount()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStaleProcessing(UUID deliveryId, LocalDateTime staleBefore) {
        NotificationDelivery delivery = deliveryRepository.findForUpdateById(deliveryId)
                .orElse(null);
        if (delivery == null
                || delivery.getDeliveryStatus() != NotificationDeliveryStatus.PROCESSING
                || delivery.getProcessingStartedAt() == null
                || !delivery.getProcessingStartedAt().isBefore(staleBefore)) {
            return false;
        }
        delivery.setProcessingStartedAt(null);
        delivery.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.setFailureCode("MAX_ATTEMPTS_EXHAUSTED");
            deliveryRepository.saveAndFlush(delivery);
            return false;
        }
        delivery.setFailureCode("PROCESSING_TIMEOUT");
        deliveryRepository.saveAndFlush(delivery);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID deliveryId) {
        deliveryRepository.findForUpdateById(deliveryId).ifPresent(delivery -> {
            if (delivery.getDeliveryStatus() == NotificationDeliveryStatus.PROCESSING) {
                delivery.setDeliveryStatus(NotificationDeliveryStatus.SENT);
                delivery.setSentAt(nowUtc());
                delivery.setProcessingStartedAt(null);
                delivery.setFailureCode(null);
                deliveryRepository.saveAndFlush(delivery);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID deliveryId, String failureCode, boolean deactivateInstallation) {
        deliveryRepository.findForUpdateById(deliveryId).ifPresent(delivery -> {
            if (delivery.getDeliveryStatus() == NotificationDeliveryStatus.PROCESSING) {
                delivery.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
                delivery.setProcessingStartedAt(null);
                delivery.setFailureCode(safeFailureCode(failureCode));
                deliveryRepository.saveAndFlush(delivery);
                if (deactivateInstallation) {
                    FirebaseInstallation installation = delivery.getInstallation();
                    installation.setActive(false);
                    installation.setRevokedAt(nowUtc());
                    installationRepository.save(installation);
                }
            }
        });
    }

    private String safeFailureCode(String value) {
        String normalized = value == null ? "DELIVERY_FAILED" : value.trim();
        return normalized.length() <= 64
                ? normalized
                : normalized.substring(0, 64);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
