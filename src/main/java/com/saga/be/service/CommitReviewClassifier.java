package com.saga.be.service;

import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;
import java.time.LocalDateTime;
import java.util.Optional;

public final class CommitReviewClassifier {

    public static final int LIVE_PRIORITY_RANK = 1;
    public static final int HISTORICAL_PRIORITY_RANK = 0;

    private CommitReviewClassifier() {
    }

    public static Optional<Classification> classify(LocalDateTime committedAt, LocalDateTime reviewCutoverAt) {
        if (reviewCutoverAt == null) {
            return Optional.empty();
        }
        if (committedAt == null || committedAt.isBefore(reviewCutoverAt)) {
            return Optional.of(new Classification(
                    CommitReviewMode.HISTORICAL_LIGHT,
                    CommitReviewPriority.LOW,
                    HISTORICAL_PRIORITY_RANK
            ));
        }
        return Optional.of(new Classification(
                CommitReviewMode.LIVE_TASK_AWARE,
                CommitReviewPriority.HIGH,
                LIVE_PRIORITY_RANK
        ));
    }

    public record Classification(
            CommitReviewMode mode,
            CommitReviewPriority priority,
            int priorityRank
    ) {
    }
}
