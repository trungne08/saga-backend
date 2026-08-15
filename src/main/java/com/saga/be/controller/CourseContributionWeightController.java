package com.saga.be.controller;

import com.saga.be.dto.request.ContributionConfigModeRequest;
import com.saga.be.dto.request.CourseContributionSliceWeightUpdateRequest;
import com.saga.be.dto.response.ContributionConfigModeResponse;
import com.saga.be.dto.response.CourseContributionSliceWeightResponse;
import com.saga.be.dto.response.CourseTeamContributionWeightsResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.CourseContributionWeightService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đóng góp", description = "Quản lý trọng số đánh giá đóng góp.")
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseContributionWeightController {

    private final CourseContributionWeightService courseContributionWeightService;

    @GetMapping("/{courseId}/contribution-slice-weights")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<CourseContributionSliceWeightResponse> getCurrentWeights(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId
    ) {
        return ResponseEntity.ok(courseContributionWeightService.getCurrentWeights(principal, courseId));
    }

    @PutMapping("/{courseId}/contribution-slice-weights")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<CourseContributionSliceWeightResponse> updateCurrentWeights(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestBody CourseContributionSliceWeightUpdateRequest request
    ) {
        return ResponseEntity.ok(courseContributionWeightService.updateCurrentWeights(principal, courseId, request));
    }

    @PutMapping("/{courseId}/contribution-config-mode")
    @PreAuthorize("hasRole('LECTURER')")
    public ResponseEntity<ContributionConfigModeResponse> switchConfigMode(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestBody ContributionConfigModeRequest request
    ) {
        return ResponseEntity.ok(courseContributionWeightService.switchConfigMode(principal, courseId, request));
    }

    @GetMapping("/{courseId}/contribution-team-weights")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<CourseTeamContributionWeightsResponse> getTeamWeights(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId
    ) {
        return ResponseEntity.ok(courseContributionWeightService.getTeamWeights(principal, courseId));
    }
}
