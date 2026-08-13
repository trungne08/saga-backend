package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record InternalCommitReviewContextResponse(
        String contextSchemaVersion,
        String contextProvider,
        ProjectIdentity project,
        RepositoryIdentity repository,
        CommitEvidence commit,
        TraceabilityEvidence traceability,
        ContextBounds contextBounds
) {

    public record ProjectIdentity(UUID projectId) {
    }

    public record RepositoryIdentity(
            UUID localRepositoryId,
            Long repositoryId,
            String provider,
            String fullName
    ) {
    }

    public record CommitEvidence(
            String sha,
            String message,
            LocalDateTime committedAt,
            Integer additions,
            Integer deletions,
            int totalChangedFiles,
            List<ChangedFileEvidence> changedFiles
    ) {
        public CommitEvidence {
            changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        }
    }

    public record ChangedFileEvidence(
            String path,
            String changeType,
            Integer additions,
            Integer deletions,
            String patch,
            boolean patchTruncated
    ) {
    }

    public record TraceabilityEvidence(
            String authority,
            String relationStatus,
            List<LinkedIssueEvidence> linkedIssues,
            List<String> evidenceRefs
    ) {
        public TraceabilityEvidence {
            linkedIssues = linkedIssues == null ? List.of() : List.copyOf(linkedIssues);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public record LinkedIssueEvidence(
            UUID issueId,
            Integer issueNumber,
            String title,
            String state,
            String commitRelationType,
            List<LinkedTaskEvidence> linkedTasks
    ) {
        public LinkedIssueEvidence {
            linkedTasks = linkedTasks == null ? List.of() : List.copyOf(linkedTasks);
        }
    }

    public record LinkedTaskEvidence(
            UUID taskId,
            String externalKey,
            String title,
            String description,
            String type,
            String status,
            String priority,
            Integer storyPoint
    ) {
    }

    public record ContextBounds(
            boolean truncated,
            List<String> truncationReasons,
            int totalChangedFiles,
            int includedChangedFiles,
            int maxChangedFiles,
            int maxPatchCharsPerFile,
            int maxTotalContextChars
    ) {
        public ContextBounds {
            truncationReasons = truncationReasons == null
                    ? List.of()
                    : List.copyOf(truncationReasons);
        }
    }
}
