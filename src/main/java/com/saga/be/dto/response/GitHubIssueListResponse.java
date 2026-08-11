package com.saga.be.dto.response;

import java.util.List;

public record GitHubIssueListResponse(
        List<GitHubIssueSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Summary summary
) {

    public GitHubIssueListResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public record Summary(
            long open,
            long closed,
            long assignedToMe,
            long unassigned
    ) {
    }
}
