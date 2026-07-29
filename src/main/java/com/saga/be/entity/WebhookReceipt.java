package com.saga.be.entity;

import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "webhook_receipt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_webhook_provider_delivery",
                columnNames = {"provider", "delivery_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookReceipt extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 32)
    private IntegrationProvider provider;

    @Column(name = "delivery_id", nullable = false, length = 128)
    private String deliveryId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "target_id", columnDefinition = "char(36)")
    private UUID targetId;

    @Column(name = "payload_ciphertext", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false, length = 32)
    private WebhookReceiptStatus receiptStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_category", length = 64)
    private String errorCategory;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
