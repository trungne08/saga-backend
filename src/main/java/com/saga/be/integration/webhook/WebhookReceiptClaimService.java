package com.saga.be.integration.webhook;

import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import com.saga.be.repository.WebhookReceiptRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookReceiptClaimService {
    private final WebhookReceiptRepository repository;
    public WebhookReceiptClaimService(WebhookReceiptRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<WebhookReceiptClaim> claim(UUID id) {
        WebhookReceipt receipt = repository.findForUpdateById(id).orElse(null);
        if (receipt == null || receipt.getAttemptCount() >= 5
                || (receipt.getReceiptStatus() != WebhookReceiptStatus.RECEIVED
                && receipt.getReceiptStatus() != WebhookReceiptStatus.FAILED)) return Optional.empty();
        receipt.setReceiptStatus(WebhookReceiptStatus.PROCESSING);
        receipt.setAttemptCount(receipt.getAttemptCount() + 1);
        repository.saveAndFlush(receipt);
        return Optional.of(new WebhookReceiptClaim(receipt.getId(), receipt.getProvider(),
                receipt.getTargetId(), receipt.getDeliveryId(), receipt.getEventType(), receipt.getPayloadCiphertext()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recoverStaleProcessing(UUID id, LocalDateTime before) {
        WebhookReceipt receipt = repository.findForUpdateById(id).orElse(null);
        if (receipt == null || receipt.getReceiptStatus() != WebhookReceiptStatus.PROCESSING) return false;
        LocalDateTime activity = receipt.getUpdatedAt() == null ? receipt.getCreatedAt() : receipt.getUpdatedAt();
        if (activity != null && !activity.isBefore(before)) return false;
        receipt.setReceiptStatus(WebhookReceiptStatus.RECEIVED);
        receipt.setErrorCategory("WORKER_INTERRUPTED");
        repository.saveAndFlush(receipt);
        return true;
    }
}
