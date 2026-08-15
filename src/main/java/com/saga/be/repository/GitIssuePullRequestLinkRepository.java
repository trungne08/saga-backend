package com.saga.be.repository;

import com.saga.be.entity.GitIssuePullRequestLink;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitIssuePullRequestLinkRepository
        extends JpaRepository<GitIssuePullRequestLink, UUID> {

    @EntityGraph(attributePaths = {"pullRequest", "pullRequest.repo", "pullRequest.author"})
    List<GitIssuePullRequestLink> findByGitIssueIdOrderByPullRequestPullNumberAscIdAsc(
            UUID gitIssueId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"gitIssue", "pullRequest", "pullRequest.repo", "pullRequest.author"})
    List<GitIssuePullRequestLink> findByGitIssueIdInOrderByPullRequestPullNumberAscIdAsc(
            List<UUID> gitIssueIds,
            Pageable pageable
    );
}
