package com.saga.be.repository;

import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JiraWriteOperationRepository extends JpaRepository<JiraWriteOperation, UUID> {

    Optional<JiraWriteOperation> findByProjectIdAndIdempotencyKey(
            UUID projectId,
            String idempotencyKey
    );

    List<JiraWriteOperation> findByStatusIn(Collection<JiraWriteOperationStatus> statuses);
}
