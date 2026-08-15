package com.saga.be.dto.response;

import java.util.List;

public record TaskTraceabilityResponse(
        TraceabilityTaskSummaryResponse task,
        TraceabilityLinksResponse<ImplementationTrace> linkedIssues,
        List<TraceabilityTimelineEventResponse> timeline
) {

    public TaskTraceabilityResponse {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    public record ImplementationTrace(
            GitHubIssueSummaryResponse issue,
            TraceabilityLinksResponse<TraceabilityPullRequestSummaryResponse> linkedPullRequests,
            TraceabilityLinksResponse<TraceabilityCommitSummaryResponse> linkedCommits
    ) {
    }
}
