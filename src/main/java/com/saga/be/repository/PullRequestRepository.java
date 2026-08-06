package com.saga.be.repository;

import com.saga.be.entity.PullRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {
    long countByRepoProjectId(UUID projectId);
    Optional<PullRequest> findByRepoIdAndGithubPullRequestId(
            UUID repoId,
            Long githubPullRequestId
    );

    Optional<PullRequest> findByRepoIdAndPullNumber(
            UUID repoId,
            Integer pullNumber
    );
}
