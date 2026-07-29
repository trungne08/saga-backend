package com.saga.be.repository;

import com.saga.be.entity.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Optional<Task> findByProjectIdAndExternalId(UUID projectId, String externalId);
}
