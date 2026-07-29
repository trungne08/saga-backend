package com.saga.be.integration.provider;

import java.util.List;

public record JiraIssuePage(
        List<JiraIssueSnapshot> issues,
        String nextPageToken,
        boolean last
) {
}
