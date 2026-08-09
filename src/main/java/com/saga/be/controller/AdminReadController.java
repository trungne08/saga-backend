package com.saga.be.controller;

import com.saga.be.dto.response.AdminAuditLogResponse;
import com.saga.be.dto.response.AdminCourseProgressOverviewResponse;
import com.saga.be.dto.response.AdminProjectReadResponse;
import com.saga.be.dto.response.AdminSystemStatsResponse;
import com.saga.be.dto.response.AdminTeamReadResponse;
import com.saga.be.dto.response.AdminUserReadResponse;
import com.saga.be.dto.request.AdminUserStatusRequest;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import com.saga.be.service.AdminReadService;
import com.saga.be.service.AdminUserStatusService;
import com.saga.be.security.SagaPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global operational views for authenticated administrators. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Các màn hình đọc dữ liệu vận hành toàn cục dành riêng cho quản trị viên.")
public class AdminReadController {
    private final AdminReadService adminReadService;
    private final AdminUserStatusService adminUserStatusService;

    @GetMapping("/users")
    @Operation(summary = "Danh sách người dùng", description = "Đọc phân trang local profile an toàn; không trả Cognito subject hoặc dữ liệu bí mật.")
    public ResponseEntity<Page<AdminUserReadResponse>> users(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) ApplicationRole role,
            @RequestParam(required = false) AccountStatus accountStatus,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminReadService.users(keyword, role, accountStatus, page, size));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Cập nhật trạng thái tài khoản", description = "ADMIN chỉ cập nhật Student/Lecturer sang ACTIVE, INACTIVE hoặc SUSPENDED; không đổi role hay Cognito.")
    public ResponseEntity<AdminUserReadResponse> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserStatusRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal SagaPrincipal principal
    ) {
        return ResponseEntity.ok(adminUserStatusService.updateStatus(principal, id, request.status()));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Nhật ký hệ thống", description = "Đọc phân trang nhật ký Mongo mới nhất trước; không trả actor, IP hoặc payload old/new raw.")
    public ResponseEntity<Page<AdminAuditLogResponse>> auditLogs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminReadService.auditLogs(page, size));
    }

    @GetMapping("/system-stats")
    @Operation(summary = "Thống kê hệ thống", description = "Đếm dữ liệu local và trạng thái integration đã lưu; không gọi Jira hoặc GitHub.")
    public ResponseEntity<AdminSystemStatsResponse> systemStats() {
        return ResponseEntity.ok(adminReadService.systemStats());
    }

    @GetMapping("/teams")
    @Operation(summary = "Danh sách nhóm", description = "Đọc phân trang nhóm toàn cục cùng tóm tắt Course và Project local.")
    public ResponseEntity<Page<AdminTeamReadResponse>> teams(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminReadService.teams(page, size));
    }

    @GetMapping("/projects")
    @Operation(summary = "Danh sách dự án", description = "Đọc phân trang Project cùng tóm tắt Course và integration local; không trả secret hoặc gọi provider.")
    public ResponseEntity<Page<AdminProjectReadResponse>> projects(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminReadService.projects(page, size));
    }

    @GetMapping("/course-progress-overview")
    @Operation(summary = "Tổng quan tiến độ Course", description = "Đọc phân trang các count local hiện tại theo Course; không suy ra điểm cuối kỳ hoặc trạng thái hoàn tất.")
    public ResponseEntity<Page<AdminCourseProgressOverviewResponse>> courseProgressOverview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID lecturerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminReadService.courseProgressOverview(keyword, semesterId, lecturerId, page, size));
    }
}
