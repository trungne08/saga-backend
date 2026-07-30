package com.saga.be.repository;

import com.saga.be.entity.Team;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findByProjectId(UUID projectId);

    Optional<Team> findByCourseIdAndName(UUID courseId, String name);
}
