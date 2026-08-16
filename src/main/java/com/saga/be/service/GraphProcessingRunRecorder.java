package com.saga.be.service;

import com.saga.be.entity.GraphProcessingRun;
import com.saga.be.entity.enums.GraphProcessingKind;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Fail-open telemetry recorder for successfully built graph projections. */
@Service
@RequiredArgsConstructor
@Slf4j
public class GraphProcessingRunRecorder {

    private final GraphProcessingRunPersistenceService persistenceService;
    private final Clock clock;

    public void record(GraphProcessingKind graphKind, UUID courseId, UUID teamId, UUID studentId,
            int nodesBuilt, int edgesBuilt) {
        if (nodesBuilt < 0 || edgesBuilt < 0) {
            throw new IllegalArgumentException("Graph processing counts must be non-negative");
        }
        try {
            persistenceService.persist(GraphProcessingRun.builder()
                    .graphKind(graphKind)
                    .occurredAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .courseId(courseId)
                    .teamId(teamId)
                    .studentId(studentId)
                    .nodesBuilt(nodesBuilt)
                    .edgesBuilt(edgesBuilt)
                    .build());
        } catch (RuntimeException exception) {
            log.warn("graph_processing_telemetry_persist_failed kind={} failure={}", graphKind,
                    exception.getClass().getSimpleName());
        }
    }
}
