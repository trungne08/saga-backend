package com.saga.be.service;

import com.saga.be.config.WarningEmailProperties;
import com.saga.be.entity.enums.WarningEmailStatus;
import com.saga.be.repository.WarningEmailOutboxRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(
        name = "app.warning-email.processing-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class WarningEmailProcessor {

    private static final Logger log = LoggerFactory.getLogger(WarningEmailProcessor.class);

    private final WarningEmailOutboxRepository outbox;
    private final WarningEmailClaimService claims;
    private final WarningEmailDeliveryAdapter delivery;
    private final WarningEmailProperties properties;

    public WarningEmailProcessor(
            WarningEmailOutboxRepository outbox,
            WarningEmailClaimService claims,
            WarningEmailDeliveryAdapter delivery,
            WarningEmailProperties properties
    ) {
        this.outbox = outbox;
        this.claims = claims;
        this.delivery = delivery;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(WarningEmailQueued event) {
        process(event.outboxId());
    }

    @Scheduled(fixedDelayString = "${app.warning-email.retry-delay-ms:60000}")
    public void retryFailedDeliveries() {
        List<UUID> ids = new ArrayList<>(
                outbox.findTop100IdsByDeliveryStatusInOrderByCreatedAtAsc(
                        List.of(WarningEmailStatus.PENDING, WarningEmailStatus.FAILED)
                )
        );
        LocalDateTime staleBefore = WarningEmailOutboxService.nowUtc().minus(properties.processingTimeout());
        outbox.findTop100IdsByProcessingStartedAtBefore(WarningEmailStatus.PROCESSING, staleBefore)
                .forEach(id -> {
                    if (claims.recoverStaleProcessing(id, staleBefore)) {
                        ids.add(id);
                    }
                });
        ids.forEach(this::process);
    }

    public void process(UUID outboxId) {
        claims.claim(outboxId).ifPresent(message -> {
            try {
                delivery.deliver(message);
                claims.markSent(outboxId);
            } catch (GmailDeliveryException exception) {
                claims.markFailed(outboxId, exception.getCategory());
                log.warn("warning-email delivery failed category={}", exception.getCategory());
            } catch (RuntimeException exception) {
                claims.markFailed(outboxId, "DELIVERY_FAILED");
                log.warn("warning-email delivery failed type={}", exception.getClass().getSimpleName());
            }
        });
    }
}
