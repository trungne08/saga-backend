package com.saga.be.repository;

import com.saga.be.entity.CommitReviewResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitReviewResultRepository extends JpaRepository<CommitReviewResult, UUID> {

    Optional<CommitReviewResult> findByIntentId(UUID intentId);

    List<CommitReviewResult> findByIntentIdIn(Collection<UUID> intentIds);

    Optional<CommitReviewResult> findByAiJobId(UUID aiJobId);

    @EntityGraph(attributePaths = {"intent", "repo", "commit"})
    List<CommitReviewResult> findByProjectIdAndReviewModeOrderByCompletedAtDescIdDesc(
            UUID projectId,
            String reviewMode,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"commit", "repo", "intent"})
    @Query("""
            select result from CommitReviewResult result
            where result.projectId = :projectId
              and result.reviewMode = 'TASK_LINKED'
              and result.verdict in ('PASS', 'NEEDS_CHANGES')
            order by result.completedAt desc, result.id desc
            """)
    List<CommitReviewResult> findEligibleLiveLinkedByProject(
            @Param("projectId") UUID projectId,
            Pageable pageable
    );

    @Query("""
            select result from CommitReviewResult result
            where result.repo.id = :repoId
              and result.reviewMode = 'HISTORICAL_LIGHT'
              and result.completedAt >= :fromInclusive
              and result.completedAt < :toExclusive
            order by result.completedAt asc, result.id asc
            """)
    List<CommitReviewResult> findHistoricalCompletedInWindow(
            @Param("repoId") UUID repoId,
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive
    );

    @Query("""
            select result.repo.id from CommitReviewResult result
            where result.reviewMode = 'HISTORICAL_LIGHT'
              and result.completedAt >= :fromInclusive
              and result.completedAt < :toExclusive
            group by result.repo.id
            """)
    List<UUID> findHistoricalRepoIdsInWindow(
            @Param("fromInclusive") java.time.LocalDateTime fromInclusive,
            @Param("toExclusive") java.time.LocalDateTime toExclusive
    );

    long countByProjectIdIn(Collection<UUID> projectIds);
}
