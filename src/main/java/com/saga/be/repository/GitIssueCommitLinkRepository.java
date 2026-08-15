package com.saga.be.repository;

import com.saga.be.entity.GitIssueCommitLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitIssueCommitLinkRepository extends JpaRepository<GitIssueCommitLink, UUID> {

    boolean existsByGitIssueIdAndCommitId(UUID gitIssueId, UUID commitId);

    Optional<GitIssueCommitLink> findByGitIssueIdAndCommitId(UUID gitIssueId, UUID commitId);

    @EntityGraph(attributePaths = {"gitIssue", "gitIssue.repo", "commit"})
    List<GitIssueCommitLink> findByCommitIdOrderByGitIssueIssueNumberAscIdAsc(
            UUID commitId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"commit", "commit.repo", "commit.author"})
    List<GitIssueCommitLink> findByGitIssueIdOrderByCommitTimestampDescIdDesc(
            UUID gitIssueId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"gitIssue", "commit", "commit.repo", "commit.author"})
    List<GitIssueCommitLink> findByGitIssueIdInOrderByCommitTimestampDescIdDesc(
            List<UUID> gitIssueIds,
            Pageable pageable
    );
}
