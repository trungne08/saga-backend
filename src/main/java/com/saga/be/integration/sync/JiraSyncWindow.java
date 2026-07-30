package com.saga.be.integration.sync;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Keeps the Jira JQL minute-granularity window separate from the exact local
 * timestamp used as the committed sync cursor.
 */
public final class JiraSyncWindow {

    private JiraSyncWindow() {
    }

    public static Instant effectiveLowerBound(
            Instant cursorBeforeUtc,
            Instant capturedUpperBoundUtc,
            Duration overlapWindow
    ) {
        if (cursorBeforeUtc == null) {
            return null;
        }
        Instant lowerBound = cursorBeforeUtc.minus(overlapWindow);
        return !lowerBound.isBefore(capturedUpperBoundUtc)
                ? capturedUpperBoundUtc.minus(overlapWindow)
                : lowerBound;
    }

    public static LocalDateTime lowerBoundForJql(
            Instant effectiveLowerBoundUtc,
            ZoneId jiraZoneId
    ) {
        return effectiveLowerBoundUtc == null
                ? null
                : effectiveLowerBoundUtc.atZone(jiraZoneId)
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.MINUTES);
    }

    public static LocalDateTime upperBoundExclusiveForJql(
            Instant capturedUpperBoundUtc,
            ZoneId jiraZoneId
    ) {
        return capturedUpperBoundUtc.atZone(jiraZoneId)
                .toLocalDateTime()
                .truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(1);
    }

    public static boolean isWithinCapturedUpperBound(
            Instant issueUpdatedAtUtc,
            Instant capturedUpperBoundUtc
    ) {
        return issueUpdatedAtUtc != null
                && !issueUpdatedAtUtc.isAfter(capturedUpperBoundUtc);
    }
}
