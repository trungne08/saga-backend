package com.saga.be.integration.webhook;

import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import com.saga.be.repository.WebhookReceiptRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookReceiptStateService {
    private final WebhookReceiptRepository repository;
    public WebhookReceiptStateService(WebhookReceiptRepository repository) { this.repository = repository; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id) { update(id, true, null); }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, String category) { update(id, false, category); }
    private void update(UUID id, boolean complete, String category) {
        repository.findForUpdateById(id).ifPresent(receipt -> {
            receipt.setReceiptStatus(complete ? WebhookReceiptStatus.COMPLETED : WebhookReceiptStatus.FAILED);
            receipt.setErrorCategory(category);
            if (complete) { receipt.setPayloadCiphertext(""); receipt.setProcessedAt(LocalDateTime.now()); }
            repository.saveAndFlush(receipt);
        });
    }
}
