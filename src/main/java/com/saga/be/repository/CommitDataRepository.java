package com.saga.be.repository;

import com.saga.be.entity.CommitData;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommitDataRepository extends JpaRepository<CommitData, UUID> {
    Optional<CommitData> findByRepoIdAndShaHash(UUID repoId, String shaHash);
}
