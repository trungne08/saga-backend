package com.saga.be.repository;

import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubInstallationRepository
        extends JpaRepository<GitHubInstallation, UUID> {
    Optional<GitHubInstallation> findByInstallationId(Long installationId);

    List<GitHubInstallation> findByInstallationStatus(
            GitHubInstallationStatus status
    );
}
