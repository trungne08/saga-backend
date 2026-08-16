package com.saga.be.controller;

import com.saga.be.dto.response.AdminAnomaliesReportResponse;
import com.saga.be.dto.response.AdminGraphProcessingReportResponse;
import com.saga.be.service.AdminDashboardReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Báo cáo vận hành dashboard dành cho quản trị viên.")
public class AdminDashboardReportController {

    private final AdminDashboardReportService adminDashboardReportService;

    @GetMapping("/anomalies")
    @Operation(
            summary = "Xem báo cáo anomaly dashboard",
            description = "Trả OVERDUE_TASK đã hỗ trợ từ Task local; MSR, DEADLINE_PROCESS và SNA_ISOLATION còn TBD với count null. Không gọi Jira hoặc GitHub."
    )
    public ResponseEntity<AdminAnomaliesReportResponse> anomalies() {
        return ResponseEntity.ok(adminDashboardReportService.anomalies());
    }

    @GetMapping("/graph-processing")
    @Operation(
            summary = "Xem báo cáo xử lý graph",
            description = "Trả các bucket graph-processing persisted trong rolling 7 ngày theo Asia/Ho_Chi_Minh; không tạo điểm lịch sử giả."
    )
    public ResponseEntity<AdminGraphProcessingReportResponse> graphProcessing() {
        return ResponseEntity.ok(adminDashboardReportService.graphProcessing());
    }
}
