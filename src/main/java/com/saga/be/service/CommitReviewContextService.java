package com.saga.be.service;

import com.saga.be.dto.response.InternalCommitReviewContextResponse;
import com.saga.be.dto.response.InternalCommitReviewContextResponse.ChangedFileEvidence;
import com.saga.be.dto.response.InternalCommitReviewContextResponse.ContextBounds;
import com.saga.be.dto.response.InternalCommitReviewContextResponse.LinkedIssueEvidence;
import com.saga.be.dto.response.InternalCommitReviewContextResponse.LinkedTaskEvidence;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.provider.GitHubChangedFileSnapshot;
import com.saga.be.integration.provider.GitHubCommitDetailSnapshot;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.service.CommitReviewContextReader.IssueLinkSnapshot;
import com.saga.be.service.CommitReviewContextReader.SourceSnapshot;
import com.saga.be.service.CommitReviewContextReader.TaskLinkSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommitReviewContextService {

    public static final String CONTEXT_SCHEMA_VERSION = "saga-commit-review-context-v1";
    public static final String CONTEXT_PROVIDER = "SAGA_BACKEND";
    private static final String TRACEABILITY_AUTHORITY = "EXPLICIT_LOCAL_RELATION_ONLY";
    private static final Logger log = LoggerFactory.getLogger(CommitReviewContextService.class);

    private final CommitReviewContextReader reader;
    private final GitHubProviderClient github;
    private final CommitReviewContextSanitizer sanitizer;
    private final CommitReviewContextLimits limits;

    @Autowired
    public CommitReviewContextService(
            CommitReviewContextReader reader,
            GitHubProviderClient github,
            CommitReviewContextSanitizer sanitizer
    ) {
        this(reader, github, sanitizer, CommitReviewContextLimits.defaults());
    }

    CommitReviewContextService(
            CommitReviewContextReader reader,
            GitHubProviderClient github,
            CommitReviewContextSanitizer sanitizer,
            CommitReviewContextLimits limits
    ) {
        this.reader = reader;
        this.github = github;
        this.sanitizer = sanitizer;
        this.limits = limits;
    }

    public InternalCommitReviewContextResponse context(
            UUID projectId,
            long providerRepositoryId,
            String requestedCommitSha
    ) {
        long startedAt = System.nanoTime();
        String commitSha = requireCommitSha(requestedCommitSha);
        SourceSnapshot source = reader.load(projectId, providerRepositoryId, commitSha);
        GitHubCommitDetailSnapshot detail = github.commitDetail(
                source.repository().installationId(),
                source.repository().ownerLogin(),
                source.repository().name(),
                commitSha
        );
        requireMatchingCommit(source, detail, commitSha);
        InternalCommitReviewContextResponse response = assemble(source, detail);
        log.info(
                "internal_commit_review_context_completed project_id={} repository_id={} "
                        + "commit_sha={} context_status={} duration_ms={}",
                projectId,
                providerRepositoryId,
                commitSha,
                response.contextBounds().truncated() ? "TRUNCATED" : "COMPLETE",
                (System.nanoTime() - startedAt) / 1_000_000
        );
        return response;
    }

    private InternalCommitReviewContextResponse assemble(
            SourceSnapshot source,
            GitHubCommitDetailSnapshot detail
    ) {
        Set<String> reasons = new LinkedHashSet<>();
        TextBudget budget = new TextBudget(limits.maxTotalContextChars(), sanitizer, reasons);
        String fullName = budget.take(source.repository().fullName(), false);
        String commitMessage = budget.take(detail.message(), true);

        List<ChangedFileEvidence> changedFiles = changedFiles(detail, budget, reasons);
        List<LinkedIssueEvidence> linkedIssues = linkedIssues(source, budget, reasons);
        if (source.traceabilityTruncated()) {
            reasons.add("TRACEABILITY_LIMIT_EXCEEDED");
        }

        int totalChangedFiles = detail.changedFiles().size();
        if (source.commit().filesChanged() != null) {
            totalChangedFiles = Math.max(totalChangedFiles, source.commit().filesChanged());
        }
        if (totalChangedFiles > detail.changedFiles().size()) {
            reasons.add("UPSTREAM_CHANGED_FILES_TRUNCATED");
        }
        if (detail.changedFiles().size() > limits.maxChangedFiles()) {
            reasons.add("MAX_CHANGED_FILES_EXCEEDED");
        }
        List<String> evidenceRefs = evidenceRefs(source, linkedIssues, budget, reasons);
        String relationStatus = linkedIssues.isEmpty()
                ? "NOT_PROVEN"
                : "EXPLICIT_LINKS_PRESENT";

        return new InternalCommitReviewContextResponse(
                CONTEXT_SCHEMA_VERSION,
                CONTEXT_PROVIDER,
                new InternalCommitReviewContextResponse.ProjectIdentity(source.projectId()),
                new InternalCommitReviewContextResponse.RepositoryIdentity(
                        source.repository().localRepositoryId(),
                        source.repository().providerRepositoryId(),
                        source.repository().provider(),
                        fullName
                ),
                new InternalCommitReviewContextResponse.CommitEvidence(
                        detail.sha().toLowerCase(Locale.ROOT),
                        commitMessage,
                        detail.committedAt(),
                        detail.additions(),
                        detail.deletions(),
                        totalChangedFiles,
                        changedFiles
                ),
                new InternalCommitReviewContextResponse.TraceabilityEvidence(
                        TRACEABILITY_AUTHORITY,
                        relationStatus,
                        linkedIssues,
                        evidenceRefs
                ),
                new ContextBounds(
                        !reasons.isEmpty(),
                        List.copyOf(reasons),
                        totalChangedFiles,
                        changedFiles.size(),
                        limits.maxChangedFiles(),
                        limits.maxPatchCharsPerFile(),
                        limits.maxTotalContextChars()
                )
        );
    }

    private List<ChangedFileEvidence> changedFiles(
            GitHubCommitDetailSnapshot detail,
            TextBudget budget,
            Set<String> reasons
    ) {
        List<ChangedFileEvidence> result = new ArrayList<>();
        for (GitHubChangedFileSnapshot file : detail.changedFiles()) {
            if (result.size() >= limits.maxChangedFiles()) {
                reasons.add("MAX_CHANGED_FILES_EXCEEDED");
                break;
            }
            if (!budget.canIncludeRequired(file.path(), file.changeType())) {
                reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
                break;
            }
            String path = budget.take(file.path(), true);
            String changeType = budget.take(file.changeType(), true);
            String patch = file.patch();
            boolean patchTruncated = false;
            if (patch != null && patch.length() > limits.maxPatchCharsPerFile()) {
                patch = patch.substring(0, limits.maxPatchCharsPerFile());
                patchTruncated = true;
                reasons.add("MAX_PATCH_CHARS_PER_FILE_EXCEEDED");
            }
            TextBudget.Value boundedPatch = budget.takeOptional(patch);
            patchTruncated = patchTruncated || boundedPatch.truncated();
            if (boundedPatch.truncated()) {
                reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
            }
            result.add(new ChangedFileEvidence(
                    path,
                    changeType,
                    file.additions(),
                    file.deletions(),
                    boundedPatch.text(),
                    patchTruncated
            ));
        }
        return List.copyOf(result);
    }

    private List<LinkedIssueEvidence> linkedIssues(
            SourceSnapshot source,
            TextBudget budget,
            Set<String> reasons
    ) {
        List<LinkedIssueEvidence> result = new ArrayList<>();
        for (IssueLinkSnapshot issue : source.linkedIssues()) {
            TextBudget.Value title = budget.takeOptional(issue.title());
            if (title.truncated()) {
                reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
            }
            List<LinkedTaskEvidence> tasks = new ArrayList<>();
            for (TaskLinkSnapshot task : issue.linkedTasks()) {
                TextBudget.Value externalKey = budget.takeOptional(task.externalKey());
                TextBudget.Value taskTitle = budget.takeOptional(task.title());
                TextBudget.Value description = budget.takeOptional(task.description());
                if (externalKey.truncated()
                        || taskTitle.truncated()
                        || description.truncated()) {
                    reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
                }
                tasks.add(new LinkedTaskEvidence(
                        task.taskId(),
                        externalKey.text(),
                        taskTitle.text(),
                        description.text(),
                        task.type(),
                        task.status(),
                        task.priority(),
                        task.storyPoint()
                ));
            }
            result.add(new LinkedIssueEvidence(
                    issue.issueId(),
                    issue.issueNumber(),
                    title.text(),
                    issue.state(),
                    issue.relationType(),
                    tasks
            ));
        }
        return List.copyOf(result);
    }

    private List<String> evidenceRefs(
            SourceSnapshot source,
            List<LinkedIssueEvidence> includedIssues,
            TextBudget budget,
            Set<String> reasons
    ) {
        Set<UUID> includedIssueIds = includedIssues.stream()
                .map(LinkedIssueEvidence::issueId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> refs = new ArrayList<>();
        for (IssueLinkSnapshot issue : source.linkedIssues()) {
            if (!includedIssueIds.contains(issue.issueId())) {
                continue;
            }
            TextBudget.Value issueRef = budget.takeOptional(
                    "git-issue-commit-link:" + issue.issueCommitLinkId()
            );
            if (issueRef.truncated()) {
                reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
                break;
            }
            refs.add(issueRef.text());
            for (TaskLinkSnapshot task : issue.linkedTasks()) {
                TextBudget.Value taskRef = budget.takeOptional(
                        "task-git-issue-link:" + task.taskIssueLinkId()
                );
                if (taskRef.truncated()) {
                    reasons.add("MAX_TOTAL_CONTEXT_EXCEEDED");
                    return List.copyOf(refs);
                }
                refs.add(taskRef.text());
            }
        }
        return List.copyOf(refs);
    }

    private void requireMatchingCommit(
            SourceSnapshot source,
            GitHubCommitDetailSnapshot detail,
            String requestedCommitSha
    ) {
        if (detail == null
                || detail.sha() == null
                || !requestedCommitSha.equalsIgnoreCase(detail.sha())
                || !requestedCommitSha.equalsIgnoreCase(source.commit().sha())
                || detail.message() == null
                || detail.message().isBlank()) {
            throw IntegrationException.unavailable("GITHUB_RESPONSE_INVALID");
        }
    }

    private String requireCommitSha(String value) {
        if (value == null || !value.matches("(?i)[0-9a-f]{40}")) {
            throw IntegrationException.invalid(
                    "GITHUB_COMMIT_SHA_INVALID",
                    "The GitHub commit SHA is invalid"
            );
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static final class TextBudget {
        private int remaining;
        private final CommitReviewContextSanitizer sanitizer;
        private final Set<String> reasons;

        private TextBudget(
                int maximum,
                CommitReviewContextSanitizer sanitizer,
                Set<String> reasons
        ) {
            this.remaining = maximum;
            this.sanitizer = sanitizer;
            this.reasons = reasons;
        }

        private boolean canIncludeRequired(String... values) {
            int required = java.util.Arrays.stream(values)
                    .mapToInt(value -> value == null ? 0 : value.length())
                    .sum();
            return required > 0 && required <= remaining;
        }

        private String take(String source, boolean required) {
            Value value = takeOptional(source);
            if (required && (value.text() == null || value.text().isBlank())) {
                throw IntegrationException.unavailable("GITHUB_RESPONSE_INVALID");
            }
            return value.text();
        }

        private Value takeOptional(String source) {
            if (source == null) {
                return new Value(null, false);
            }
            CommitReviewContextSanitizer.SanitizedText sanitized = sanitizer.sanitize(source);
            if (sanitized.redacted()) {
                reasons.add("SECRET_REDACTION_APPLIED");
            }
            if (remaining <= 0) {
                return new Value(null, !sanitized.value().isEmpty());
            }
            String bounded = sanitized.value().substring(
                    0,
                    Math.min(sanitized.value().length(), remaining)
            );
            remaining -= bounded.length();
            return new Value(bounded, bounded.length() < sanitized.value().length());
        }

        private record Value(String text, boolean truncated) {
        }
    }
}
