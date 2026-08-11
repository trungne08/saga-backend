package com.saga.be.repository;

import com.saga.be.entity.TaskGitIssueLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskGitIssueLinkRepository extends JpaRepository<TaskGitIssueLink, UUID> {

    boolean existsByTaskIdAndGitIssueId(UUID taskId, UUID gitIssueId);

    Optional<TaskGitIssueLink> findByTaskIdAndGitIssueId(UUID taskId, UUID gitIssueId);

    @EntityGraph(attributePaths = {"gitIssue", "gitIssue.repo", "gitIssue.author", "gitIssue.assignee"})
    List<TaskGitIssueLink> findByTaskIdOrderByGitIssueIssueNumberAscIdAsc(
            UUID taskId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"task", "task.assignee"})
    List<TaskGitIssueLink> findByGitIssueIdOrderByTaskExternalKeyAscIdAsc(
            UUID gitIssueId,
            Pageable pageable
    );
}
