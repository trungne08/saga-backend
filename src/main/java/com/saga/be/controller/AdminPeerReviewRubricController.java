package com.saga.be.controller;

import com.saga.be.dto.request.AdminPeerReviewRubricRequest;
import com.saga.be.dto.response.AdminPeerReviewRubricResponse;
import com.saga.be.service.AdminPeerReviewRubricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/peer-review-rubrics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Quản trị", description = "Quản lý rubric peer review global đang hoạt động.")
public class AdminPeerReviewRubricController {

    private final AdminPeerReviewRubricService adminPeerReviewRubricService;

    @PostMapping
    @Operation(summary = "Tạo rubric peer review global")
    public ResponseEntity<AdminPeerReviewRubricResponse> create(
            @Valid @RequestBody AdminPeerReviewRubricRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminPeerReviewRubricService.create(request));
    }

    @PutMapping("/{rubricId}")
    @Operation(summary = "Cập nhật rubric peer review global đang hoạt động")
    public ResponseEntity<AdminPeerReviewRubricResponse> update(
            @PathVariable UUID rubricId,
            @Valid @RequestBody AdminPeerReviewRubricRequest request
    ) {
        return ResponseEntity.ok(adminPeerReviewRubricService.update(rubricId, request));
    }

    @DeleteMapping("/{rubricId}")
    @Operation(summary = "Vô hiệu hóa mềm rubric peer review global")
    public ResponseEntity<Void> delete(@PathVariable UUID rubricId) {
        adminPeerReviewRubricService.softDelete(rubricId);
        return ResponseEntity.noContent().build();
    }
}
