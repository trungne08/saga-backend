package com.saga.be.repository;

import com.saga.be.entity.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Optional<Task> findByProjectIdAndExternalId(UUID projectId, String externalId);

    @Query("select coalesce(sum(case when task.storyPoint is null then 1 else task.storyPoint end), 0) "
            + "from Task task where task.project.id = :projectId and task.sprint.id = :sprintId "
            + "and task.assignee.id = :studentId and task.status = com.saga.be.entity.enums.TaskStatus.DONE")
    Long sumDoneEffectiveStoryPoints(
            @Param("projectId") UUID projectId,
            @Param("sprintId") UUID sprintId,
            @Param("studentId") UUID studentId
    );
}
