package com.saga.be.repository;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncJobLogRepository extends JpaRepository<SyncJobLog, UUID> {
    List<SyncJobLog> findTop20ByTargetIdInOrderByStartedAtDesc(
            Collection<UUID> targetIds
    );

    Optional<SyncJobLog> findTopByTargetIdAndJobTypeOrderByStartedAtDesc(
            UUID targetId,
            SyncJobType jobType
    );
}
