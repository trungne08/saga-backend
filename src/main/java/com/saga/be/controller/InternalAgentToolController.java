package com.saga.be.controller;

import com.saga.be.dto.request.InternalAgentToolRequests;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.security.SagaPrincipal;
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

    public InternalAgentToolController(
            AgentDelegationService delegations,
            AgentToolProjectionService projections,
            AgentRoleAwareProjectionService roleAware,
            AgentTaskProposalValidationService proposals
    ) {
        this.delegations = delegations;
        this.projections = projections;
        this.roleAware = roleAware;
        this.proposals = proposals;
    }

    @PostMapping("/resource-context")
    public InternalAgentToolResponses.ResourceContext resourceContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        return projections.resourceContext(actor(context, request.conversationId(), false));
    }

    @PostMapping("/project-summary")
    public ProjectDetailResponse projectSummary(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        return projections.projectSummary(actor(context, request.conversationId(), false), request.projectId());
    }

    @PostMapping("/project-tasks")
    public InternalAgentToolResponses.ProjectTasks projectTasks(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.ProjectTasks request
    ) {
        return projections.projectTasks(
                actor(context, request.conversationId(), false), request.projectId(), request.page(), request.size()
        );
    }

    @PostMapping("/task-detail")
    public TaskReadResponse taskDetail(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Task request
    ) {
        return projections.taskDetail(
                actor(context, request.conversationId(), false), request.projectId(), request.taskId()
        );
    }

    @PostMapping("/student-progress")
    public InternalAgentToolResponses.StudentProgress studentProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        return projections.studentProgress(actor(context, request.conversationId(), false), request.projectId());
    }

    @PostMapping("/team-progress")
    public InternalAgentToolResponses.TeamProgress teamProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        return projections.teamProgress(actor(context, request.conversationId(), false), request.teamId());
    }

    @PostMapping("/team-contribution")
    public InternalAgentToolResponses.ContributionSnapshot teamContribution(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        return projections.teamContribution(actor(context, request.conversationId(), false), request.teamId());
    }

    @PostMapping("/student-contribution")
    public InternalAgentToolResponses.StudentContribution studentContribution(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        return projections.studentContribution(actor(context, request.conversationId(), false), request.teamId());
    }

    @PostMapping("/team-sprints")
    public SprintListResponse teamSprints(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Team request
    ) {
        return projections.teamSprints(actor(context, request.conversationId(), false), request.teamId());
    }

    @PostMapping("/course-warnings")
    public LecturerAnalyticsResponses.EarlyWarnings courseWarnings(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Course request
    ) {
        return projections.courseWarnings(actor(context, request.conversationId(), false), request.courseId());
    }

    @PostMapping("/project-traceability")
    public ProjectTraceabilityResponse projectTraceability(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Project request
    ) {
        return projections.projectTraceability(actor(context, request.conversationId(), false), request.projectId());
    }

    @PostMapping("/validate-commit-review")
    public InternalAgentToolResponses.CommitReviewTarget validateCommitReview(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.CommitReview request
    ) {
        return projections.commitReviewTarget(
                actor(context, request.conversationId(), false),
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
        return projections.srsContext(actor(context, request.conversationId(), false), request.projectId());
    }

    @PostMapping("/self-progress")
    public InternalAgentToolResponses.SelfProgress selfProgress(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalProject request
    ) {
        return roleAware.selfProgress(
                actor(context, request.conversationId(), false), request.courseId(), request.projectId()
        );
    }

    @PostMapping("/self-recent-commits")
    public InternalAgentToolResponses.RecentCommits selfRecentCommits(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        return roleAware.recentCommits(actor(context, request.conversationId(), false));
    }

    @PostMapping("/leader-team-context")
    public InternalAgentToolResponses.LeaderTeamContext leaderTeamContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalTeam request
    ) {
        return roleAware.leaderTeamContext(actor(context, request.conversationId(), false), request.teamId());
    }

    @PostMapping("/lecturer-course-context")
    public InternalAgentToolResponses.LecturerCourseContext lecturerCourseContext(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalCourse request
    ) {
        return roleAware.lecturerCourseContext(actor(context, request.conversationId(), false), request.courseId());
    }

    @PostMapping("/lecturer-progress-report")
    public InternalAgentToolResponses.LecturerProgressReport lecturerProgressReport(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.OptionalCourse request
    ) {
        return roleAware.lecturerProgressReport(actor(context, request.conversationId(), false), request.courseId());
    }

    @PostMapping("/admin-system-report")
    public InternalAgentToolResponses.AdminSystemReport adminSystemReport(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.Context request
    ) {
        return roleAware.adminSystemReport(actor(context, request.conversationId(), false));
    }

    @PostMapping("/resolve-assignee")
    public InternalAgentToolResponses.AssigneeResolution resolveAssignee(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.AssigneeResolve request
    ) {
        return projections.resolveAssignee(
                actor(context, request.conversationId(), false),
                request.projectId(), request.fullName(), request.studentCode()
        );
    }

    @PostMapping("/validate-task-create")
    public InternalAgentToolResponses.ActionValidation validateTaskCreate(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.TaskCreate request
    ) {
        return proposals.validateCreate(actor(context, request.conversationId(), true), request);
    }

    @PostMapping("/validate-task-update")
    public InternalAgentToolResponses.ActionValidation validateTaskUpdate(
            @RequestHeader(DELEGATED_CONTEXT_HEADER) String context,
            @Valid @RequestBody InternalAgentToolRequests.TaskUpdate request
    ) {
        return proposals.validateUpdate(actor(context, request.conversationId(), true), request);
    }

    private SagaPrincipal actor(String token, java.util.UUID conversationId, boolean writeProposal) {
        return delegations.resolve(
                token,
                conversationId,
                writeProposal ? AgentDelegationCapability.PROPOSE_WRITE : AgentDelegationCapability.READ
        );
    }
}
