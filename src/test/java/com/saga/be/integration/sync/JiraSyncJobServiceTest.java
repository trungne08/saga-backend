package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.SyncJobLog;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.SyncJobType;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SyncJobLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JiraSyncJobServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:13:49Z"),
            ZoneOffset.UTC
    );

    @Test
    void claimWritesStartedAtUsingTheFixedUtcClock() {
        JiraBoardRepository boardRepository = mock(JiraBoardRepository.class);
        SyncJobLogRepository jobRepository = mock(SyncJobLogRepository.class);
        UUID boardId = UUID.randomUUID();
        JiraBoard board = JiraBoard.builder()
                .connectionStatus(IntegrationStatus.BACKFILLING)
                .build();
        board.setId(boardId);
        when(boardRepository.findForSyncClaimById(boardId))
                .thenReturn(Optional.of(board));
        when(jobRepository.findActiveByTargetIdOrderByStartedAtDesc(
                eq(boardId),
                any()
        )).thenReturn(List.of());
        when(jobRepository.saveAndFlush(any(SyncJobLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JiraSyncJobService service = new JiraSyncJobService(
                boardRepository,
                jobRepository,
                mock(SyncJobFinalizationService.class),
                Duration.ofMinutes(15),
                FIXED_CLOCK
        );

        SyncJobLog job = service.claim(boardId, SyncJobType.INITIAL_BACKFILL)
                .orElseThrow();

        assertEquals(
                LocalDateTime.of(2026, 8, 4, 5, 13, 49),
                job.getStartedAt()
        );
    }

    @Test
    void neverClaimsADisconnectedBoard() {
        JiraBoardRepository boardRepository = mock(JiraBoardRepository.class);
        UUID boardId = UUID.randomUUID();
        JiraBoard board = JiraBoard.builder()
                .connectionStatus(IntegrationStatus.DISCONNECTED).build();
        when(boardRepository.findForSyncClaimById(boardId))
                .thenReturn(Optional.of(board));
        JiraSyncJobService service = new JiraSyncJobService(
                boardRepository,
                mock(SyncJobLogRepository.class),
                mock(SyncJobFinalizationService.class),
                Duration.ofMinutes(15),
                FIXED_CLOCK
        );

        assertTrue(service.claim(boardId, SyncJobType.RECONCILIATION).isEmpty());
    }
}
