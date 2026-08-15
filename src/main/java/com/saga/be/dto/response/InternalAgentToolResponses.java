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
            List<String> dataLimitations,
            CurrentActor currentActor
    ) {
    }

    public record CurrentActor(
            String applicationRole,
            UUID localProfileId,
            String displayName,
            String studentCode,
            String identitySource,
            String teamRoleSelectionState,
            long ledTeamCount
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
            double researchContributionPercentage,
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

    public record AssigneeResolution(
            UUID projectId,
            UUID teamId,
            String matchState,
            List<AssigneeCandidate> candidates
    ) {
    }

    public record AssigneeCandidate(
            UUID studentId,
            String fullName,
            String studentCode
    ) {
    }

    public record ActionValidation(
            boolean valid,
            Map<String, Object> normalizedPayload,
            String summary
    ) {
    }

    public record SelfProgress(
            String selectionState,
            UUID courseId,
            UUID teamId,
            UUID projectId,
            LecturerAnalyticsResponses.StudentProgress progress,
            StudentProgress assignedTasks,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> reviewAdvisories,
            List<String> unsupportedSignals,
            List<ResourceTeamContext> candidates,
            List<String> dataLimitations
    ) {
    }

    public record RecentCommits(
            String mappingState,
            String selectionState,
            List<RecentCommit> commits,
            List<String> dataLimitations
    ) {
    }

    public record RecentCommit(
            UUID projectId,
            Long providerRepositoryId,
            String commitSha,
            String shortSha,
            String message,
            java.time.LocalDateTime timestamp
    ) {
    }

    public record LeaderTeamContext(
            String selectionState,
            UUID teamId,
            TeamProgress progress,
            List<OverdueTaskEvidence> overdueTasks,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> unsupportedSignals,
            List<String> reviewAdvisories,
            List<ResourceTeamContext> candidates,
            List<String> dataLimitations
    ) {
    }

    public record LeaderTeamProgressReport(
            String projectionVersion,
            String artifactType,
            String selectionState,
            java.time.OffsetDateTime generatedAt,
            UUID teamId,
            String teamName,
            UUID projectId,
            TeamProgress progress,
            List<OverdueTaskEvidence> overdueTasks,
            TraceabilitySummary traceability,
            CommitReviewOperationalAggregate commitReviewOperational,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> reviewAdvisories,
            List<String> unsupportedSignals,
            List<ResourceTeamContext> candidates,
            List<String> unsupportedFields,
            List<String> dataLimitations
    ) {
    }

    public record OverdueTaskEvidence(
            UUID taskId,
            String title,
            UUID assigneeId,
            String assigneeDisplayName,
            String evidenceType,
            String evidenceNote,
            java.time.LocalDateTime dueDate
    ) {
    }

    public record ConfirmedWarning(
            String signal,
            UUID teamId,
            UUID taskId,
            UUID assigneeId,
            String assigneeDisplayName,
            String evidenceNote,
            java.time.LocalDateTime dueDate
    ) {
    }

    public record TraceabilitySummary(
            UUID projectId,
            int eventCount,
            boolean truncated,
            List<String> dataLimitations
    ) {
    }

    public record CommitReviewOperationalAggregate(
            boolean resultWarningIntegrationConfirmed,
            long pending,
            long starting,
            long started,
            long running,
            long waitingRetry,
            long completed,
            long failed,
            long cancelled
    ) {
    }

    public record TeamReportSection(
            UUID teamId,
            String teamName,
            UUID projectId,
            LecturerCourseDashboardResponses.TeamProgress progress,
            ContributionSnapshot contribution,
            LecturerAnalyticsResponses.SprintVelocity velocity,
            LecturerAnalyticsResponses.ActivityOverview activities,
            LecturerAnalyticsResponses.BurndownChart burndown,
            TraceabilitySummary traceability,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> reviewAdvisories,
            List<String> unsupportedSignals,
            List<String> dataLimitations
    ) {
    }

    public record LecturerCourseContext(
            String selectionState,
            UUID courseId,
            LecturerCourseDashboardResponses.TeamsProgress teamsProgress,
            LecturerCourseDashboardResponses.ContributionSummary contributionSummary,
            LecturerCourseDashboardResponses.Trends trends,
            LecturerCourseDashboardResponses.AtRiskSummary atRiskSummary,
            LecturerAnalyticsResponses.EarlyWarnings earlyWarnings,
            List<CourseContext> candidates,
            List<String> dataLimitations
    ) {
    }

    public record LecturerProgressReport(
            String projectionVersion,
            String artifactType,
            String selectionState,
            java.time.OffsetDateTime generatedAt,
            UUID courseId,
            CourseReportMetadata course,
            List<TeamReportSection> teamSections,
            LecturerCourseDashboardResponses.TeamsProgress teamsProgress,
            LecturerCourseDashboardResponses.ContributionSummary contributionSummary,
            LecturerCourseDashboardResponses.Trends trends,
            LecturerCourseDashboardResponses.AtRiskSummary atRiskSummary,
            LecturerAnalyticsResponses.EarlyWarnings earlyWarnings,
            List<LecturerAnalyticsResponses.SprintVelocity> velocities,
            CommitReviewOperationalAggregate commitReviewOperational,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> unsupportedSignals,
            List<String> reviewAdvisories,
            List<CourseContext> candidates,
            List<String> unsupportedFields,
            List<String> dataLimitations
    ) {
    }

    public record CourseReportMetadata(
            UUID courseId,
            String courseCode,
            String courseName,
            UUID instructorId,
            String instructorDisplayName
    ) {
    }

    public record AdminSystemReport(
            String projectionVersion,
            String artifactType,
            java.time.OffsetDateTime generatedAt,
            AdminSystemStatsResponse systemStats,
            AdminIntegrationHealthResponse integrationHealth,
            org.springframework.data.domain.Page<AdminCourseProgressOverviewResponse> courseProgressOverview,
            AdminAnomaliesReportResponse anomalies,
            AdminGraphProcessingReportResponse graphProcessing,
            CommitReviewOperationalAggregate commitReviewOperational,
            List<ConfirmedWarning> confirmedWarnings,
            List<String> unsupportedSignals,
            List<String> reviewAdvisories,
            List<String> unsupportedFields,
            List<String> dataLimitations
    ) {
    }
}
