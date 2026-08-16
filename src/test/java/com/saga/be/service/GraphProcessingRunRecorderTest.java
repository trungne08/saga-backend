package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.saga.be.entity.GraphProcessingRun;
import com.saga.be.entity.enums.GraphProcessingKind;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GraphProcessingRunRecorderTest {

    @Test
    void recordsUtcSnapshotWithProjectionCounts() {
        GraphProcessingRunPersistenceService persistence = Mockito.mock(GraphProcessingRunPersistenceService.class);
        Instant now = Instant.parse("2026-08-15T18:00:00Z");
        GraphProcessingRunRecorder recorder = new GraphProcessingRunRecorder(persistence, Clock.fixed(now, ZoneOffset.UTC));
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        recorder.record(GraphProcessingKind.CONTRIBUTION, courseId, teamId, null, 4, 3);

        ArgumentCaptor<GraphProcessingRun> captured = ArgumentCaptor.forClass(GraphProcessingRun.class);
        verify(persistence).persist(captured.capture());
        assertEquals(GraphProcessingKind.CONTRIBUTION, captured.getValue().getGraphKind());
        assertEquals(LocalDateTime.ofInstant(now, ZoneOffset.UTC), captured.getValue().getOccurredAt());
        assertEquals(4, captured.getValue().getNodesBuilt());
        assertEquals(3, captured.getValue().getEdgesBuilt());
    }

    @Test
    void persistenceFailureDoesNotBreakSuccessfulGraphProjection() {
        GraphProcessingRunPersistenceService persistence = Mockito.mock(GraphProcessingRunPersistenceService.class);
        doThrow(new IllegalStateException("database unavailable")).when(persistence).persist(any());
        GraphProcessingRunRecorder recorder = new GraphProcessingRunRecorder(persistence, Clock.systemUTC());

        assertDoesNotThrow(() -> recorder.record(GraphProcessingKind.INTERACTION, null, UUID.randomUUID(), null, 1, 0));
    }

    @Test
    void rejectsNegativeCounts() {
        GraphProcessingRunRecorder recorder = new GraphProcessingRunRecorder(
                Mockito.mock(GraphProcessingRunPersistenceService.class), Clock.systemUTC());
        assertThrows(IllegalArgumentException.class,
                () -> recorder.record(GraphProcessingKind.INTERACTION, null, null, null, -1, 0));
    }
}
