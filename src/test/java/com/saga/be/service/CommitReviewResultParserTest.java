package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommitReviewResultParserTest {

    @Test
    void unknownEnumAndUnknownSchemaAreRejected() {
        assertThrows(CommitReviewResultParser.CommitReviewResultRejected.class,
                () -> CommitReviewResultParser.parse(Map.of("kind", "SOMETHING_NEW")));
        Map<String, Object> unknownMode = linkedNeedsChanges();
        unknownMode.put("reviewMode", "BRAND_NEW");
        assertThrows(CommitReviewResultParser.CommitReviewResultRejected.class,
                () -> CommitReviewResultParser.parse(unknownMode));
    }

    @Test
    void completedLinkedNeedsChangesIsWarningEligibleAndFailedCancelledAreNot() {
        CommitReviewResultParser.ParsedResult linked = CommitReviewResultParser.parse(linkedNeedsChanges());
        assertEquals("commit-review-result-v2", linked.schemaVersion());
        assertEquals("TASK_LINKED", linked.reviewMode());
        assertEquals("NEEDS_CHANGES", linked.verdict());
        assertTrue(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", linked));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("FAILED", linked));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("CANCELLED", linked));
        assertTrue(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("COMPLETED", linked));
    }

    @Test
    void advisoryAndHistoricalAreNotNeedsChangesOrRepeatedIssueWindow() {
        var advisory = CommitReviewResultParser.parse(unlinkedAdvisory());
        var historical = CommitReviewResultParser.parse(historicalLight());
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.eligibleForNeedsChangesWarning("COMPLETED", historical));
        assertTrue(CommitReviewResultParser.eligibleForUnlinkedAdvisory("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("COMPLETED", advisory));
        assertFalse(CommitReviewResultParser.countsTowardRepeatedIssuesWindow("COMPLETED", historical));
        assertTrue(CommitReviewResultParser.historicalLight("COMPLETED", historical));
        assertEquals("POOR", advisory.messageQuality());
    }

    @Test
    void modeMismatchIsRejected() {
        Map<String, Object> mismatched = unlinkedAdvisory();
        mismatched.put("reviewMode", "TASK_LINKED");
        assertThrows(CommitReviewResultParser.CommitReviewResultRejected.class,
                () -> CommitReviewResultParser.parse(mismatched));
    }

    static Map<String, Object> linkedNeedsChanges() {
        return base(
                "TASK_LINKED",
                "VERIFIED",
                "GOOD",
                "RISKS",
                "NEEDS_CHANGES",
                true,
                "NEEDS_CHANGES",
                "NEEDS_CHANGES",
                "PROVEN"
        );
    }

    static Map<String, Object> linkedPass() {
        return base(
                "TASK_LINKED",
                "VERIFIED",
                "GOOD",
                "GOOD",
                "ALIGNED",
                true,
                "PASS",
                "PASS",
                "PROVEN"
        );
    }

    static Map<String, Object> unlinkedAdvisory() {
        return base(
                "UNLINKED_ADVISORY",
                "NOT_PROVEN",
                "POOR",
                "RISKS",
                "NOT_EVALUATED",
                false,
                "ADVISORY_ONLY",
                "INSUFFICIENT_CONTEXT",
                "NOT_PROVEN"
        );
    }

    static Map<String, Object> historicalLight() {
        return base(
                "HISTORICAL_LIGHT",
                "NOT_REQUIRED",
                "WEAK",
                "INSUFFICIENT_CONTEXT",
                "NOT_REQUIRED",
                false,
                "ADVISORY_ONLY",
                "INSUFFICIENT_CONTEXT",
                "NOT_PROVEN"
        );
    }

    private static Map<String, Object> base(
            String reviewMode,
            String traceability,
            String messageQuality,
            String codeQuality,
            String alignment,
            boolean eligible,
            String verdict,
            String overall,
            String coverage
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("schemaVersion", "commit-review-result-v2");
        result.put("reviewMode", reviewMode);
        result.put("traceability", Map.of("status", traceability));
        result.put("commitMessageAssessment", Map.of("quality", messageQuality));
        result.put("codeAssessment", Map.of("quality", codeQuality));
        result.put("taskAlignment", Map.of("status", alignment));
        result.put("verdict", Map.of("eligible", eligible, "status", verdict));
        result.put("overallStatus", overall);
        result.put("requirementCoverage", coverage);
        result.put("verificationFacts", Map.of("commitPresent", true));
        result.put("findings", List.of());
        result.put("evidenceRefs", List.of());
        return result;
    }
}
