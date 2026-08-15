package com.saga.be.service;

import com.saga.be.dto.response.AdminAnomaliesReportResponse;
import com.saga.be.dto.response.AdminAnomaliesReportResponse.AdminAnomalySignalResponse;
import com.saga.be.dto.response.AdminGraphProcessingReportResponse;
import com.saga.be.entity.enums.AdminAnomalySignalType;
import com.saga.be.entity.enums.AdminReportSupportStatus;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

    private final TaskRepository taskRepository;

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
        return new AdminGraphProcessingReportResponse(
                OffsetDateTime.now(ZoneOffset.UTC),
                GRAPH_PROCESSING_PERIOD_DAYS,
                false,
                List.of()
        );
    }
}
