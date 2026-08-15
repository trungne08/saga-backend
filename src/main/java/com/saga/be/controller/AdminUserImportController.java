package com.saga.be.controller;

import com.saga.be.dto.response.AdminUserImportResponse;
import com.saga.be.service.AdminUserImportService;
import com.saga.be.service.AdminUserImportService.ImportRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Pre-provision local profile toàn cục, không gán Course hoặc Team.")
public class AdminUserImportController {

    private final AdminUserImportService adminUserImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import người dùng toàn cục", description = "role chỉ nhận STUDENT hoặc LECTURER. Workbook không có cột role.")
    public ResponseEntity<AdminUserImportResponse> importUsers(
            @RequestParam("role") ImportRole role,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(adminUserImportService.importUsers(role, file));
    }
}
