package com.saga.be.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.saga.be.entity.enums.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FirebaseAdminNotificationDeliveryAdapterTest {

    @Mock private FirebaseMessaging firebaseMessaging;

    @Test
    void sendsMinimalNotificationAndDataMessageWithoutRealFirebase() throws Exception {
        FirebaseAdminNotificationDeliveryAdapter adapter =
                new FirebaseAdminNotificationDeliveryAdapter(firebaseMessaging);

        adapter.deliver(message());

        verify(firebaseMessaging).send(any(Message.class));
    }

    @Test
    void unregisteredProviderResponseIsClassifiedAsUnusableInstallation() throws Exception {
        FirebaseMessagingException providerException =
                org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(providerException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebaseMessaging.send(any(Message.class))).thenThrow(providerException);

        FirebaseAdminNotificationDeliveryAdapter adapter =
                new FirebaseAdminNotificationDeliveryAdapter(firebaseMessaging);

        assertThatThrownBy(() -> adapter.deliver(message()))
                .isInstanceOf(FirebaseNotificationDeliveryException.class)
                .satisfies(error -> {
                    FirebaseNotificationDeliveryException deliveryError =
                            (FirebaseNotificationDeliveryException) error;
                    org.assertj.core.api.Assertions.assertThat(deliveryError.getCategory())
                            .isEqualTo("UNREGISTERED");
                    org.assertj.core.api.Assertions.assertThat(deliveryError.isInstallationUnusable())
                            .isTrue();
                });
    }

    private FirebaseNotificationMessage message() {
        return new FirebaseNotificationMessage(
                UUID.randomUUID(), "fid-placeholder",
                NotificationType.COURSE_MEMBERSHIP_ADDED,
                "title", "message", "/courses/example", 1
        );
    }
}
