package com.saga.be.repository;

import com.saga.be.entity.PeerReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeerReviewRepository extends JpaRepository<PeerReview, UUID> {

    List<PeerReview> findByRevieweeIdAndSprintId(UUID revieweeId, UUID sprintId);

    List<PeerReview> findByRevieweeIdAndSprintBoardProjectId(UUID revieweeId, UUID projectId);
}
