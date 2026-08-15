package com.saga.be.repository;

import com.saga.be.entity.WarningEmailOutbox;
import com.saga.be.entity.enums.WarningEmailStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WarningEmailOutboxRepository extends JpaRepository<WarningEmailOutbox, UUID> {

    Optional<WarningEmailOutbox> findByNotificationId(UUID notificationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select row from WarningEmailOutbox row where row.id = :id")
    Optional<WarningEmailOutbox> findLockedById(@Param("id") UUID id);

    @Query("""
            select row.id from WarningEmailOutbox row
            where row.deliveryStatus in :statuses
            order by row.createdAt asc, row.id asc
            """)
    List<UUID> findTop100IdsByDeliveryStatusInOrderByCreatedAtAsc(
            @Param("statuses") Collection<WarningEmailStatus> statuses
    );

    @Query("""
            select row.id from WarningEmailOutbox row
            where row.deliveryStatus = :status
              and row.processingStartedAt < :staleBefore
            order by row.processingStartedAt asc, row.id asc
            """)
    List<UUID> findTop100IdsByProcessingStartedAtBefore(
            @Param("status") WarningEmailStatus status,
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
