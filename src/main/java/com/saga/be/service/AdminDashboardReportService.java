package com.saga.be.service;

import com.saga.be.dto.response.AdminAnomaliesReportResponse;
import com.saga.be.dto.response.AdminAnomaliesReportResponse.AdminAnomalySignalResponse;
import com.saga.be.dto.response.AdminGraphProcessingReportResponse;
import com.saga.be.dto.response.AdminGraphProcessingReportResponse.AdminGraphProcessingPointResponse;
import com.saga.be.entity.GraphProcessingRun;
import com.saga.be.entity.enums.AdminAnomalySignalType;
import com.saga.be.entity.enums.AdminReportSupportStatus;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.GraphProcessingRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin dashboard V1 report reads. Local persisted data only; no provider calls or mutations.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardReportService {

    static final int GRAPH_PROCESSING_PERIOD_DAYS = 7;
    static final ZoneId GRAPH_PROCESSING_DAY_BUCKET_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TaskRepository taskRepository;
    private final GraphProcessingRunRepository graphProcessingRunRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminAnomaliesReportResponse anomalies() {
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        long overdueCount = taskRepository.countOverdueAssignedTeamMemberTasks(nowUtc);
        return new AdminAnomaliesReportResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                List.of(
                        new AdminAnomalySignalResponse(
                                AdminAnomalySignalType.OVERDUE_TASK,
                                AdminReportSupportStatus.SUPPORTED,
                                overdueCount
                        ),
                        new AdminAnomalySignalResponse(
                                AdminAnomalySignalType.MSR,
                                AdminReportSupportStatus.TBD,
                                null
                        ),
                        new AdminAnomalySignalResponse(
                                AdminAnomalySignalType.DEADLINE_PROCESS,
                                AdminReportSupportStatus.TBD,
                                null
                        ),
                        new AdminAnomalySignalResponse(
                                AdminAnomalySignalType.SNA_ISOLATION,
                                AdminReportSupportStatus.TBD,
                                null
                        )
                )
        );
    }

    @Transactional(readOnly = true)
    public AdminGraphProcessingReportResponse graphProcessing() {
        Instant now = clock.instant();
        LocalDate currentBusinessDate = now.atZone(GRAPH_PROCESSING_DAY_BUCKET_ZONE).toLocalDate();
        LocalDate earliestBusinessDate = currentBusinessDate.minusDays(GRAPH_PROCESSING_PERIOD_DAYS - 1L);
        LocalDateTime windowStartUtc = LocalDateTime.ofInstant(
                earliestBusinessDate.atStartOfDay(GRAPH_PROCESSING_DAY_BUCKET_ZONE).toInstant(), ZoneOffset.UTC);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        Map<LocalDate, GraphProcessingTotals> totalsByDate = new TreeMap<>();
        for (GraphProcessingRun run : graphProcessingRunRepository
                .findByOccurredAtGreaterThanEqualAndOccurredAtLessThanEqualOrderByOccurredAtAsc(windowStartUtc, nowUtc)) {
            LocalDate businessDate = run.getOccurredAt().atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(GRAPH_PROCESSING_DAY_BUCKET_ZONE).toLocalDate();
            if (businessDate.isBefore(earliestBusinessDate) || businessDate.isAfter(currentBusinessDate)) {
                continue;
            }
            totalsByDate.computeIfAbsent(businessDate, ignored -> new GraphProcessingTotals()).add(run);
        }
        List<AdminGraphProcessingPointResponse> points = new ArrayList<>();
        totalsByDate.forEach((date, totals) -> points.add(
                new AdminGraphProcessingPointResponse(date, totals.nodesBuilt, totals.edgesBuilt, totals.runCount)));
        return new AdminGraphProcessingReportResponse(
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                GRAPH_PROCESSING_PERIOD_DAYS,
                true,
                graphProcessingRunRepository.findTopByOrderByOccurredAtAsc()
                        .map(run -> OffsetDateTime.of(run.getOccurredAt(), ZoneOffset.UTC)).orElse(null),
                points
        );
    }

    private static final class GraphProcessingTotals {
        private long nodesBuilt;
        private long edgesBuilt;
        private long runCount;

        private void add(GraphProcessingRun run) {
            nodesBuilt += run.getNodesBuilt();
            edgesBuilt += run.getEdgesBuilt();
            runCount++;
        }
    }
}
