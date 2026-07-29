package com.saga.be.repository;

import com.saga.be.entity.SyncJobLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncJobLogRepository extends JpaRepository<SyncJobLog, UUID> {
    List<SyncJobLog> findTop20ByTargetIdOrderByStartedAtDesc(UUID targetId);
}
