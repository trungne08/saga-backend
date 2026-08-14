package com.saga.be.dto.response;

import com.saga.be.entity.enums.DocumentType;
import com.saga.be.entity.enums.RoleInTeam;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InternalAgentToolResponses {

    private InternalAgentToolResponses() {
    }

    public record ResourceContext(
            String actorRole,
            String selectionState,
            long totalCourses,
            long totalTeams,
            long totalProjects,
            List<CourseContext> courses,
            List<String> dataLimitations
    ) {
    }

    public record CourseContext(
            UUID courseId,
            String courseCode,
            String courseName,
            List<ResourceTeamContext> teams
    ) {
    }

    public record ResourceTeamContext(
            UUID teamId,
            String teamName,
            RoleInTeam currentStudentRole,
            ResourceProjectContext project
    ) {
    }

    public record ResourceProjectContext(UUID projectId, String projectName) {
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
            ContributionSnapshot contribution,
            List<String> dataLimitations
    ) {
    }

    public record ContributionSnapshot(
            UUID teamId,
            UUID projectId,
            LocalDateTime evaluatedAt,
            List<ContributionMemberSnapshot> members
    ) {
    }

    public record ContributionMemberSnapshot(
            UUID studentId,
            String fullName,
            String studentCode,
            double codeContributionPercentage,
            double documentContributionPercentage,
            double designContributionPercentage,
            double taskContributionPercentage,
            double finalContributionPercentage,
            int evidenceCount
    ) {
    }

    public record StudentContribution(
            UUID studentId,
            UUID teamId,
            UUID projectId,
            LocalDateTime evaluatedAt,
            ContributionMemberSnapshot currentAggregate
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
