package com.saga.be.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CommitReviewResultParser {

    public static final String SCHEMA_VERSION = "commit-review-result-v2";

    private static final Set<String> JOB_STATUSES = Set.of(
            "PENDING", "RUNNING", "WAITING_RETRY", "COMPLETED", "FAILED", "CANCELLED"
    );
    private static final Set<String> REVIEW_MODES = Set.of(
            "HISTORICAL_LIGHT", "TASK_LINKED", "UNLINKED_ADVISORY"
    );
    private static final Set<String> TRACEABILITIES = Set.of(
            "VERIFIED", "NOT_PROVEN", "NOT_REQUIRED"
    );
    private static final Set<String> MESSAGE_QUALITIES = Set.of("GOOD", "WEAK", "POOR");
    private static final Set<String> CODE_QUALITIES = Set.of("GOOD", "RISKS", "INSUFFICIENT_CONTEXT");
    private static final Set<String> CONFIDENCES = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> TASK_ALIGNMENTS = Set.of(
            "ALIGNED", "NEEDS_CHANGES", "INSUFFICIENT_CONTEXT", "NOT_EVALUATED", "NOT_REQUIRED"
    );
    private static final Set<String> VERDICTS = Set.of(
            "PASS", "NEEDS_CHANGES", "INSUFFICIENT_CONTEXT", "ADVISORY_ONLY"
    );
    private static final Set<String> OVERALL = Set.of("PASS", "NEEDS_CHANGES", "INSUFFICIENT_CONTEXT");
    private static final Set<String> COVERAGES = Set.of("PROVEN", "NOT_PROVEN", "CONFLICTING_EVIDENCE");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARNING", "ERROR");
    private static final int MAX_SAFE_FINDINGS = 50;
    private static final int MAX_SAFE_TEXT = 500;

    private CommitReviewResultParser() {
    }

    public static ParsedResult parse(Map<String, Object> result) {
        if (result == null) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_MISSING");
        }
        rejectUnknownTopLevel(result);
        String schemaVersion = requiredEnum(result, "schemaVersion", Set.of(SCHEMA_VERSION));
        String reviewMode = requiredEnum(result, "reviewMode", REVIEW_MODES);
        Map<String, Object> traceability = requiredObject(result, "traceability");
        String traceabilityStatus = requiredEnum(traceability, "status", TRACEABILITIES);
        Map<String, Object> message = requiredObject(result, "commitMessageAssessment");
        String messageQuality = requiredEnum(message, "quality", MESSAGE_QUALITIES);
        Map<String, Object> code = requiredObject(result, "codeAssessment");
        String codeQuality = requiredEnum(code, "quality", CODE_QUALITIES);
        Map<String, Object> alignment = requiredObject(result, "taskAlignment");
        String taskAlignment = requiredEnum(alignment, "status", TASK_ALIGNMENTS);
        Map<String, Object> verdictObject = requiredObject(result, "verdict");
        boolean eligible = requiredBoolean(verdictObject, "eligible");
        String verdict = requiredEnum(verdictObject, "status", VERDICTS);
        String overallStatus = requiredEnum(result, "overallStatus", OVERALL);
        requiredEnum(result, "requirementCoverage", COVERAGES);
        requiredObject(result, "verificationFacts");
        String policyVersion = optionalString(result, "reviewPolicyVersion");
        String inferredLabel = null;
        String inferredConfidence = null;
        Object inferredRaw = result.get("inferredFunction");
        if (inferredRaw != null) {
            if (!(inferredRaw instanceof Map<?, ?> inferredMap)) {
                throw new CommitReviewResultRejected("AI_REVIEW_RESULT_INFERREDFUNCTION_INVALID");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> inferred = (Map<String, Object>) inferredMap;
            inferredLabel = requiredEnum(inferred, "label", Set.of("HYPOTHESIS_ONLY"));
            inferredConfidence = requiredEnum(inferred, "confidence", CONFIDENCES);
        }
        List<Map<String, Object>> findings = safeFindings(result.get("findings"));
        List<String> evidenceRefs = stringList(result.get("evidenceRefs"));
        requireModeConsistency(reviewMode, traceabilityStatus, taskAlignment, eligible, verdict);
        return new ParsedResult(
                schemaVersion,
                reviewMode,
                traceabilityStatus,
                messageQuality,
                codeQuality,
                inferredLabel,
                inferredConfidence,
                taskAlignment,
                eligible,
                verdict,
                overallStatus,
                policyVersion,
                findings,
                evidenceRefs
        );
    }

    public static String requireJobStatus(String jobStatus) {
        if (jobStatus == null || jobStatus.isBlank() || !JOB_STATUSES.contains(jobStatus.trim())) {
            throw new CommitReviewResultRejected("AI_REVIEW_JOB_STATUS_UNKNOWN");
        }
        return jobStatus.trim();
    }

    public static boolean eligibleForNeedsChangesWarning(String jobStatus, ParsedResult result) {
        if (!"COMPLETED".equals(jobStatus) || result == null) {
            return false;
        }
        return "TASK_LINKED".equals(result.reviewMode())
                && result.verdictEligible()
                && "NEEDS_CHANGES".equals(result.verdict());
    }

    public static boolean eligibleForUnlinkedAdvisory(String jobStatus, ParsedResult result) {
        if (!"COMPLETED".equals(jobStatus) || result == null) {
            return false;
        }
        return "UNLINKED_ADVISORY".equals(result.reviewMode())
                && "ADVISORY_ONLY".equals(result.verdict())
                && "NOT_PROVEN".equals(result.traceabilityStatus())
                && "NOT_EVALUATED".equals(result.taskAlignment());
    }

    public static boolean countsTowardRepeatedIssuesWindow(String jobStatus, ParsedResult result) {
        return "COMPLETED".equals(jobStatus)
                && result != null
                && "TASK_LINKED".equals(result.reviewMode())
                && result.verdictEligible()
                && ("PASS".equals(result.verdict()) || "NEEDS_CHANGES".equals(result.verdict()));
    }

    public static boolean historicalLight(String jobStatus, ParsedResult result) {
        return "COMPLETED".equals(jobStatus)
                && result != null
                && "HISTORICAL_LIGHT".equals(result.reviewMode());
    }

    private static void requireModeConsistency(
            String reviewMode,
            String traceability,
            String alignment,
            boolean eligible,
            String verdict
    ) {
        boolean valid = switch (reviewMode) {
            case "HISTORICAL_LIGHT" -> "NOT_REQUIRED".equals(traceability)
                    && "NOT_REQUIRED".equals(alignment)
                    && !eligible
                    && "ADVISORY_ONLY".equals(verdict);
            case "TASK_LINKED" -> "VERIFIED".equals(traceability)
                    && eligible
                    && !"ADVISORY_ONLY".equals(verdict)
                    && !"NOT_EVALUATED".equals(alignment)
                    && !"NOT_REQUIRED".equals(alignment);
            case "UNLINKED_ADVISORY" -> "NOT_PROVEN".equals(traceability)
                    && "NOT_EVALUATED".equals(alignment)
                    && !eligible
                    && "ADVISORY_ONLY".equals(verdict);
            default -> false;
        };
        if (!valid) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_REVIEWMODE_MISMATCH");
        }
    }

    private static void rejectUnknownTopLevel(Map<String, Object> result) {
        Set<String> allowed = Set.of(
                "schemaVersion", "jobId", "projectRef", "commitSha", "reviewPolicyVersion",
                "reviewMode", "traceability", "commitMessageAssessment", "inferredFunction",
                "codeAssessment", "taskAlignment", "verdict", "overallStatus",
                "requirementCoverage", "verificationFacts", "findings", "summary",
                "createdAt", "evidenceRefs"
        );
        for (String key : result.keySet()) {
            if (!allowed.contains(key)) {
                throw new CommitReviewResultRejected("AI_REVIEW_RESULT_UNKNOWN_FIELD");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requiredObject(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_MISSING");
        }
        return (Map<String, Object>) map;
    }

    private static boolean requiredBoolean(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
    }

    private static String optionalString(Map<String, Object> source, String field) {
        Object value = source.get(field);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String raw) || raw.isBlank()) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_" + field.toUpperCase(Locale.ROOT) + "_INVALID");
        }
        return raw.trim();
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

    private static List<Map<String, Object>> safeFindings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_FINDINGS_INVALID");
        }
        List<Map<String, Object>> safe = new ArrayList<>();
        int limit = Math.min(list.size(), MAX_SAFE_FINDINGS);
        for (int index = 0; index < limit; index++) {
            Object item = list.get(index);
            if (!(item instanceof Map<?, ?> map)) {
                throw new CommitReviewResultRejected("AI_REVIEW_RESULT_FINDINGS_INVALID");
            }
            Object severity = map.get("severity");
            if (severity instanceof String raw && !SEVERITIES.contains(raw.trim())) {
                throw new CommitReviewResultRejected("AI_REVIEW_RESULT_FINDINGS_UNKNOWN");
            }
            safe.add(Map.of(
                    "severity", bounded(stringValue(map.get("severity"))),
                    "category", bounded(stringValue(map.get("category"))),
                    "message", bounded(stringValue(map.get("message")))
            ));
        }
        return List.copyOf(safe);
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new CommitReviewResultRejected("AI_REVIEW_RESULT_EVIDENCE_INVALID");
        }
        List<String> refs = new ArrayList<>();
        int limit = Math.min(list.size(), MAX_SAFE_FINDINGS);
        for (int index = 0; index < limit; index++) {
            refs.add(bounded(stringValue(list.get(index))));
        }
        return List.copyOf(refs);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String bounded(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= MAX_SAFE_TEXT ? normalized : normalized.substring(0, MAX_SAFE_TEXT);
    }

    public record ParsedResult(
            String schemaVersion,
            String reviewMode,
            String traceabilityStatus,
            String messageQuality,
            String codeQuality,
            String inferredFunctionLabel,
            String inferredFunctionConfidence,
            String taskAlignment,
            boolean verdictEligible,
            String verdict,
            String overallStatus,
            String policyVersion,
            List<Map<String, Object>> findings,
            List<String> evidenceRefs
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
