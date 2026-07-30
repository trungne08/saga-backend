package com.saga.be.integration.webhook;

import com.saga.be.entity.enums.IntegrationProvider;
import java.util.UUID;

public record WebhookReceiptClaim(UUID receiptId, IntegrationProvider provider,
        UUID targetId, String deliveryId, String eventType, String payloadCiphertext) { }
