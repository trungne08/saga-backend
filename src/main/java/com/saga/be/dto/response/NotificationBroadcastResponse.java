package com.saga.be.dto.response;

import com.saga.be.entity.NotificationBroadcast;
import com.saga.be.entity.enums.NotificationBroadcastAudience;
import com.saga.be.entity.enums.NotificationBroadcastStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationBroadcastResponse(
        UUID broadcastId,
        NotificationBroadcastAudience audience,
        NotificationBroadcastStatus status,
        int recipientCount,
        int notificationCount,
        int deliveryQueuedCount,
        LocalDateTime completedAt
) {
    public static NotificationBroadcastResponse from(NotificationBroadcast broadcast) {
        return new NotificationBroadcastResponse(
                broadcast.getId(),
                broadcast.getAudience(),
                broadcast.getStatus(),
                broadcast.getRecipientCount(),
                broadcast.getNotificationCount(),
                broadcast.getDeliveryQueuedCount(),
                broadcast.getCompletedAt()
        );
    }
}
