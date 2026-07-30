package com.saga.be.integration.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class JiraSyncWindowTest {

    @Test
    void floorsLowerBoundToMinuteAndBuildsExclusiveNextMinuteUpperBound() {
        assertEquals(
                LocalDateTime.parse("2026-07-30T17:46:00"),
                JiraSyncWindow.lowerBoundForJql(
                        LocalDateTime.parse("2026-07-30T17:46:18.987")
                )
        );
        assertEquals(
                LocalDateTime.parse("2026-07-31T00:57:00"),
                JiraSyncWindow.upperBoundExclusiveForJql(
                        LocalDateTime.parse("2026-07-31T00:56:10")
                )
        );
        assertEquals(
                LocalDateTime.parse("2026-07-31T00:57:00"),
                JiraSyncWindow.upperBoundExclusiveForJql(
                        LocalDateTime.parse("2026-07-31T00:56:00")
                )
        );
    }

    @Test
    void filtersOnlyIssuesNewerThanCapturedUpperBoundAndKeepsThemForNextJob() {
        LocalDateTime captured = LocalDateTime.parse("2026-07-31T00:56:10");
        LocalDateTime atCapture = LocalDateTime.parse("2026-07-31T00:56:10");
        LocalDateTime afterCapture = LocalDateTime.parse("2026-07-31T00:56:50");

        assertTrue(JiraSyncWindow.isWithinCapturedUpperBound(atCapture, captured));
        assertFalse(
                JiraSyncWindow.isWithinCapturedUpperBound(afterCapture, captured)
        );
        assertTrue(JiraSyncWindow.isWithinCapturedUpperBound(
                afterCapture,
                LocalDateTime.parse("2026-07-31T00:57:10")
        ));
    }
}
