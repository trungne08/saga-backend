package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class JiraSyncWindowTest {

    @Test
    void convertsUtcBoundsToJiraZoneAndBuildsExclusiveNextMinuteUpperBound() {
        assertEquals(
                LocalDateTime.parse("2026-07-31T04:22:00"),
                JiraSyncWindow.lowerBoundForJql(
                        Instant.parse("2026-07-30T21:22:18.987Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")
                )
        );
        assertEquals(
                LocalDateTime.parse("2026-07-31T04:23:00"),
                JiraSyncWindow.upperBoundExclusiveForJql(
                        Instant.parse("2026-07-30T21:22:10Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")
                )
        );
        assertEquals(
                LocalDateTime.parse("2026-07-31T04:23:00"),
                JiraSyncWindow.upperBoundExclusiveForJql(
                        Instant.parse("2026-07-30T21:22:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")
                )
        );
    }

    @Test
    void filtersOnlyIssuesNewerThanCapturedUpperBoundAndKeepsThemForNextJob() {
        Instant captured = Instant.parse("2026-07-31T00:56:10Z");
        Instant atCapture = captured;
        Instant afterCapture = Instant.parse("2026-07-31T00:56:50Z");

        assertTrue(JiraSyncWindow.isWithinCapturedUpperBound(atCapture, captured));
        assertFalse(
                JiraSyncWindow.isWithinCapturedUpperBound(afterCapture, captured)
        );
        assertTrue(JiraSyncWindow.isWithinCapturedUpperBound(
                afterCapture,
                Instant.parse("2026-07-31T00:57:10Z")
        ));
    }

    @Test
    void resetsAnInvertedCursorWindowToTheOverlapBeforeCapture() {
        Instant captured = Instant.parse("2026-07-31T00:56:10Z");
        assertEquals(
                captured.minus(Duration.ofMinutes(5)),
                JiraSyncWindow.effectiveLowerBound(
                        Instant.parse("2026-07-31T01:10:00Z"),
                        captured,
                        Duration.ofMinutes(5)
                )
        );
    }
}
