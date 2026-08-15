package com.saga.be.repository;

import com.saga.be.entity.ProjectGroupWeightConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectGroupWeightConfigRepository extends JpaRepository<ProjectGroupWeightConfig, UUID> {

    Optional<ProjectGroupWeightConfig> findByProjectId(UUID projectId);
}
