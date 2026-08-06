package com.saga.be.controller;

import com.saga.be.dto.response.PeerReviewDefaultRubricResponse;
import com.saga.be.service.PeerReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Đánh giá", description = "Xem rubric peer review mặc định.")
@RequestMapping("/api/v1/peer-review-rubrics")
@RequiredArgsConstructor
public class PeerReviewDefaultRubricController {

    private final PeerReviewService peerReviewService;

    @GetMapping("/default")
    public ResponseEntity<PeerReviewDefaultRubricResponse> getDefaultRubric() {
        return ResponseEntity.ok(peerReviewService.getDefaultPeerReviewRubric());
    }
}
