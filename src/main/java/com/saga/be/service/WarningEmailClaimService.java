package com.saga.be.service;

import com.saga.be.config.WarningEmailProperties;
import com.saga.be.entity.WarningEmailOutbox;
import com.saga.be.entity.enums.WarningEmailStatus;
import com.saga.be.repository.WarningEmailOutboxRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarningEmailClaimService {

    private final WarningEmailOutboxRepository outbox;
    private final WarningEmailProperties properties;

    public WarningEmailClaimService(
            WarningEmailOutboxRepository outbox,
            WarningEmailProperties properties
    ) {
        this.outbox = outbox;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<GmailMessage> claim(UUID outboxId) {
        WarningEmailOutbox row = outbox.findLockedById(outboxId).orElse(null);
        if (row == null
                || row.getDeliveryStatus() == WarningEmailStatus.SENT
                || row.getDeliveryStatus() == WarningEmailStatus.PROCESSING
                || row.getAttemptCount() >= properties.getMaxAttempts()) {
            return Optional.empty();
        }
        LocalDateTime claimedAt = WarningEmailOutboxService.nowUtc();
        row.setDeliveryStatus(WarningEmailStatus.PROCESSING);
        row.setAttemptCount(row.getAttemptCount() + 1);
        row.setLastAttemptAt(claimedAt);
        row.setProcessingStartedAt(claimedAt);
        row.setFailureCode(null);
        outbox.saveAndFlush(row);
        return Optional.of(new GmailMessage(
                row.getRecipientEmail(),
                row.getSubject(),
                row.getBodyText(),
                row.getBodyHtml()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStaleProcessing(UUID outboxId, LocalDateTime staleBefore) {
        WarningEmailOutbox row = outbox.findLockedById(outboxId).orElse(null);
        if (row == null
                || row.getDeliveryStatus() != WarningEmailStatus.PROCESSING
                || row.getProcessingStartedAt() == null
                || !row.getProcessingStartedAt().isBefore(staleBefore)) {
            return false;
        }
        row.setProcessingStartedAt(null);
        if (row.getAttemptCount() >= properties.getMaxAttempts()) {
            row.setDeliveryStatus(WarningEmailStatus.FAILED);
            row.setFailureCode("MAX_ATTEMPTS_EXHAUSTED");
            outbox.saveAndFlush(row);
            return false;
        }
        row.setDeliveryStatus(WarningEmailStatus.FAILED);
        row.setFailureCode("PROCESSING_TIMEOUT");
        outbox.saveAndFlush(row);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID outboxId) {
        outbox.findLockedById(outboxId).ifPresent(row -> {
            if (row.getDeliveryStatus() == WarningEmailStatus.PROCESSING) {
                row.setDeliveryStatus(WarningEmailStatus.SENT);
                row.setSentAt(WarningEmailOutboxService.nowUtc());
                row.setProcessingStartedAt(null);
                row.setFailureCode(null);
                outbox.saveAndFlush(row);
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID outboxId, String failureCode) {
        outbox.findLockedById(outboxId).ifPresent(row -> {
            if (row.getDeliveryStatus() == WarningEmailStatus.PROCESSING) {
                row.setDeliveryStatus(WarningEmailStatus.FAILED);
                row.setProcessingStartedAt(null);
                row.setFailureCode(failureCode);
                outbox.saveAndFlush(row);
            }
        });
    }
}
