package com.saga.be.repository;

import com.saga.be.entity.GitIssue;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface GitIssueRepository extends JpaRepository<GitIssue, UUID>,
        JpaSpecificationExecutor<GitIssue> {
    Optional<GitIssue> findByRepoIdAndGithubIssueId(
            UUID repoId,
            Long githubIssueId
    );

    Optional<GitIssue> findByRepoIdAndIssueNumber(
            UUID repoId,
            Integer issueNumber
    );

    @EntityGraph(attributePaths = {"repo", "author", "assignee"})
    Page<GitIssue> findAll(Specification<GitIssue> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"repo", "author", "assignee"})
    Optional<GitIssue> findByIdAndRepoProjectId(UUID id, UUID projectId);

    @EntityGraph(attributePaths = {"repo", "repo.project", "author", "assignee"})
    @org.springframework.data.jpa.repository.Query(
            "select issue from GitIssue issue where issue.id = :id"
    )
    Optional<GitIssue> findWithRepoProjectById(
            @org.springframework.data.repository.query.Param("id") UUID id
    );

    @EntityGraph(attributePaths = {"repo", "author", "assignee"})
    List<GitIssue> findByRepoProjectIdAndExternalUpdatedAtIsNotNullOrderByExternalUpdatedAtDescIdDesc(
            UUID projectId,
            Pageable pageable
    );
}
