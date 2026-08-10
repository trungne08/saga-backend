package com.saga.be.repository;

import com.saga.be.entity.NotificationDelivery;
import com.saga.be.entity.enums.NotificationDeliveryStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select delivery from NotificationDelivery delivery "
            + "join fetch delivery.notification "
            + "join fetch delivery.installation "
            + "where delivery.id = :id")
    Optional<NotificationDelivery> findForUpdateById(@Param("id") UUID id);

    @Query("select delivery.id from NotificationDelivery delivery "
            + "where delivery.deliveryStatus in :statuses order by delivery.createdAt asc")
    List<UUID> findTop100IdsByDeliveryStatusInOrderByCreatedAtAsc(
            @Param("statuses") List<NotificationDeliveryStatus> statuses,
            Pageable pageable
    );

    @Query("select delivery.id from NotificationDelivery delivery "
            + "where delivery.deliveryStatus = :status "
            + "and delivery.processingStartedAt < :staleBefore "
            + "order by delivery.processingStartedAt asc")
    List<UUID> findTop100IdsByProcessingStartedAtBefore(
            @Param("status") NotificationDeliveryStatus status,
            @Param("staleBefore") LocalDateTime staleBefore,
            Pageable pageable
    );
}
