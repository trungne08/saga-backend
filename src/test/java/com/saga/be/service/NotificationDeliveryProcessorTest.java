package com.saga.be.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.config.NotificationDeliveryProperties;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.NotificationDeliveryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryProcessorTest {

    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private NotificationDeliveryClaimService claimService;
    @Mock private FirebaseNotificationDeliveryAdapter deliveryAdapter;
    private NotificationDeliveryProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new NotificationDeliveryProcessor(
                deliveryRepository, claimService, deliveryAdapter,
                new NotificationDeliveryProperties()
        );
    }

    @Test
    void successfulDeliveryIsMarkedSent() {
        UUID deliveryId = UUID.randomUUID();
        FirebaseNotificationMessage message = message();
        when(claimService.claim(deliveryId)).thenReturn(Optional.of(message));

        processor.process(deliveryId);

        verify(deliveryAdapter).deliver(message);
        verify(claimService).markSent(deliveryId);
        verify(claimService, never()).markFailed(deliveryId, "DELIVERY_UNAVAILABLE", false);
    }

    @Test
    void unavailableProviderMarksOnlyDeliveryFailed() {
        UUID deliveryId = UUID.randomUUID();
        FirebaseNotificationMessage message = message();
        when(claimService.claim(deliveryId)).thenReturn(Optional.of(message));
        org.mockito.Mockito.doThrow(new FirebaseNotificationDeliveryUnavailableException())
                .when(deliveryAdapter).deliver(message);

        processor.process(deliveryId);

        verify(claimService).markFailed(deliveryId, "DELIVERY_UNAVAILABLE", false);
        verify(claimService, never()).markSent(deliveryId);
    }

    @Test
    void unregisteredInstallationIsDeactivatedByClaimServiceContract() {
        UUID deliveryId = UUID.randomUUID();
        FirebaseNotificationMessage message = message();
        when(claimService.claim(deliveryId)).thenReturn(Optional.of(message));
        org.mockito.Mockito.doThrow(new FirebaseNotificationDeliveryException(
                "UNREGISTERED", true, new IllegalStateException("provider rejected FID")
        )).when(deliveryAdapter).deliver(message);

        processor.process(deliveryId);

        verify(claimService).markFailed(deliveryId, "UNREGISTERED", true);
    }

    private FirebaseNotificationMessage message() {
        return new FirebaseNotificationMessage(
                UUID.randomUUID(), "fid-placeholder",
                NotificationType.COURSE_MEMBERSHIP_ADDED,
                "title", "message", null, 1
        );
    }
}
