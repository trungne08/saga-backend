package com.saga.be.controller;

import com.saga.be.dto.response.PeerReviewRubricResponse;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.PeerReviewService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đánh giá", description = "Xem rubric peer review của nhóm.")
@RequestMapping("/api/v1/teams/{teamId}/peer-review-rubric")
@RequiredArgsConstructor
public class PeerReviewRubricController {

    private final PeerReviewService peerReviewService;

    @GetMapping
    public ResponseEntity<PeerReviewRubricResponse> getRubric(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID teamId
    ) {
        return ResponseEntity.ok(peerReviewService.getPeerReviewRubric(principal, teamId));
    }
}
