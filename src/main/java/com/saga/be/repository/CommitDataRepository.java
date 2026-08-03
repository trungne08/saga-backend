package com.saga.be.repository;

import com.saga.be.entity.CommitData;
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
}
