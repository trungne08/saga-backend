package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommitReviewResultParserTest {

    @Test
    void unknownEnumIsRejectedAndFailedJobIsNotNeedsChanges() {
        assertThrows(CommitReviewResultParser.CommitReviewResultRejected.class,
                () -> CommitReviewResultParser.parse(Map.of(
                        "kind", "SOMETHING_NEW",
                        "messageQuality", "GOOD",
                        "confidence", "HIGH",
                        "traceability", "NOT_PROVEN"
                )));
        CommitReviewResultParser.ParsedResult linked = CommitReviewResultParser.parse(Map.of(
                "kind", "LIVE_TASK_LINKED",
                "messageQuality", "GOOD",
                "confidence", "HIGH",
                "traceability", "EXPLICIT_LINKS_PRESENT",
                "verdictStatus", "NEEDS_CHANGES",
                "findings", List.of(),
                "evidenceRefs", List.of()
        ));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("CANCELLED", linked));
        assertTrue(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", linked));
    }

    @Test
    void advisoryAndHistoricalAreNotNeedsChangesOrRepeatedIssueWindow() {
        var advisory = CommitReviewResultParser.parse(Map.of(
                "kind", "LIVE_UNLINKED_ADVISORY",
                "messageQuality", "POOR",
                "confidence", "LOW",
                "traceability", "NOT_PROVEN"
        ));
        var historical = CommitReviewResultParser.parse(Map.of(
                "kind", "HISTORICAL",
                "messageQuality", "WEAK",
                "confidence", "MEDIUM",
                "traceability", "NOT_PROVEN"
        ));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", historical));
        assertTrue(CommitReviewResultParser.eligibleForUnlinkedAdvisory("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("COMPLETED", historical));
        assertFalse(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("FAILED", advisory));
        assertEquals("POOR", advisory.messageQuality());
    }
}
