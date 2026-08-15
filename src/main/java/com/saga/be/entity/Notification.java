package com.saga.be.entity;

import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.ApplicationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;
import java.sql.Types;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_user_notification_broadcast_recipient",
                columnNames = {"broadcast_id", "recipient_profile_id", "recipient_role"}
        ),
        @UniqueConstraint(
                name = "uk_user_notification_recipient_event",
                columnNames = {"recipient_profile_id", "recipient_role", "event_key"}
        )
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "recipient_profile_id", nullable = false, columnDefinition = "char(36)")
    private UUID recipientProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_role", nullable = false, length = 32)
    private ApplicationRole recipientRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 64)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broadcast_id")
    private NotificationBroadcast broadcast;

    @Column(name = "event_key", length = 255)
    private String eventKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
