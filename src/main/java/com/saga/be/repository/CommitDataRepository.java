package com.saga.be.repository;

import com.saga.be.entity.CommitData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitDataRepository extends JpaRepository<CommitData, UUID> {
    Optional<CommitData> findByRepoIdAndShaHash(UUID repoId, String shaHash);

    @Query("select count(commit) from CommitData commit "
            + "where commit.repo.project.id = :projectId and commit.author.id = :studentId")
    long countByProjectIdAndAuthorId(
            @Param("projectId") UUID projectId,
            @Param("studentId") UUID studentId
    );
    @Query("select commit from CommitData commit join commit.repo repo where commit.author.id = :authorId and repo.project.id = :projectId")
    List<CommitData> findByAuthorIdAndProjectId(@Param("authorId") UUID authorId, @Param("projectId") UUID projectId);

    @Query("select commit from CommitData commit join commit.repo repo where commit.author.id = :authorId and repo.project.id = :projectId and commit.task is not null")
    List<CommitData> findByAuthorIdAndProjectIdAndTaskIsNotNull(@Param("authorId") UUID authorId, @Param("projectId") UUID projectId);

    @Query("select commit from CommitData commit join commit.repo repo where repo.project.id = :projectId")
    List<CommitData> findByProjectId(@Param("projectId") UUID projectId);
}
