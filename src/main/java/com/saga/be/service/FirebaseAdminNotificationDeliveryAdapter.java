package com.saga.be.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;

public class FirebaseAdminNotificationDeliveryAdapter
        implements FirebaseNotificationDeliveryAdapter {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseAdminNotificationDeliveryAdapter(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public void deliver(FirebaseNotificationMessage message) {
        Message.Builder builder = Message.builder()
                .setFid(message.firebaseInstallationId())
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.message())
                        .build())
                .putData("notificationId", message.notificationId().toString())
                .putData("type", message.type().name())
                .putData("title", message.title())
                .putData("message", message.message());
        if (message.actionUrl() != null) {
            builder.putData("actionUrl", message.actionUrl());
        }
        try {
            firebaseMessaging.send(builder.build());
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode errorCode = exception.getMessagingErrorCode();
            String category = errorCode == null ? "PROVIDER" : errorCode.name();
            throw new FirebaseNotificationDeliveryException(
                    category,
                    errorCode == MessagingErrorCode.UNREGISTERED,
                    exception
            );
        } catch (RuntimeException exception) {
            throw new FirebaseNotificationDeliveryException(
                    "MESSAGE_BUILD",
                    false,
                    exception
            );
        }
    }
}
