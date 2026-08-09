package com.saga.be.repository;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.IntegrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GitRepoRepository extends JpaRepository<GitRepo, UUID> {
    List<GitRepo> findByProjectIdOrderByFullName(UUID projectId);

    Optional<GitRepo> findByProjectIdAndRepositoryId(
            UUID projectId,
            Long repositoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"installation"})
    @Query("select repository from GitRepo repository where repository.project.id = :projectId and repository.repositoryId = :repositoryId")
    Optional<GitRepo> findForReconnectByProjectIdAndRepositoryId(
            @Param("projectId") UUID projectId,
            @Param("repositoryId") Long repositoryId
    );

    Optional<GitRepo> findByRepositoryId(Long repositoryId);

    @EntityGraph(attributePaths = {"project", "installation"})
    @Query("select repository from GitRepo repository where repository.id = :id")
    Optional<GitRepo> findForSyncById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project", "installation"})
    @Query("select repository from GitRepo repository where repository.id = :id")
    Optional<GitRepo> findForInitialBackfillClaimById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select repository from GitRepo repository where repository.id = :id")
    Optional<GitRepo> findForStateUpdateById(@Param("id") UUID id);

    List<GitRepo> findByInstallationInstallationId(Long installationId);

    List<GitRepo> findByConnectionStatusIn(List<IntegrationStatus> statuses);

    List<GitRepo> findByProjectIdIn(List<UUID> projectIds);

    long countByConnectionStatus(IntegrationStatus connectionStatus);
}
