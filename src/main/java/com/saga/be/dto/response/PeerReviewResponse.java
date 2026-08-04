package com.saga.be.dto.response;

import com.saga.be.entity.PeerReview;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PeerReviewResponse(
        UUID id,
        UUID sprintId,
        String sprintName,
        UUID reviewerId,
        String reviewerName,
        UUID revieweeId,
        String revieweeName,
        Integer starRating,
        List<PeerReviewCriterionResponse> criteriaRatings,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PeerReviewResponse from(PeerReview peerReview) {
        return new PeerReviewResponse(
                peerReview.getId(),
                peerReview.getSprint() != null ? peerReview.getSprint().getId() : null,
                peerReview.getSprint() != null ? peerReview.getSprint().getName() : null,
                peerReview.getReviewer() != null ? peerReview.getReviewer().getId() : null,
                peerReview.getReviewer() != null ? peerReview.getReviewer().getFullName() : null,
                peerReview.getReviewee() != null ? peerReview.getReviewee().getId() : null,
                peerReview.getReviewee() != null ? peerReview.getReviewee().getFullName() : null,
                peerReview.getStarRating(),
                peerReview.getCriteriaRatings() == null
                        ? List.of()
                        : peerReview.getCriteriaRatings().stream()
                                .map(PeerReviewCriterionResponse::from)
                                .toList(),
                peerReview.getComment(),
                peerReview.getCreatedAt(),
                peerReview.getUpdatedAt()
        );
    }
}
