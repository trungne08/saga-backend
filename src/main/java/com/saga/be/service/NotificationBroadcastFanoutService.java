package com.saga.be.service;

import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.ApplicationRole;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationBroadcastFanoutService {

    private final NotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fanout(
            UUID broadcastId,
            List<UUID> recipientIds,
            ApplicationRole recipientRole,
            NotificationType type,
            String title,
            String message
    ) {
        for (UUID recipientId : recipientIds) {
            notificationService.createForBroadcast(
                    broadcastId,
                    recipientId,
                    recipientRole,
                    type,
                    title,
                    message
            );
        }
    }
}
