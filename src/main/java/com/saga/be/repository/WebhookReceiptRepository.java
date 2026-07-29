package com.saga.be.repository;

import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookReceiptRepository
        extends JpaRepository<WebhookReceipt, UUID> {
    Optional<WebhookReceipt> findByProviderAndDeliveryId(
            IntegrationProvider provider,
            String deliveryId
    );

    List<WebhookReceipt> findTop100ByReceiptStatusInOrderByCreatedAtAsc(
            List<WebhookReceiptStatus> statuses
    );
}
