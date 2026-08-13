package com.saga.be.service;

public record CommitReviewContextLimits(
        int maxChangedFiles,
        int maxPatchCharsPerFile,
        int maxTotalContextChars
) {
    public static final int DEFAULT_MAX_CHANGED_FILES = 50;
    public static final int DEFAULT_MAX_PATCH_CHARS_PER_FILE = 20_000;
    public static final int DEFAULT_MAX_TOTAL_CONTEXT_CHARS = 100_000;

    public CommitReviewContextLimits {
        if (maxChangedFiles < 1
                || maxPatchCharsPerFile < 1
                || maxTotalContextChars < 1) {
            throw new IllegalArgumentException("Commit review context limits must be positive");
        }
    }

    public static CommitReviewContextLimits defaults() {
        return new CommitReviewContextLimits(
                DEFAULT_MAX_CHANGED_FILES,
                DEFAULT_MAX_PATCH_CHARS_PER_FILE,
                DEFAULT_MAX_TOTAL_CONTEXT_CHARS
        );
    }
}
