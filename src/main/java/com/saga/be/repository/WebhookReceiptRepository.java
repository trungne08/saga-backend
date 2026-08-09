package com.saga.be.repository;

import com.saga.be.entity.WebhookReceipt;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.WebhookReceiptStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface WebhookReceiptRepository
        extends JpaRepository<WebhookReceipt, UUID> {
    Optional<WebhookReceipt> findByProviderAndDeliveryId(
            IntegrationProvider provider,
            String deliveryId
    );

    long countByProviderAndReceiptStatus(
            IntegrationProvider provider,
            WebhookReceiptStatus receiptStatus
    );

    List<WebhookReceipt> findTop100ByReceiptStatusInOrderByCreatedAtAsc(
            List<WebhookReceiptStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select receipt from WebhookReceipt receipt where receipt.id = :id")
    Optional<WebhookReceipt> findForUpdateById(@Param("id") UUID id);

    @Query("select receipt.id from WebhookReceipt receipt where receipt.receiptStatus in :statuses order by receipt.createdAt asc")
    List<UUID> findTop100IdsByReceiptStatusInOrderByCreatedAtAsc(
            @Param("statuses") List<WebhookReceiptStatus> statuses
    );
}
