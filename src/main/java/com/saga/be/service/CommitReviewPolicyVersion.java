package com.saga.be.service;

import com.saga.be.entity.enums.CommitReviewMode;
import com.saga.be.entity.enums.CommitReviewPriority;

public enum CommitReviewPolicyVersion {
    HISTORICAL_V1("commit-review-historical-v1", CommitReviewPriority.LOW, CommitReviewMode.HISTORICAL_LIGHT),
    LIVE_TASK_AWARE_V1(
            "commit-review-live-task-aware-v1",
            CommitReviewPriority.HIGH,
            CommitReviewMode.LIVE_TASK_AWARE
    );

    private final String wireValue;
    private final CommitReviewPriority requiredPriority;
    private final CommitReviewMode mode;

    CommitReviewPolicyVersion(String wireValue, CommitReviewPriority requiredPriority, CommitReviewMode mode) {
        this.wireValue = wireValue;
        this.requiredPriority = requiredPriority;
        this.mode = mode;
    }

    public String wireValue() {
        return wireValue;
    }

    public CommitReviewPriority requiredPriority() {
        return requiredPriority;
    }

    public CommitReviewMode mode() {
        return mode;
    }

    public static CommitReviewPolicyVersion requireExact(String policy, CommitReviewPriority priority) {
        if (policy == null || priority == null) {
            throw new CommitReviewContractRejected("AI_REVIEW_POLICY_INVALID");
        }
        for (CommitReviewPolicyVersion value : values()) {
            if (value.wireValue.equals(policy)) {
                if (value.requiredPriority != priority) {
                    throw new CommitReviewContractRejected("AI_REVIEW_PRIORITY_POLICY_MISMATCH");
                }
                return value;
            }
        }
        throw new CommitReviewContractRejected("AI_REVIEW_POLICY_UNKNOWN");
    }

    public static CommitReviewPolicyVersion forMode(CommitReviewMode mode) {
        if (mode == null) {
            throw new CommitReviewContractRejected("AI_REVIEW_POLICY_INVALID");
        }
        return switch (mode) {
            case HISTORICAL_LIGHT -> HISTORICAL_V1;
            case LIVE_TASK_AWARE -> LIVE_TASK_AWARE_V1;
        };
    }

    public static final class CommitReviewContractRejected extends RuntimeException {
        private final String code;

        public CommitReviewContractRejected(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
