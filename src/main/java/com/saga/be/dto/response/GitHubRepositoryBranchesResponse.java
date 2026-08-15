package com.saga.be.dto.response;

import java.util.List;
import java.util.UUID;

public record GitHubRepositoryBranchesResponse(UUID repositoryId, String repositoryName, Page branches) {
    public record Branch(String name, String headSha, boolean protectedBranch) {}
    public record Page(List<Branch> content, int page, int size, boolean hasNext) {}
}
