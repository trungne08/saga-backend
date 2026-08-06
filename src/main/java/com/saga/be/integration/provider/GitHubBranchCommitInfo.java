package com.saga.be.integration.provider;

import java.time.Instant;

public record GitHubBranchCommitInfo(String sha, String message, String authorName,
        String authorLogin, Instant authoredAt, Instant committedAt, String url) {}
