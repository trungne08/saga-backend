package com.saga.be.repository;

import com.saga.be.entity.CommitData;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommitDataRepository extends JpaRepository<CommitData, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"repo", "repo.project"})
    Optional<CommitData> findByRepoIdAndShaHash(UUID repoId, String shaHash);

    @Query("select count(commit) from CommitData commit "
            + "where commit.repo.project.id = :projectId and commit.author.id = :studentId")
    long countByProjectIdAndAuthorId(
            @Param("projectId") UUID projectId,
            @Param("studentId") UUID studentId
    );

    @Query("select count(commit) from CommitData commit "
            + "where commit.repo.project.id = :projectId and commit.author.id = :studentId "
            + "and commit.timestamp is not null")
    long countActivitiesByProjectIdAndAuthorId(
            @Param("projectId") UUID projectId,
            @Param("studentId") UUID studentId
    );

    @Query("select commit from CommitData commit join commit.repo repo where commit.author.id = :authorId and repo.project.id = :projectId")
    List<CommitData> findByAuthorIdAndProjectId(@Param("authorId") UUID authorId, @Param("projectId") UUID projectId);

    @Query("select commit from CommitData commit join commit.repo repo where commit.author.id = :authorId and repo.project.id = :projectId and commit.task is not null")
    List<CommitData> findByAuthorIdAndProjectIdAndTaskIsNotNull(@Param("authorId") UUID authorId, @Param("projectId") UUID projectId);

    @Query("select commit from CommitData commit join commit.repo repo where repo.project.id = :projectId")
    List<CommitData> findByProjectId(@Param("projectId") UUID projectId);

    @Query("select commit.repo.project.id, count(commit) from CommitData commit "
            + "where commit.repo.project.course.id = :courseId "
            + "group by commit.repo.project.id order by commit.repo.project.id")
    List<Object[]> countByProjectForCourse(@Param("courseId") UUID courseId);

    List<CommitData> findByAuthorIdAndRepoProjectIdOrderByTimestampDescIdDesc(
            UUID authorId, UUID projectId, Pageable pageable);

    @Query("select function('date', commit.timestamp), count(commit) from CommitData commit "
            + "where commit.repo.project.id = :projectId and commit.timestamp >= :startAt "
            + "and commit.timestamp < :endExclusive and (:studentId is null or commit.author.id = :studentId) "
            + "group by function('date', commit.timestamp) order by function('date', commit.timestamp)")
    List<Object[]> aggregateDailyCounts(
            @Param("projectId") UUID projectId,
            @Param("studentId") UUID studentId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @Query("""
            select commit.author.id, function('date', commit.timestamp), count(commit)
            from CommitData commit
            where commit.repo.project.id = :projectId
              and commit.author.id in :authorIds
              and commit.timestamp >= :startAt
              and commit.timestamp < :endExclusive
            group by commit.author.id, function('date', commit.timestamp)
            order by commit.author.id, function('date', commit.timestamp)
            """)
    List<Object[]> aggregateDailyCountsByProjectAndAuthorIds(
            @Param("projectId") UUID projectId,
            @Param("authorIds") Collection<UUID> authorIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"repo", "author"})
    List<CommitData> findByRepoProjectIdAndTimestampIsNotNullOrderByTimestampDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"repo", "repo.project"})
    @Query("""
            select commit from CommitData commit
            where commit.author.id = :studentId
               or commit.authorExternalId = :externalAccountId
            order by commit.timestamp desc, commit.id desc
            """)
    List<CommitData> findRecentByAuthorIdentity(
            @Param("studentId") UUID studentId,
            @Param("externalAccountId") String externalAccountId,
            Pageable pageable
    );

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"repo", "repo.project"})
    @Query("select commit from CommitData commit where commit.id = :id")
    Optional<CommitData> findWithRepoProjectById(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"repo", "repo.project"})
    @Query("""
            select commit from CommitData commit
            where commit.repo.reviewCutoverAt is not null
              and not exists (
                  select 1 from CommitReviewIntent intent
                  where intent.commit.id = commit.id
              )
            order by commit.id asc
            """)
    List<CommitData> findHistoricalBacklogWithoutIntent(Pageable pageable);

    @Query("""
            select max(commit.timestamp) from CommitData commit
            where commit.timestamp is not null
              and commit.repo.project.id = :projectId
              and (
                    commit.author.id = :studentId
                    or commit.authorExternalId = :externalAccountId
              )
            """)
    LocalDateTime findLatestMappedTimestamp(
            @Param("projectId") UUID projectId,
            @Param("studentId") UUID studentId,
            @Param("externalAccountId") String externalAccountId
    );
}
