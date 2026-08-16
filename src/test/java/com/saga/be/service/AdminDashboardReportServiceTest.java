package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saga.be.entity.GraphProcessingRun;
import com.saga.be.entity.enums.GraphProcessingKind;
import com.saga.be.repository.GraphProcessingRunRepository;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminDashboardReportServiceTest {

    @Test
    void graphProcessingAggregatesOnlyPersistedRunsInSevenBusinessDays() {
        Instant now = Instant.parse("2026-08-16T18:00:00Z"); // 01:00 on 17 Aug in Ho Chi Minh City
        GraphProcessingRunRepository runs = Mockito.mock(GraphProcessingRunRepository.class);
        when(runs.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of(
                        run("2026-08-15T17:30:00", 2, 3),
                        run("2026-08-16T17:30:00", 5, 7),
                        run("2026-08-09T17:00:00", 99, 99)));
        when(runs.findTopByOrderByOccurredAtAsc()).thenReturn(Optional.of(run("2026-08-01T00:00:00", 1, 1)));

        var response = new AdminDashboardReportService(Mockito.mock(TaskRepository.class), runs,
                Clock.fixed(now, ZoneOffset.UTC)).graphProcessing();

        assertEquals(7, response.periodDays());
        assertEquals(true, response.historySupported());
        assertEquals(OffsetDateTime.of(LocalDateTime.parse("2026-08-01T00:00:00"), ZoneOffset.UTC), response.coverageStart());
        assertEquals(2, response.points().size());
        assertEquals(LocalDate.of(2026, 8, 16), response.points().get(0).date());
        assertEquals(2, response.points().get(0).nodesBuilt());
        assertEquals(3, response.points().get(0).edgesBuilt());
        assertEquals(1, response.points().get(0).runCount());
        assertEquals(LocalDate.of(2026, 8, 17), response.points().get(1).date());
        assertEquals(5, response.points().get(1).nodesBuilt());
        assertEquals(7, response.points().get(1).edgesBuilt());
        assertEquals(1, response.points().get(1).runCount());
    }

    @Test
    void graphProcessingReturnsNoSyntheticPointsWhenNoRunsExist() {
        GraphProcessingRunRepository runs = Mockito.mock(GraphProcessingRunRepository.class);
        when(runs.findByOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of());
        when(runs.findTopByOrderByOccurredAtAsc()).thenReturn(Optional.empty());

        var response = new AdminDashboardReportService(Mockito.mock(TaskRepository.class), runs,
                Clock.fixed(Instant.parse("2026-08-15T18:00:00Z"), ZoneOffset.UTC)).graphProcessing();

        assertNull(response.coverageStart());
        assertEquals(List.of(), response.points());
    }

    private GraphProcessingRun run(String occurredAt, int nodesBuilt, int edgesBuilt) {
        return GraphProcessingRun.builder().graphKind(GraphProcessingKind.CONTRIBUTION)
                .occurredAt(LocalDateTime.parse(occurredAt)).nodesBuilt(nodesBuilt).edgesBuilt(edgesBuilt).build();
    }
}
