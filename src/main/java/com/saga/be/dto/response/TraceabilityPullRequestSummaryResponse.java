package com.saga.be.dto.response;

import com.saga.be.entity.GitIssuePullRequestLink;
import com.saga.be.entity.PullRequest;
import com.saga.be.entity.enums.PullRequestStatus;
import com.saga.be.entity.enums.TraceabilityRelationType;
import java.time.LocalDateTime;
import java.util.UUID;

public record TraceabilityPullRequestSummaryResponse(
        UUID id,
        Integer pullNumber,
        String title,
        PullRequestStatus status,
        GitHubIssueSummaryResponse.RepositoryReference repository,
        GitHubIssueSummaryResponse.StudentReference author,
        TraceabilityRelationType relationType,
        LocalDateTime externalUpdatedAt,
        LocalDateTime mergedAt
) {

    public static TraceabilityPullRequestSummaryResponse from(
            GitIssuePullRequestLink link
    ) {
        PullRequest pull = link.getPullRequest();
        return new TraceabilityPullRequestSummaryResponse(
                pull.getId(),
                pull.getPullNumber(),
                pull.getTitle(),
                pull.getStatus(),
                new GitHubIssueSummaryResponse.RepositoryReference(
                        pull.getRepo().getRepositoryId(),
                        pull.getRepo().getFullName()
                ),
                GitHubIssueSummaryResponse.StudentReference.from(pull.getAuthor()),
                link.getRelationType(),
                pull.getExternalUpdatedAt(),
                pull.getMergedAt()
        );
    }
}
