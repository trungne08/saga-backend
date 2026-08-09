package com.saga.be.repository;

import com.saga.be.entity.PeerReview;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PeerReviewRepository extends JpaRepository<PeerReview, UUID> {
        List<PeerReview> findByRevieweeId(UUID revieweeId);

        List<PeerReview> findByRevieweeIdInAndSprintBoardProjectId(
                        Collection<UUID> revieweeIds,
                        UUID projectId);

        @EntityGraph(attributePaths = { "criteriaRatings", "criteriaRatings.rubricTemplate" })
        Optional<PeerReview> findBySprintIdAndReviewerIdAndRevieweeId(
                        UUID sprintId,
                        UUID reviewerId,
                        UUID revieweeId);

        @EntityGraph(attributePaths = { "criteriaRatings", "criteriaRatings.rubricTemplate" })
        List<PeerReview> findBySprintIdAndReviewerIdAndRevieweeIdIn(
                        UUID sprintId,
                        UUID reviewerId,
                        Collection<UUID> revieweeIds);

        @EntityGraph(attributePaths = { "sprint", "reviewer", "reviewee", "criteriaRatings", "criteriaRatings.rubricTemplate" })
        List<PeerReview> findBySprintIdAndRevieweeIdInAndReviewerIdInOrderByCreatedAtAsc(
                        UUID sprintId,
                        Collection<UUID> revieweeIds,
                        Collection<UUID> reviewerIds);

        List<PeerReview> findByRevieweeIdAndSprintId(UUID revieweeId, UUID sprintId);

        List<PeerReview> findByRevieweeIdAndSprintBoardProjectId(UUID revieweeId, UUID projectId);

        @EntityGraph(attributePaths = {"sprint", "reviewer", "reviewee"})
        List<PeerReview> findBySprintBoardProjectCourseIdAndSprintDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                        UUID courseId
        );
}
