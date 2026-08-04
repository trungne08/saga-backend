package com.saga.be.repository;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    List<SyncJobLog> findByStatusIn(Collection<
            com.saga.be.entity.enums.SyncJobStatus> statuses);

    @Query("""
            select job from SyncJobLog job
            where job.targetId = :targetId
              and job.status in :statuses
            order by job.startedAt desc
            """)
    List<SyncJobLog> findActiveByTargetIdOrderByStartedAtDesc(
            @Param("targetId") UUID targetId,
            @Param("statuses") Collection<
                    com.saga.be.entity.enums.SyncJobStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from SyncJobLog job where job.id = :id")
    Optional<SyncJobLog> findForFinalizationById(@Param("id") UUID id);
}
