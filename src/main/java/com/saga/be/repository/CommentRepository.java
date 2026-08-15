package com.saga.be.repository;

import com.saga.be.entity.Comment;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    Optional<Comment> findBySourceSystemAndExternalCommentId(
            String sourceSystem,
            String externalCommentId
    );

    @Query("""
            select comment.author.id, function('date', comment.createdAt), count(comment)
            from Comment comment
            where comment.author.id in :authorIds
              and comment.createdAt >= :startAt
              and comment.createdAt < :endExclusive
              and (
                   (comment.task is not null and comment.task.project.id = :projectId)
                or (comment.pullRequest is not null and comment.pullRequest.repo.project.id = :projectId)
                or (comment.gitIssue is not null and comment.gitIssue.repo.project.id = :projectId)
              )
            group by comment.author.id, function('date', comment.createdAt)
            order by comment.author.id, function('date', comment.createdAt)
            """)
    List<Object[]> aggregateDailyCountsByProjectAndAuthorIds(
            @Param("projectId") UUID projectId,
            @Param("authorIds") Collection<UUID> authorIds,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    @EntityGraph(attributePaths = {
            "author",
            "parentComment",
            "parentComment.author",
            "task",
            "task.assignee",
            "task.reporter",
            "pullRequest",
            "pullRequest.repo",
            "gitIssue",
            "gitIssue.repo"
    })
    List<Comment> findByTaskProjectIdOrderByCreatedAtAscIdAsc(UUID projectId);

    @EntityGraph(attributePaths = {
            "author",
            "parentComment",
            "parentComment.author",
            "task",
            "task.assignee",
            "task.reporter",
            "pullRequest",
            "pullRequest.repo",
            "gitIssue",
            "gitIssue.repo"
    })
    List<Comment> findByPullRequestRepoProjectIdOrderByCreatedAtAscIdAsc(UUID projectId);

    @EntityGraph(attributePaths = {
            "author",
            "parentComment",
            "parentComment.author",
            "task",
            "task.assignee",
            "task.reporter",
            "pullRequest",
            "pullRequest.repo",
            "gitIssue",
            "gitIssue.repo"
    })
    List<Comment> findByGitIssueRepoProjectIdOrderByCreatedAtAscIdAsc(UUID projectId);
}
