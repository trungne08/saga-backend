package com.saga.be.integration.provider;

public record GitHubBranchInfo(String name, String headSha, boolean protectedBranch) {}
