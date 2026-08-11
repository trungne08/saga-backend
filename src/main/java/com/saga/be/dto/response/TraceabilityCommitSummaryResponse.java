package com.saga.be.dto.response;

import com.saga.be.entity.CommitData;
import com.saga.be.entity.GitIssueCommitLink;
import com.saga.be.entity.enums.TraceabilityRelationType;
import java.time.LocalDateTime;
import java.util.UUID;

public record TraceabilityCommitSummaryResponse(
        UUID id,
        String sha,
        String message,
        GitHubIssueSummaryResponse.RepositoryReference repository,
        GitHubIssueSummaryResponse.StudentReference author,
        TraceabilityRelationType relationType,
        LocalDateTime committedAt,
        Integer additions,
        Integer deletions,
        Integer filesChanged
) {

    public static TraceabilityCommitSummaryResponse from(GitIssueCommitLink link) {
        CommitData commit = link.getCommit();
        return new TraceabilityCommitSummaryResponse(
                commit.getId(),
                commit.getShaHash(),
                commit.getMessage(),
                new GitHubIssueSummaryResponse.RepositoryReference(
                        commit.getRepo().getRepositoryId(),
                        commit.getRepo().getFullName()
                ),
                GitHubIssueSummaryResponse.StudentReference.from(commit.getAuthor()),
                link.getRelationType(),
                commit.getTimestamp(),
                commit.getAdditions(),
                commit.getDeletions(),
                commit.getFilesChanged()
        );
    }
}
