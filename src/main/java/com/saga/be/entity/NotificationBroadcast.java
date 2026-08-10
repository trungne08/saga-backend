package com.saga.be.entity;

import com.saga.be.entity.enums.NotificationBroadcastAudience;
import com.saga.be.entity.enums.NotificationBroadcastStatus;
import com.saga.be.security.ApplicationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

@Entity
@Table(name = "notification_broadcast", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_broadcast_sender_key",
        columnNames = {"sender_profile_id", "sender_role", "idempotency_key"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationBroadcast extends BaseEntity {

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "sender_profile_id", nullable = false, columnDefinition = "char(36)")
    private UUID senderProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 32)
    private ApplicationRole senderRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 64)
    private NotificationBroadcastAudience audience;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationBroadcastStatus status;

    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    @Column(name = "notification_count", nullable = false)
    private int notificationCount;

    @Column(name = "delivery_queued_count", nullable = false)
    private int deliveryQueuedCount;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
