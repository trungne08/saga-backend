package com.saga.be.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GitHubRepositoryCommitsResponse(UUID repositoryId, String branch, Page commits) {
    public record Commit(String sha, String message, String authorName, String authorLogin,
                         Instant authoredAt, Instant committedAt, String url) {}
    public record Page(List<Commit> content, int page, int size, boolean hasNext) {}
}
