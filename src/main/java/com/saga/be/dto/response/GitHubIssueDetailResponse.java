package com.saga.be.dto.response;

import java.util.List;

public record GitHubIssueDetailResponse(
        GitHubIssueSummaryResponse issue,
        TraceabilityLinksResponse<TraceabilityTaskSummaryResponse> linkedTasks,
        TraceabilityLinksResponse<TraceabilityPullRequestSummaryResponse> linkedPullRequests,
        TraceabilityLinksResponse<TraceabilityCommitSummaryResponse> linkedCommits,
        List<TraceabilityTimelineEventResponse> timeline
) {

    public GitHubIssueDetailResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }
}
