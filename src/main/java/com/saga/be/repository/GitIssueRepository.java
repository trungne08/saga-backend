package com.saga.be.repository;

import com.saga.be.entity.GitIssue;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitIssueRepository extends JpaRepository<GitIssue, UUID> {
    Optional<GitIssue> findByRepoIdAndGithubIssueId(
            UUID repoId,
            Long githubIssueId
    );

    Optional<GitIssue> findByRepoIdAndIssueNumber(
            UUID repoId,
            Integer issueNumber
    );
}
