package com.saga.be.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CommitReviewResultParser {

    private static final Set<String> KINDS = Set.of(
            "HISTORICAL",
            "LIVE_TASK_LINKED",
            "LIVE_UNLINKED_ADVISORY"
    );
    private static final Set<String> MESSAGE_QUALITIES = Set.of("GOOD", "WEAK", "POOR");
    private static final Set<String> CONFIDENCES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> TRACEABILITIES = Set.of("EXPLICIT_LINKS_PRESENT", "NOT_PROVEN");
    private static final Set<String> VERDICTS = Set.of("PASS", "NEEDS_CHANGES", "INELIGIBLE");
    private static final Set<String> JOB_STATUSES = Set.of(
            "PENDING", "RUNNING", "WAITING_RETRY", "COMPLETED", "FAILED", "CANCELLED"
    );

    private CommitReviewResultParser() {
    }

    public static ParsedResult parse(Map<String, Object> result) {
        if (result == null) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_MISSING");
        }
        String kind = requiredEnum(result, "kind", KINDS);
        String messageQuality = requiredEnum(result, "messageQuality", MESSAGE_QUALITIES);
        String confidence = requiredEnum(result, "confidence", CONFIDENCES);
        String traceability = requiredEnum(result, "traceability", TRACEABILITIES);
        String verdictStatus = optionalEnum(result, "verdictStatus", VERDICTS);
        Object findings = result.get("findings");
        Object evidenceRefs = result.get("evidenceRefs");
        if (findings != null && !(findings instanceof List<?>)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_FINDINGS_INVALID");
        }
        if (evidenceRefs != null && !(evidenceRefs instanceof List<?>)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_EVIDENCE_INVALID");
        }
        return new ParsedResult(
                kind,
                messageQuality,
                confidence,
                traceability,
                verdictStatus,
                findings instanceof List<?> list ? List.copyOf(list) : List.of(),
                evidenceRefs instanceof List<?> list ? List.copyOf(list) : List.of()
        );
    }

    public static String requireJobStatus(String jobStatus) {
        if (jobStatus == null || jobStatus.isBlank() || !JOB_STATUSES.contains(jobStatus.trim())) {
            throw new CommitReviewResultRejected("AI_REVIEW_JOB_STATUS_UNKNOWN");
        }
        return jobStatus.trim();
    }

    public static boolean eligibleForNeedsChangesWarning(String jobStatus, ParsedResult result) {
        if (jobStatus == null || !JOB_STATUSES.contains(jobStatus)) {
            return false;
        }
        if ("FAILED".equals(jobStatus) || "CANCELLED".equals(jobStatus)) {
            return false;
        }
        if (!"COMPLETED".equals(jobStatus) || result == null) {
            return false;
        }
        return "LIVE_TASK_LINKED".equals(result.kind())
                && "NEEDS_CHANGES".equals(result.verdictStatus());
    }

    public static boolean eligibleForUnlinkedAdvisory(String jobStatus, ParsedResult result) {
        if (!"COMPLETED".equals(jobStatus) || result == null) {
            return false;
        }
        return "LIVE_UNLINKED_ADVISORY".equals(result.kind());
    }

    public static boolean countsTowardRepeatedIssuesWindow(String jobStatus, ParsedResult result) {
        return "COMPLETED".equals(jobStatus)
                && result != null
                && "LIVE_TASK_LINKED".equals(result.kind())
                && ("PASS".equals(result.verdictStatus()) || "NEEDS_CHANGES".equals(result.verdictStatus()));
    }

    private static String requiredEnum(Map<String, Object> result, String field, Set<String> allowed) {
        Object value = result.get(field);
        if (!(value instanceof String raw) || raw.isBlank()) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_MISSING");
        }
        String normalized = raw.trim();
        if (!allowed.contains(normalized)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_UNKNOWN");
        }
        return normalized;
    }

    private static String optionalEnum(Map<String, Object> result, String field, Set<String> allowed) {
        Object value = result.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String raw) || raw.isBlank()) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        String normalized = raw.trim();
        if (!allowed.contains(normalized)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_UNKNOWN");
        }
        return normalized;
    }

    public record ParsedResult(
            String kind,
            String messageQuality,
            String confidence,
            String traceability,
            String verdictStatus,
            List<?> findings,
            List<?> evidenceRefs
    ) {
    }

    public static final class CommitReviewResultRejected extends RuntimeException {
        private final String code;

        public CommitReviewResultRejected(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
