package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.SyncJobStatus;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncJobFinalizationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:13:49Z"),
            ZoneOffset.UTC
    );

    @Test
    void failedJobWritesCompletedAtUsingTheFixedUtcClock() {
        assertTerminalJobCompletedAt(SyncJobStatus.FAILED);
    }

    @Test
    void partialFailureJobWritesCompletedAtUsingTheFixedUtcClock() {
        assertTerminalJobCompletedAt(SyncJobStatus.PARTIAL_FAILURE);
    }

    private void assertTerminalJobCompletedAt(SyncJobStatus status) {
        SyncJobLogRepository jobRepository = mock(SyncJobLogRepository.class);
        SyncJobLog job = SyncJobLog.builder()
                .status(SyncJobStatus.IN_PROGRESS)
                .build();
        UUID jobId = UUID.randomUUID();
        job.setId(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        SyncJobFinalizationService service = new SyncJobFinalizationService(
                jobRepository,
                FIXED_CLOCK
        );

        service.finalizeJob(jobId, status, 1, 1, null, "SYNC_FAILURE");

        assertEquals(status, job.getStatus());
        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                job.getCompletedAt()
        );
        verify(jobRepository).saveAndFlush(job);
    }
}
