package com.saga.be.controller;

import com.saga.be.dto.request.InternalAgentToolRequests;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.service.AgentConversationScopeService;
import com.saga.be.service.AgentDelegatedAccess;
import com.saga.be.service.AgentDelegationCapability;
import com.saga.be.service.AgentDelegationService;
import com.saga.be.service.AgentRoleAwareProjectionService;
import com.saga.be.service.AgentTaskProposalValidationService;
import com.saga.be.service.AgentToolProjectionService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Hidden
@RequestMapping("/internal/ai/v1/agent/tools")
public class InternalAgentToolController {

    public static final String DELEGATED_CONTEXT_HEADER = "X-SAGA-Agent-Context";

    private final AgentDelegationService delegations;
    private final AgentToolProjectionService projections;
    private final AgentRoleAwareProjectionService roleAware;
    private final AgentTaskProposalValidationService proposals;
    private final AgentConversationScopeService conversationScopes;

    public InternalAgentToolController(
            AgentDelegationService delegations,
            AgentToolProjectionService projections,
            AgentRoleAwareProjectionService roleAware,
            AgentTaskProposalValidationService proposals,
            AgentConversationScopeService conversationScopes
    ) {
        this.delegations = delegations;
        this.projections = projections;
        this.roleAware = roleAware;
        this.proposals = proposals;
        this.conversationScopes = conversationScopes;
    }

    @PostMapping("/resource-context")
    public InternalAgentToolResponses.ResourceContext resourceContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        return projections.resourceContext(access.actor(), access.courseId());
    }

    @PostMapping("/project-summary")
    public ProjectDetailResponse projectSummary(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.projectSummary(access.actor(), request.projectId());
    }

    @PostMapping("/project-tasks")
    public InternalAgentToolResponses.ProjectTasks projectTasks(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.ProjectTasks request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.projectTasks(
                access.actor(), request.projectId(), request.page(), request.size()
        );
    }

    @PostMapping("/task-detail")
    public TaskReadResponse taskDetail(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Task request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.taskDetail(access.actor(), request.projectId(), request.taskId());
    }

    @PostMapping("/student-progress")
    public InternalAgentToolResponses.StudentProgress studentProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.studentProgress(access.actor(), request.projectId());
    }

    @PostMapping("/team-progress")
    public InternalAgentToolResponses.TeamProgress teamProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return projections.teamProgress(access.actor(), request.teamId());
    }

    @PostMapping("/team-contribution")
    public InternalAgentToolResponses.ContributionSnapshot teamContribution(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return projections.teamContribution(access.actor(), request.teamId());
    }

    @PostMapping("/student-contribution")
    public InternalAgentToolResponses.StudentContribution studentContribution(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return projections.studentContribution(access.actor(), request.teamId());
    }

    @PostMapping("/team-sprints")
    public SprintListResponse teamSprints(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return projections.teamSprints(access.actor(), request.teamId());
    }

    @PostMapping("/course-warnings")
    public LecturerAnalyticsResponses.EarlyWarnings courseWarnings(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Course request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        return projections.courseWarnings(
                access.actor(),
                conversationScopes.effectiveCourseId(access.courseId(), request.courseId())
        );
    }

    @PostMapping("/project-traceability")
    public ProjectTraceabilityResponse projectTraceability(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.projectTraceability(access.actor(), request.projectId());
    }

    @PostMapping("/validate-commit-review")
    public InternalAgentToolResponses.CommitReviewTarget validateCommitReview(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.CommitReview request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.commitReviewTarget(
                access.actor(),
                request.projectId(),
                request.repositoryId(),
                request.commitSha()
        );
    }

    @PostMapping("/srs-context")
    public InternalAgentToolResponses.SrsContext srsContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.srsContext(access.actor(), request.projectId());
    }

    @PostMapping("/self-progress")
    public InternalAgentToolResponses.SelfProgress selfProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalProject request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return roleAware.selfProgress(
                access.actor(),
                conversationScopes.effectiveCourseId(access.courseId(), request.courseId()),
                request.projectId()
        );
    }

    @PostMapping("/self-recent-commits")
    public InternalAgentToolResponses.RecentCommits selfRecentCommits(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        return roleAware.recentCommits(access(context, request.conversationId(), false).actor());
    }

    @PostMapping("/leader-team-context")
    public InternalAgentToolResponses.LeaderTeamContext leaderTeamContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalTeam request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return roleAware.leaderTeamContext(access.actor(), request.teamId());
    }

    @PostMapping("/leader-team-progress-report")
    public InternalAgentToolResponses.LeaderTeamProgressReport leaderTeamProgressReport(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalTeam request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireTeamInScope(access.courseId(), request.teamId());
        return roleAware.leaderTeamProgressReport(access.actor(), request.teamId());
    }

    @PostMapping("/lecturer-course-context")
    public InternalAgentToolResponses.LecturerCourseContext lecturerCourseContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalCourse request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        return roleAware.lecturerCourseContext(
                access.actor(),
                conversationScopes.effectiveCourseId(access.courseId(), request.courseId())
        );
    }

    @PostMapping("/lecturer-progress-report")
    public InternalAgentToolResponses.LecturerProgressReport lecturerProgressReport(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalCourse request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        return roleAware.lecturerProgressReport(
                access.actor(),
                conversationScopes.effectiveCourseId(access.courseId(), request.courseId())
        );
    }

    @PostMapping("/admin-system-report")
    public InternalAgentToolResponses.AdminSystemReport adminSystemReport(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        return roleAware.adminSystemReport(access(context, request.conversationId(), false).actor());
    }

    @PostMapping("/resolve-assignee")
    public InternalAgentToolResponses.AssigneeResolution resolveAssignee(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.AssigneeResolve request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), false);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return projections.resolveAssignee(
                access.actor(),
                request.projectId(), request.fullName(), request.studentCode()
        );
    }

    @PostMapping("/validate-task-create")
    public InternalAgentToolResponses.ActionValidation validateTaskCreate(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.TaskCreate request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), true);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return proposals.validateCreate(access.actor(), request);
    }

    @PostMapping("/validate-task-update")
    public InternalAgentToolResponses.ActionValidation validateTaskUpdate(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.TaskUpdate request
    ) {
        AgentDelegatedAccess access = access(context, request.conversationId(), true);
        conversationScopes.requireProjectInScope(access.courseId(), request.projectId());
        return proposals.validateUpdate(access.actor(), request);
    }

    private AgentDelegatedAccess access(String token, java.util.UUID conversationId, boolean writeProposal) {
        return delegations.resolveAccess(
                token,
                conversationId,
                writeProposal ? AgentDelegationCapability.PROPOSE_WRITE : AgentDelegationCapability.READ
        );
    }
}
