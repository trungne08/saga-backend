package com.saga.be.dto.response;

import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.RoleInTeam;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InternalAgentToolResponses {

    private InternalAgentToolResponses() {
    }

    public record ProjectTasks(
            UUID projectId,
            int page,
            int size,
            long totalElements,
            boolean truncated,
            List<TaskReadResponse> tasks
    ) {
    }

    public record StudentProgress(
            UUID studentId,
            UUID projectId,
            long totalAssignedTasks,
            boolean truncated,
            Map<String, Long> statusCounts,
            List<TaskReadResponse> tasks,
            List<String> dataLimitations
    ) {
    }

    public record TeamProgress(
            UUID teamId,
            UUID projectId,
            long totalTasks,
            boolean truncated,
            Map<String, Long> statusCounts,
            TeamContributionEvaluationResponse contribution,
            List<String> dataLimitations
    ) {
    }

    public record SrsContext(
            ProjectDetailResponse project,
            TeamContext team,
            List<TaskReadResponse> tasks,
            List<RepositoryEvidence> repositories,
            ProjectTraceabilityResponse traceability,
            List<DocumentEvidence> documents,
            ContextBounds bounds
    ) {
    }

    public record TeamContext(
            UUID teamId,
            String name,
            UUID courseId,
            String courseName,
            List<MemberEvidence> members
    ) {
    }

    public record MemberEvidence(
            UUID studentId,
            String displayName,
            String studentCode,
            RoleInTeam roleInTeam
    ) {
    }

    public record RepositoryEvidence(Long repositoryId, String name) {
    }

    public record CommitReviewTarget(
            UUID projectId,
            long repositoryId,
            String commitSha
    ) {
    }

    public record DocumentEvidence(UUID id, String title, DocumentType type) {
    }

    public record ContextBounds(
            boolean truncated,
            List<String> truncationReasons,
            long totalTasks,
            int includedTasks,
            int maximumTasks,
            int maximumTraceabilityEvents
    ) {
    }

    public record ActionValidation(
            boolean valid,
            Map<String, Object> normalizedPayload,
            String summary
    ) {
    }
}
