package com.saga.be.service;

import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.InternalAgentToolResponses.ContextBounds;
import com.saga.be.dto.response.InternalAgentToolResponses.DocumentEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.MemberEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.RepositoryEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.TeamContext;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolProjectionService {

    private static final int MAX_TASKS = 100;
    private static final int MAX_TRACEABILITY_EVENTS = 50;

    private final ProjectDetailService projects;
    private final ProjectTaskReadService tasks;
    private final TeamContributionService contributions;
    private final GitHubTraceabilityService traceability;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final GitRepoRepository repositories;
    private final DocumentRepository documents;
    private final CommitReviewContextReader commitReviewContexts;

    public AgentToolProjectionService(
            ProjectDetailService projects,
            ProjectTaskReadService tasks,
            TeamContributionService contributions,
            GitHubTraceabilityService traceability,
            TeamRepository teams,
            TeamMemberRepository teamMembers,
            GitRepoRepository repositories,
            DocumentRepository documents,
            CommitReviewContextReader commitReviewContexts
    ) {
        this.projects = projects;
        this.tasks = tasks;
        this.contributions = contributions;
        this.traceability = traceability;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.repositories = repositories;
        this.documents = documents;
        this.commitReviewContexts = commitReviewContexts;
    }

    public ProjectDetailResponse projectSummary(SagaPrincipal actor, UUID projectId) {
        return projects.get(actor, projectId);
    }

    public InternalAgentToolResponses.ProjectTasks projectTasks(
            SagaPrincipal actor, UUID projectId, int page, int size
    ) {
        Page<TaskReadResponse> result = tasks.getTasks(
                actor, projectId, null, null, null, null,
                "externalKey", "asc", page, size
        );
        return new InternalAgentToolResponses.ProjectTasks(
                projectId, page, size, result.getTotalElements(), result.hasNext(), result.getContent()
        );
    }

    public TaskReadResponse taskDetail(SagaPrincipal actor, UUID projectId, UUID taskId) {
        return tasks.getTask(actor, projectId, taskId);
    }

    public InternalAgentToolResponses.StudentProgress studentProgress(
            SagaPrincipal actor, UUID projectId
    ) {
        if (actor.applicationRole() != ApplicationRole.STUDENT) {
            throw new AccessDeniedException("Student personal progress is available only to the current Student");
        }
        Page<TaskReadResponse> result = tasks.getTasks(
                actor, projectId, null, null, actor.localProfileId(), null,
                "externalKey", "asc", 0, 50
        );
        return new InternalAgentToolResponses.StudentProgress(
                actor.localProfileId(), projectId, result.getTotalElements(), result.hasNext(),
                statusCounts(result.getContent()), result.getContent(),
                result.hasNext() ? List.of("ASSIGNED_TASK_LIMIT_EXCEEDED") : List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.TeamProgress teamProgress(SagaPrincipal actor, UUID teamId) {
        TeamContributionEvaluationResponse contribution = contributions.evaluate(actor, teamId);
        Team team = teams.findById(teamId).orElseThrow(() -> new IntegrationException(
                HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "The team does not exist"
        ));
        if (team.getProject() == null) {
            return new InternalAgentToolResponses.TeamProgress(
                    teamId, null, 0, false, Map.of(), contribution, List.of("PROJECT_NOT_ASSIGNED")
            );
        }
        UUID projectId = team.getProject().getId();
        Page<TaskReadResponse> page = tasks.getTasks(
                actor, projectId, null, null, null, null,
                "externalKey", "asc", 0, 50
        );
        return new InternalAgentToolResponses.TeamProgress(
                teamId, projectId, page.getTotalElements(), page.hasNext(),
                statusCounts(page.getContent()), contribution,
                page.hasNext() ? List.of("TASK_LIMIT_EXCEEDED") : List.of()
        );
    }

    public TeamContributionEvaluationResponse teamContribution(SagaPrincipal actor, UUID teamId) {
        return contributions.evaluate(actor, teamId);
    }

    public ProjectTraceabilityResponse projectTraceability(SagaPrincipal actor, UUID projectId) {
        return traceability.projectTimeline(actor, projectId, MAX_TRACEABILITY_EVENTS);
    }

    public InternalAgentToolResponses.CommitReviewTarget commitReviewTarget(
            SagaPrincipal actor, UUID projectId, long repositoryId, String commitSha
    ) {
        projects.get(actor, projectId);
        CommitReviewContextReader.SourceSnapshot source = commitReviewContexts.load(
                projectId, repositoryId, commitSha.toLowerCase(java.util.Locale.ROOT)
        );
        return new InternalAgentToolResponses.CommitReviewTarget(
                source.projectId(),
                source.repository().providerRepositoryId(),
                source.commit().sha().toLowerCase(java.util.Locale.ROOT)
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.SrsContext srsContext(SagaPrincipal actor, UUID projectId) {
        ProjectDetailResponse project = projects.get(actor, projectId);
        Team team = teams.findWithCourseAndInstructorByProjectId(projectId)
                .orElseThrow(() -> IntegrationException.conflict(
                        "PROJECT_TEAM_MISSING", "The project is not assigned to a team"
                ));
        Page<TaskReadResponse> taskPage = tasks.getTasks(
                actor, projectId, null, null, null, null,
                "externalKey", "asc", 0, MAX_TASKS
        );
        List<MemberEvidence> members = teamMembers.findByTeamId(team.getId()).stream()
                .filter(value -> value.getStudent() != null)
                .map(value -> new MemberEvidence(
                        value.getStudent().getId(), value.getStudent().getFullName(),
                        value.getStudent().getStudentCode(), value.getRoleInTeam()
                ))
                .sorted(Comparator.comparing(
                        MemberEvidence::studentCode,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ).thenComparing(MemberEvidence::studentId))
                .toList();
        TeamContext teamContext = new TeamContext(
                team.getId(), team.getName(),
                team.getCourse() == null ? null : team.getCourse().getId(),
                team.getCourse() == null ? null : team.getCourse().getName(),
                members
        );
        List<RepositoryEvidence> repositoryEvidence = repositories
                .findByProjectIdAndRepositoryIdIsNotNullOrderByFullNameAscRepositoryIdAsc(projectId)
                .stream()
                .map(value -> new RepositoryEvidence(
                        value.getRepositoryId(),
                        value.getFullName() == null ? value.getName() : value.getFullName()
                ))
                .toList();
        List<DocumentEvidence> documentEvidence = documents.findByProjectId(projectId).stream()
                .map(value -> new DocumentEvidence(value.getId(), value.getTitle(), value.getType()))
                .sorted(Comparator.comparing(DocumentEvidence::id))
                .toList();
        List<String> reasons = new ArrayList<>();
        if (taskPage.hasNext()) {
            reasons.add("TASK_LIMIT_EXCEEDED");
        }
        ProjectTraceabilityResponse trace = traceability.projectTimeline(
                actor, projectId, MAX_TRACEABILITY_EVENTS
        );
        if (trace.truncated()) {
            reasons.add("TRACEABILITY_LIMIT_EXCEEDED");
        }
        return new InternalAgentToolResponses.SrsContext(
                project, teamContext, taskPage.getContent(), repositoryEvidence, trace,
                documentEvidence,
                new ContextBounds(
                        !reasons.isEmpty(), List.copyOf(reasons), taskPage.getTotalElements(),
                        taskPage.getNumberOfElements(), MAX_TASKS, MAX_TRACEABILITY_EVENTS
                )
        );
    }

    private Map<String, Long> statusCounts(List<TaskReadResponse> values) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TaskStatus status : TaskStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (TaskReadResponse value : values) {
            if (value.status() != null) {
                counts.compute(value.status().name(), (ignored, count) -> count == null ? 1L : count + 1L);
            }
        }
        return counts;
    }
}
