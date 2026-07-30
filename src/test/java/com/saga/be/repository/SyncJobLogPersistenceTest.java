package com.saga.be.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.entity.enums.SyncJobType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class SyncJobLogPersistenceTest {

    @Autowired
    private SyncJobLogRepository syncJobLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsAndReadsInitialBackfillJob() {
        SyncJobLog saved = syncJobLogRepository.saveAndFlush(
                SyncJobLog.builder()
                        .targetSystem("GITHUB")
                        .targetId(UUID.randomUUID())
                        .jobType(SyncJobType.INITIAL_BACKFILL)
                        .status(SyncJobStatus.IN_PROGRESS)
                        .startedAt(LocalDateTime.now())
                        .build()
        );

        entityManager.clear();

        SyncJobLog reloaded = syncJobLogRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals(SyncJobType.INITIAL_BACKFILL, reloaded.getJobType());
        assertEquals(SyncJobStatus.IN_PROGRESS, reloaded.getStatus());
    }
}
