package com.saga.be.controller;

import com.saga.be.dto.request.PeerReviewRequest;
import com.saga.be.dto.response.PeerReviewCandidatesResponse;
import com.saga.be.dto.response.PeerReviewResponse;
import com.saga.be.dto.response.SprintPeerReviewResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.PeerReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đánh giá", description = "Thực hiện và xem peer review theo Sprint.")
@RequestMapping("/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews")
@RequiredArgsConstructor
public class PeerReviewController {

    private final PeerReviewService peerReviewService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<PeerReviewResponse> submit(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @PathVariable UUID sprintId,
            @Valid @RequestBody PeerReviewRequest request
    ) {
        return ResponseEntity.ok(
                peerReviewService.submit(principal, teamId, sprintId, request)
        );
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'STUDENT')")
    public ResponseEntity<PeerReviewCandidatesResponse> getCandidates(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @PathVariable UUID sprintId
    ) {
        return ResponseEntity.ok(
                peerReviewService.getReviewCandidates(principal, teamId, sprintId)
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER', 'STUDENT')")
    public ResponseEntity<SprintPeerReviewResponse> getSprintReviews(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId,
            @PathVariable UUID sprintId
    ) {
        return ResponseEntity.ok(
                peerReviewService.getSprintReviews(principal, teamId, sprintId)
        );
    }
}
