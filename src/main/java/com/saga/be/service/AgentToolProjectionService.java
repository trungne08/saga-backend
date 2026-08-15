package com.saga.be.service;

import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.InternalAgentToolResponses.ContextBounds;
import com.saga.be.dto.response.InternalAgentToolResponses.DocumentEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.MemberEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.RepositoryEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.TeamContext;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionMemberResponse;
import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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
    private static final int MAX_CONTEXT_COURSES = 50;
    private static final int MAX_CONTEXT_TEAMS = 100;
    private static final int MAX_ASSIGNEE_CANDIDATES = 20;

    private final ProjectDetailService projects;
    private final ProjectTaskReadService tasks;
    private final TeamContributionService contributions;
    private final GitHubTraceabilityService traceability;
    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final GitRepoRepository repositories;
    private final DocumentRepository documents;
    private final CommitReviewContextReader commitReviewContexts;
    private final ProjectSprintService sprints;
    private final CourseEarlyWarningQueryService earlyWarnings;
    private final ProjectIntegrationAuthorizationService authorization;
    private final StudentIdentityNormalizer identity;

    public AgentToolProjectionService(
            ProjectDetailService projects,
            ProjectTaskReadService tasks,
            TeamContributionService contributions,
            GitHubTraceabilityService traceability,
            TeamRepository teams,
            TeamMemberRepository teamMembers,
            CourseRepository courses,
            StudentRepository students,
            GitRepoRepository repositories,
            DocumentRepository documents,
            CommitReviewContextReader commitReviewContexts,
            ProjectSprintService sprints,
            CourseEarlyWarningQueryService earlyWarnings,
            ProjectIntegrationAuthorizationService authorization,
            StudentIdentityNormalizer identity
    ) {
        this.projects = projects;
        this.tasks = tasks;
        this.contributions = contributions;
        this.traceability = traceability;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.courses = courses;
        this.students = students;
        this.repositories = repositories;
        this.documents = documents;
        this.commitReviewContexts = commitReviewContexts;
        this.sprints = sprints;
        this.earlyWarnings = earlyWarnings;
        this.authorization = authorization;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.ResourceContext resourceContext(SagaPrincipal actor) {
        if (actor.applicationRole() == ApplicationRole.STUDENT) {
            return studentResourceContext(actor);
        }
        if (actor.applicationRole() == ApplicationRole.LECTURER) {
            return lecturerResourceContext(actor);
        }
        if (actor.applicationRole() == ApplicationRole.ADMIN) {
            return new InternalAgentToolResponses.ResourceContext(
                    actor.applicationRole().name(), "SYSTEM_SCOPE", 0, 0, 0,
                    List.of(), List.of(), currentActor(actor, "ZERO_MATCH", 0)
            );
        }
        return new InternalAgentToolResponses.ResourceContext(
                actor.applicationRole().name(), "ROLE_NOT_SUPPORTED", 0, 0, 0,
                List.of(), List.of("CONTEXT_DISCOVERY_NOT_AVAILABLE"),
                currentActor(actor, "ZERO_MATCH", 0)
        );
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
        InternalAgentToolResponses.ContributionSnapshot contribution = contributionSnapshot(
                contributions.evaluate(actor, teamId)
        );
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

    public InternalAgentToolResponses.ContributionSnapshot teamContribution(
            SagaPrincipal actor, UUID teamId
    ) {
        return contributionSnapshot(contributions.evaluate(actor, teamId));
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.StudentContribution studentContribution(
            SagaPrincipal actor, UUID teamId
    ) {
        if (actor.applicationRole() != ApplicationRole.STUDENT) {
            throw new AccessDeniedException("Student contribution is available only to the current Student");
        }
        teamMembers.findByTeamIdAndStudentId(teamId, actor.localProfileId())
                .orElseThrow(() -> new AccessDeniedException(
                        "You do not have access to this Team contribution"
                ));
        InternalAgentToolResponses.ContributionSnapshot snapshot = contributionSnapshot(
                contributions.evaluate(teamId)
        );
        InternalAgentToolResponses.ContributionMemberSnapshot current = snapshot.members().stream()
                .filter(value -> actor.localProfileId().equals(value.studentId()))
                .findFirst()
                .orElse(null);
        return new InternalAgentToolResponses.StudentContribution(
                actor.localProfileId(), snapshot.teamId(), snapshot.projectId(),
                snapshot.evaluatedAt(), current
        );
    }

    public SprintListResponse teamSprints(SagaPrincipal actor, UUID teamId) {
        return sprints.getByTeam(actor, teamId);
    }

    public LecturerAnalyticsResponses.EarlyWarnings courseWarnings(
            SagaPrincipal actor, UUID courseId
    ) {
        return earlyWarnings.get(actor, courseId);
    }

    public ProjectTraceabilityResponse projectTraceability(SagaPrincipal actor, UUID projectId) {
        return traceability.projectTimeline(actor, projectId, MAX_TRACEABILITY_EVENTS);
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.AssigneeResolution resolveAssignee(
            SagaPrincipal actor, UUID projectId, String fullName, String studentCode
    ) {
        authorization.requireProjectManager(actor, projectId);
        Team team = teams.findByProjectId(projectId).orElseThrow(() -> IntegrationException.conflict(
                "PROJECT_TEAM_MISSING", "The project is not assigned to a team"
        ));
        String normalizedCode = isBlank(studentCode) ? null : identity.normalizeStudentCode(studentCode);
        String normalizedName = isBlank(fullName) ? null : identity.normalizeFullName(fullName);
        if (normalizedCode == null && normalizedName == null) {
            throw IntegrationException.invalid(
                    "AGENT_ASSIGNEE_RESOLVE_EMPTY", "Either fullName or studentCode is required"
            );
        }
        List<Student> members = teamMembers.findByTeamId(team.getId()).stream()
                .map(TeamMember::getStudent)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<Student> matches;
        if (normalizedCode != null && normalizedName != null) {
            matches = members.stream()
                    .filter(student -> normalizedCode.equals(identity.normalizeStudentCode(student.getStudentCode()))
                            && normalizedName.equals(identity.normalizeFullName(student.getFullName())))
                    .toList();
        } else if (normalizedCode != null) {
            matches = members.stream()
                    .filter(student -> normalizedCode.equals(identity.normalizeStudentCode(student.getStudentCode())))
                    .toList();
        } else {
            matches = members.stream()
                    .filter(student -> normalizedName.equals(identity.normalizeFullName(student.getFullName())))
                    .toList();
        }
        String matchState;
        List<Student> bounded;
        if (matches.isEmpty()) {
            matchState = "NOT_FOUND";
            bounded = List.of();
        } else if (matches.size() == 1) {
            matchState = "MATCHED";
            bounded = matches;
        } else {
            matchState = "MULTIPLE_MATCH";
            bounded = matches.stream()
                    .sorted(Comparator.comparing(
                            Student::getStudentCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                    ).thenComparing(Student::getId))
                    .limit(MAX_ASSIGNEE_CANDIDATES)
                    .toList();
        }
        List<InternalAgentToolResponses.AssigneeCandidate> candidates = bounded.stream()
                .map(student -> new InternalAgentToolResponses.AssigneeCandidate(
                        student.getId(), student.getFullName(), student.getStudentCode()
                ))
                .toList();
        return new InternalAgentToolResponses.AssigneeResolution(
                projectId, team.getId(), matchState, candidates
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private InternalAgentToolResponses.ResourceContext studentResourceContext(SagaPrincipal actor) {
        List<TeamMember> memberships = teamMembers.findAgentContextsByStudentId(actor.localProfileId());
        Map<UUID, Course> coursesById = new LinkedHashMap<>();
        Map<UUID, List<InternalAgentToolResponses.ResourceTeamContext>> teamsByCourse = new LinkedHashMap<>();
        Set<UUID> teamIds = new LinkedHashSet<>();
        Set<UUID> projectIds = new LinkedHashSet<>();
        for (TeamMember membership : memberships) {
            Team team = membership.getTeam();
            if (team == null || team.getCourse() == null || team.getId() == null) {
                continue;
            }
            UUID courseId = team.getCourse().getId();
            coursesById.putIfAbsent(courseId, team.getCourse());
            if (teamIds.add(team.getId())) {
                teamsByCourse.computeIfAbsent(courseId, ignored -> new ArrayList<>()).add(
                        resourceTeam(team, membership.getRoleInTeam())
                );
            }
            if (team.getProject() != null) {
                projectIds.add(team.getProject().getId());
            }
        }
        return resourceContext(
                actor.applicationRole(), selectionState(projectIds.size()), coursesById,
                teamsByCourse, teamIds.size(), projectIds.size(), currentActor(actor, ledState(memberships), ledCount(memberships))
        );
    }

    private InternalAgentToolResponses.ResourceContext lecturerResourceContext(SagaPrincipal actor) {
        List<Course> instructedCourses = courses
                .findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(actor.localProfileId());
        Map<UUID, Course> coursesById = new LinkedHashMap<>();
        Map<UUID, List<InternalAgentToolResponses.ResourceTeamContext>> teamsByCourse = new LinkedHashMap<>();
        Set<UUID> teamIds = new LinkedHashSet<>();
        Set<UUID> projectIds = new LinkedHashSet<>();
        for (Course course : instructedCourses) {
            coursesById.put(course.getId(), course);
            List<InternalAgentToolResponses.ResourceTeamContext> courseTeams = new ArrayList<>();
            for (Team team : teams.findByCourseIdOrderByNameAscIdAsc(course.getId())) {
                if (teamIds.add(team.getId())) {
                    courseTeams.add(resourceTeam(team, null));
                }
                if (team.getProject() != null) {
                    projectIds.add(team.getProject().getId());
                }
            }
            teamsByCourse.put(course.getId(), courseTeams);
        }
        return resourceContext(
                actor.applicationRole(), selectionState(coursesById.size()), coursesById,
                teamsByCourse, teamIds.size(), projectIds.size(),
                currentActor(actor, "ZERO_MATCH", 0)
        );
    }

    private InternalAgentToolResponses.ResourceContext resourceContext(
            ApplicationRole role,
            String selectionState,
            Map<UUID, Course> coursesById,
            Map<UUID, List<InternalAgentToolResponses.ResourceTeamContext>> teamsByCourse,
            long totalTeams,
            long totalProjects,
            InternalAgentToolResponses.CurrentActor currentActor
    ) {
        List<String> limitations = new ArrayList<>();
        if (coursesById.size() > MAX_CONTEXT_COURSES) {
            limitations.add("COURSE_LIMIT_EXCEEDED");
        }
        if (totalTeams > MAX_CONTEXT_TEAMS) {
            limitations.add("TEAM_LIMIT_EXCEEDED");
        }
        List<InternalAgentToolResponses.CourseContext> result = new ArrayList<>();
        int includedTeams = 0;
        for (Map.Entry<UUID, Course> entry : coursesById.entrySet()) {
            if (result.size() >= MAX_CONTEXT_COURSES) {
                break;
            }
            List<InternalAgentToolResponses.ResourceTeamContext> boundedTeams = new ArrayList<>();
            for (InternalAgentToolResponses.ResourceTeamContext team
                    : teamsByCourse.getOrDefault(entry.getKey(), List.of())) {
                if (includedTeams >= MAX_CONTEXT_TEAMS) {
                    break;
                }
                boundedTeams.add(team);
                includedTeams++;
            }
            Course course = entry.getValue();
            result.add(new InternalAgentToolResponses.CourseContext(
                    course.getId(), course.getCourseCode(), course.getName(), List.copyOf(boundedTeams)
            ));
        }
        return new InternalAgentToolResponses.ResourceContext(
                role.name(), selectionState, coursesById.size(), totalTeams, totalProjects,
                List.copyOf(result), List.copyOf(limitations), currentActor
        );
    }

    private InternalAgentToolResponses.ResourceTeamContext resourceTeam(
            Team team, com.saga.be.entity.enums.RoleInTeam currentStudentRole
    ) {
        InternalAgentToolResponses.ResourceProjectContext project = team.getProject() == null
                ? null
                : new InternalAgentToolResponses.ResourceProjectContext(
                        team.getProject().getId(), team.getProject().getName()
                );
        return new InternalAgentToolResponses.ResourceTeamContext(
                team.getId(), team.getName(), currentStudentRole, project
        );
    }

    private String selectionState(long matches) {
        if (matches == 0) {
            return "ZERO_MATCH";
        }
        return matches == 1 ? "SINGLE_MATCH" : "MULTIPLE_MATCH";
    }

    private InternalAgentToolResponses.CurrentActor currentActor(
            SagaPrincipal actor, String teamRoleSelectionState, long ledTeamCount
    ) {
        String studentCode = null;
        if (actor.applicationRole() == ApplicationRole.STUDENT) {
            studentCode = students.findById(actor.localProfileId())
                    .map(com.saga.be.entity.Student::getStudentCode)
                    .orElse(null);
        }
        return new InternalAgentToolResponses.CurrentActor(
                actor.applicationRole().name(),
                actor.localProfileId(),
                actor.fullName(),
                studentCode,
                "SAGA_PRINCIPAL_SESSION",
                teamRoleSelectionState,
                ledTeamCount
        );
    }

    private long ledCount(List<TeamMember> memberships) {
        return memberships.stream()
                .filter(membership -> membership.getRoleInTeam() == com.saga.be.entity.enums.RoleInTeam.LEADER)
                .count();
    }

    private String ledState(List<TeamMember> memberships) {
        return selectionState(ledCount(memberships));
    }

    private InternalAgentToolResponses.ContributionSnapshot contributionSnapshot(
            TeamContributionEvaluationResponse value
    ) {
        return new InternalAgentToolResponses.ContributionSnapshot(
                value.teamId(), value.projectId(), value.evaluatedAt(),
                value.members().stream().map(this::contributionMemberSnapshot).toList()
        );
    }

    private InternalAgentToolResponses.ContributionMemberSnapshot contributionMemberSnapshot(
            TeamContributionMemberResponse value
    ) {
        return new InternalAgentToolResponses.ContributionMemberSnapshot(
                value.studentId(), value.fullName(), value.studentCode(),
                value.codeContributionPercentage(), value.documentContributionPercentage(),
                value.researchContributionPercentage(), value.taskContributionPercentage(),
                value.finalContributionPercentage(), value.evidenceCount()
        );
    }
}
