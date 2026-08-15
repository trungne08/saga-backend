package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CommitReviewClassifierTest {

    private static final LocalDateTime CUTOVER = LocalDateTime.parse("2026-08-01T00:00:00");

    @Test
    void commitBeforeCutoverIsHistoricalLow() {
        var classification = CommitReviewClassifier.classify(
                CUTOVER.minusSeconds(1), CUTOVER).orElseThrow();
        assertEquals(CommitReviewMode.HISTORICAL_LIGHT, classification.mode());
        assertEquals(CommitReviewPriority.LOW, classification.priority());
        assertEquals(0, classification.priorityRank());
    }

    @Test
    void commitAtOrAfterCutoverIsLiveHigh() {
        var atCutover = CommitReviewClassifier.classify(CUTOVER, CUTOVER).orElseThrow();
        var after = CommitReviewClassifier.classify(CUTOVER.plusSeconds(1), CUTOVER).orElseThrow();
        assertEquals(CommitReviewMode.LIVE_TASK_AWARE, atCutover.mode());
        assertEquals(CommitReviewPriority.HIGH, atCutover.priority());
        assertEquals(CommitReviewMode.LIVE_TASK_AWARE, after.mode());
        assertEquals(CommitReviewPriority.HIGH, after.priority());
    }

    @Test
    void missingCutoverIsFailClosed() {
        assertTrue(CommitReviewClassifier.classify(CUTOVER, null).isEmpty());
    }

    @Test
    void missingCommitTimestampIsHistoricalLow() {
        var classification = CommitReviewClassifier.classify(null, CUTOVER).orElseThrow();
        assertEquals(CommitReviewMode.HISTORICAL_LIGHT, classification.mode());
        assertEquals(CommitReviewPriority.LOW, classification.priority());
    }
}
