package com.saga.be.integration.sync;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Keeps the Jira JQL minute-granularity window separate from the exact local
 * timestamp used as the committed sync cursor.
 */
final class JiraSyncWindow {

    private JiraSyncWindow() {
    }

    static LocalDateTime lowerBoundForJql(LocalDateTime effectiveLowerBound) {
        return effectiveLowerBound == null
                ? null
                : effectiveLowerBound.truncatedTo(ChronoUnit.MINUTES);
    }

    static LocalDateTime upperBoundExclusiveForJql(
            LocalDateTime capturedUpperBound
    ) {
        return capturedUpperBound.truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1);
    }

    static boolean isWithinCapturedUpperBound(
            LocalDateTime issueUpdatedAt,
            LocalDateTime capturedUpperBound
    ) {
        return issueUpdatedAt != null
                && !issueUpdatedAt.isAfter(capturedUpperBound);
    }
}
