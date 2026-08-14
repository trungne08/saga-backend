package com.saga.be.repository;

import com.saga.be.entity.Task;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {
    Optional<Task> findByProjectIdAndExternalId(UUID projectId, String externalId);

    Optional<Task> findByProjectIdAndExternalKey(UUID projectId, String externalKey);

    @EntityGraph(attributePaths = {"sprint", "assignee", "reporter"})
    Page<Task> findAll(Specification<Task> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"sprint", "assignee", "reporter"})
    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project"})
    @Query("select task from Task task where task.id = :id and task.project.id = :projectId")
    Optional<Task> findForTraceabilityLinkByIdAndProjectId(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId
    );

    @Query("select coalesce(sum(case when task.storyPoint is null then 1 else task.storyPoint end), 0) "
            + "from Task task where task.project.id = :projectId and task.sprint.id = :sprintId "
            + "and task.assignee.id = :studentId and task.status = com.saga.be.entity.enums.TaskStatus.DONE")
    Long sumDoneEffectiveStoryPoints(
            @Param("projectId") UUID projectId,
            @Param("sprintId") UUID sprintId,
            @Param("studentId") UUID studentId
    );
    List<Task> findByProjectId(UUID projectId);

    @EntityGraph(attributePaths = {"project", "assignee"})
    @Query("select task from Task task where task.deletedAt is null and task.dueDate is not null "
            + "and (task.status is null or task.status not in :statuses) "
            + "and (:afterId is null or task.id > :afterId) order by task.id")
    List<Task> findDeadlineEligibleTasksAfter(
            @Param("statuses") java.util.Collection<com.saga.be.entity.enums.TaskStatus> statuses,
            @Param("afterId") UUID afterId,
            Pageable pageable
    );

    List<Task> findByProjectIdAndAssigneeId(UUID projectId, UUID assigneeId);

    List<Task> findByProjectCourseId(UUID courseId);

    /**
     * Global count matching Course early-warning OVERDUE_TASK:
     * dueDate before nowUtc, status not DONE (null status counts), project and assignee present,
     * and assignee is a TeamMember of a Team that owns the task Project.
     */
    @Query("""
            select count(task)
            from Task task
            where task.dueDate is not null
              and task.dueDate < :nowUtc
              and (task.status is null or task.status <> com.saga.be.entity.enums.TaskStatus.DONE)
              and task.project is not null
              and task.assignee is not null
              and exists (
                  select 1
                  from TeamMember member
                  where member.team.project.id = task.project.id
                    and member.student.id = task.assignee.id
              )
            """)
    long countOverdueAssignedTeamMemberTasks(@Param("nowUtc") LocalDateTime nowUtc);

    boolean existsByProjectCourseIdAndAssigneeId(UUID courseId, UUID assigneeId);

    boolean existsByProjectCourseIdAndReporterId(UUID courseId, UUID reporterId);

    @EntityGraph(attributePaths = {"project", "sprint", "assignee"})
    List<Task> findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(UUID courseId);

    @EntityGraph(attributePaths = {"project", "assignee"})
    List<Task> findByProjectIdAndDeletedAtIsNullAndExternalUpdatedAtIsNotNullOrderByExternalUpdatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );

    @Query("""
            select task.assignee.id, function('date', coalesce(task.updatedAt, task.createdAt)), count(task)
            from Task task
            where task.project.id = :projectId
              and task.assignee.id in :assigneeIds
              and coalesce(task.updatedAt, task.createdAt) >= :startAt
              and coalesce(task.updatedAt, task.createdAt) < :endExclusive
            group by task.assignee.id, function('date', coalesce(task.updatedAt, task.createdAt))
            order by task.assignee.id, function('date', coalesce(task.updatedAt, task.createdAt))
            """)
    List<Object[]> aggregateDailyCountsByProjectAndAssigneeIds(
            @Param("projectId") UUID projectId,
            @Param("assigneeIds") Collection<UUID> assigneeIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
