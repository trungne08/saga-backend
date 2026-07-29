package com.saga.be.repository;

import com.saga.be.entity.GitRepo;
import com.saga.be.entity.enums.IntegrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitRepoRepository extends JpaRepository<GitRepo, UUID> {
    List<GitRepo> findByProjectIdOrderByFullName(UUID projectId);

    Optional<GitRepo> findByProjectIdAndRepositoryId(
            UUID projectId,
            Long repositoryId
    );

    Optional<GitRepo> findByRepositoryId(Long repositoryId);

    List<GitRepo> findByInstallationInstallationId(Long installationId);

    List<GitRepo> findByConnectionStatusIn(List<IntegrationStatus> statuses);
}
