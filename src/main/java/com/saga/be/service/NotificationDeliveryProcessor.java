package com.saga.be.service;

import com.saga.be.config.NotificationDeliveryProperties;
import com.saga.be.entity.enums.NotificationDeliveryStatus;
import com.saga.be.repository.NotificationDeliveryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        name = "app.notification.delivery.processing-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryProcessor {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDeliveryClaimService claimService;
    private final FirebaseNotificationDeliveryAdapter deliveryAdapter;
    private final NotificationDeliveryProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeliveryQueued(NotificationDeliveryQueued event) {
        process(event.deliveryId());
    }

    @Scheduled(fixedDelayString = "${app.notification.delivery.retry-delay-ms:60000}")
    public void retryFailedDeliveries() {
        List<UUID> deliveryIds = new ArrayList<>(
                deliveryRepository.findTop100IdsByDeliveryStatusInOrderByCreatedAtAsc(
                        List.of(
                                NotificationDeliveryStatus.PENDING,
                                NotificationDeliveryStatus.FAILED
                        ),
                        PageRequest.of(0, 100)
                )
        );
        LocalDateTime staleBefore = LocalDateTime.now(ZoneOffset.UTC)
                .minus(properties.processingTimeout());
        deliveryRepository.findTop100IdsByProcessingStartedAtBefore(
                NotificationDeliveryStatus.PROCESSING,
                staleBefore,
                PageRequest.of(0, 100)
        ).forEach(deliveryId -> {
            if (claimService.recoverStaleProcessing(deliveryId, staleBefore)) {
                deliveryIds.add(deliveryId);
            }
        });
        deliveryIds.forEach(this::process);
    }

    public void process(UUID deliveryId) {
        claimService.claim(deliveryId).ifPresent(message -> {
            try {
                deliveryAdapter.deliver(message);
                claimService.markSent(deliveryId);
                log.info(
                        "Notification delivery provider=FIREBASE_FCM stage=OUTBOX "
                                + "attempt={} result=SENT",
                        message.attemptNumber()
                );
            } catch (FirebaseNotificationDeliveryUnavailableException exception) {
                claimService.markFailed(deliveryId, "DELIVERY_UNAVAILABLE", false);
                log.warn(
                        "Notification delivery provider=FIREBASE_FCM stage=CONFIGURATION "
                                + "attempt={} result=FAILED category=UNAVAILABLE "
                                + "exceptionClass={}",
                        message.attemptNumber(),
                        exception.getClass().getSimpleName()
                );
            } catch (FirebaseNotificationDeliveryException exception) {
                claimService.markFailed(
                        deliveryId,
                        exception.getCategory(),
                        exception.isInstallationUnusable()
                );
                log.warn(
                        "Notification delivery provider=FIREBASE_FCM stage=SEND "
                                + "attempt={} result=FAILED category={} exceptionClass={}",
                        message.attemptNumber(),
                        exception.getCategory(),
                        exception.getClass().getSimpleName()
                );
            } catch (RuntimeException exception) {
                claimService.markFailed(deliveryId, "UNEXPECTED", false);
                log.warn(
                        "Notification delivery provider=FIREBASE_FCM stage=DELIVERY "
                                + "attempt={} result=FAILED category=UNEXPECTED "
                                + "exceptionClass={}",
                        message.attemptNumber(),
                        exception.getClass().getSimpleName()
                );
            }
        });
    }
}
