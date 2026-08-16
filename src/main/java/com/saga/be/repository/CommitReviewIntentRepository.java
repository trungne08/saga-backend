package com.saga.be.repository;

import com.saga.be.entity.CommitReviewIntent;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitReviewIntentRepository extends JpaRepository<CommitReviewIntent, UUID> {

    Optional<CommitReviewIntent> findByRepoIdAndShaHash(UUID repoId, String shaHash);

    List<CommitReviewIntent> findByRepoIdAndShaHashIn(UUID repoId, Collection<String> shaHashes);

    Optional<CommitReviewIntent> findByAiJobId(UUID aiJobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from CommitReviewIntent intent where intent.id = :id")
    Optional<CommitReviewIntent> findLockedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"repo", "repo.project", "commit"})
    @Query("select intent from CommitReviewIntent intent where intent.id = :id")
    Optional<CommitReviewIntent> findWithReviewTargetById(@Param("id") UUID id);

    @Query("""
            select intent from CommitReviewIntent intent
            where intent.intentStatus = :status
            order by intent.priorityRank desc, intent.createdAt asc, intent.id asc
            """)
    List<CommitReviewIntent> findReady(
            @Param("status") CommitReviewIntentStatus status,
            Pageable pageable
    );

    @Query("""
            select intent from CommitReviewIntent intent
            where intent.intentStatus in :statuses
            order by intent.priorityRank desc, intent.updatedAt asc, intent.id asc
            """)
    List<CommitReviewIntent> findByIntentStatusIn(
            @Param("statuses") Collection<CommitReviewIntentStatus> statuses,
            Pageable pageable
    );

    @Query("""
            select intent.intentStatus, count(intent)
            from CommitReviewIntent intent
            where intent.repo.project.id in :projectIds
            group by intent.intentStatus
            """)
    List<Object[]> countStatusByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

    @Query("""
            select intent.intentStatus, count(intent)
            from CommitReviewIntent intent
            group by intent.intentStatus
            """)
    List<Object[]> countStatusAll();
}
