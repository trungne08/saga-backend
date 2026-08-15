package com.saga.be.service;

import com.saga.be.entity.enums.NotificationType;
import java.util.UUID;

public record FirebaseNotificationMessage(
        UUID notificationId,
        String firebaseInstallationId,
        NotificationType type,
        String title,
        String message,
        String actionUrl,
        int attemptNumber
) {
}
