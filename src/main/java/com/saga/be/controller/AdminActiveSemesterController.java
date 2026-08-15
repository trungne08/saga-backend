package com.saga.be.controller;

import com.saga.be.dto.request.ActiveSemesterSettingRequest;
import com.saga.be.dto.response.ActiveSemesterSettingResponse;
import com.saga.be.service.AdminActiveSemesterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings/active-semester")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Cấu hình Học kỳ mặc định toàn hệ thống theo lựa chọn explícit của quản trị viên.")
public class AdminActiveSemesterController {

    private final AdminActiveSemesterService activeSemesterService;

    @GetMapping
    @Operation(summary = "Xem Học kỳ mặc định", description = "Chỉ đọc cấu hình explicit hiện tại; không suy diễn từ ngày và không lọc Course.")
    public ResponseEntity<ActiveSemesterSettingResponse> current() {
        return ResponseEntity.ok(activeSemesterService.current());
    }

    @PutMapping
    @Operation(summary = "Đặt Học kỳ mặc định", description = "Chỉ chọn Semester active theo UUID; không đổi Semester hoặc Course và yêu cầu CSRF.")
    public ResponseEntity<ActiveSemesterSettingResponse> updateActiveSemester(
            @Valid @RequestBody ActiveSemesterSettingRequest request
    ) {
        return ResponseEntity.ok(activeSemesterService.set(request.semesterId()));
    }
}
