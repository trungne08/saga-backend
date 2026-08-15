package com.saga.be.controller;

import com.saga.be.service.AdminCourseReportExportService;
import com.saga.be.service.AdminCourseReportExportService.ExportedCourseReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Tải báo cáo dữ liệu Course local dành cho quản trị viên.")
public class AdminCourseReportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AdminCourseReportExportService courseReportExportService;

    @GetMapping("/{courseId}/export")
    @Operation(summary = "Xuất báo cáo dữ liệu Course", description = "Tải XLSX dữ liệu local hiện tại; không phải bảng điểm hoặc xác nhận hoàn tất đánh giá.")
    public ResponseEntity<byte[]> export(@PathVariable UUID courseId) {
        ExportedCourseReport report = courseReportExportService.export(courseId);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(report.filename(), StandardCharsets.UTF_8).build().toString())
                .body(report.bytes());
    }
}
