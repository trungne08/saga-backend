package com.saga.be.service;

import com.saga.be.config.JiraTimeZoneProperties;
import com.saga.be.dto.response.AdminAnomaliesReportResponse;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.InternalAgentToolResponses.CommitReviewOperationalAggregate;
import com.saga.be.dto.response.InternalAgentToolResponses.ConfirmedWarning;
import com.saga.be.dto.response.InternalAgentToolResponses.CourseContext;
import com.saga.be.dto.response.InternalAgentToolResponses.CourseReportMetadata;
import com.saga.be.dto.response.InternalAgentToolResponses.OverdueTaskEvidence;
import com.saga.be.dto.response.InternalAgentToolResponses.RecentCommit;
import com.saga.be.dto.response.InternalAgentToolResponses.ResourceTeamContext;
import com.saga.be.dto.response.InternalAgentToolResponses.TeamReportSection;
import com.saga.be.dto.response.InternalAgentToolResponses.TraceabilitySummary;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.LecturerCourseDashboardResponses;
import com.saga.be.dto.response.ProjectTraceabilityResponse;
import com.saga.be.entity.BusinessWarning;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AdminAnomalySignalType;
import com.saga.be.entity.enums.AdminReportSupportStatus;
import com.saga.be.entity.enums.BusinessWarningCategory;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.BusinessWarningRepository;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewIntentRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentRoleAwareProjectionService {

    static final String LECTURER_REPORT_VERSION = "saga-lecturer-course-report-context-v1";
    static final String ADMIN_REPORT_VERSION = "saga-admin-system-report-context-v1";
    static final String LEADER_REPORT_VERSION = "saga-leader-team-report-context-v1";
    static final String LECTURER_ARTIFACT_TYPE = "LECTURER_PROGRESS_REPORT";
    static final String LEADER_ARTIFACT_TYPE = "LEADER_TEAM_PROGRESS_REPORT";
    static final String ADMIN_ARTIFACT_TYPE = "ADMIN_SYSTEM_REPORT";
    static final int RECENT_COMMIT_LIMIT = 10;
    static final int OVERDUE_TASK_LIMIT = 50;
    static final int COURSE_OVERVIEW_LIMIT = 50;
    static final int VELOCITY_TEAM_LIMIT = 50;
    static final int ACTIVITY_WINDOW_DAYS = 14;

    private static final List<String> UNSUPPORTED_WARNING_SIGNALS = List.of(
            "INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"
    );
    private static final List<String> LECTURER_UNSUPPORTED = List.of(
            "finalGrade", "aiRiskScore", "fabricatedTrend", "providerCredentials",
            "cognitoSubject", "rawSensitiveLogs", "graphProcessingHistory"
    );
    private static final List<String> ADMIN_UNSUPPORTED = List.of(
            "secrets", "rawProviderPayload", "cognitoSubject",
            "perUserHistoricalAudit", "officialGrade", "graphProcessingHistory"
    );
    private static final List<String> ADMIN_UNSUPPORTED_SIGNALS = List.of(
            "INACTIVITY_GRACE_PERIOD=TBD_PRODUCT",
            "MSR",
            "DEADLINE_PROCESS",
            "SNA_ISOLATION"
    );

    private final AgentToolProjectionService projections;
    private final TeamMemberRepository teamMembers;
    private final CourseRepository courses;
    private final IdentityMapRepository identityMaps;
    private final CommitDataRepository commits;
    private final TaskRepository tasks;
    private final LecturerStudentAnalyticsQueryService studentAnalytics;
    private final LecturerCourseDashboardQueryService courseDashboard;
    private final CourseEarlyWarningQueryService earlyWarnings;
    private final LecturerTeamAnalyticsQueryService teamAnalytics;
    private final AdminReadService adminReads;
    private final AdminDashboardReportService adminReports;
    private final LecturerAnalyticsAuthorizationService courseAccess;
    private final CommitReviewIntentRepository reviewIntents;
    private final BusinessWarningRepository businessWarnings;
    private final JiraTimeZoneProperties jiraTimeZone;

    public AgentRoleAwareProjectionService(
            AgentToolProjectionService projections,
            TeamMemberRepository teamMembers,
            CourseRepository courses,
            IdentityMapRepository identityMaps,
            CommitDataRepository commits,
            TaskRepository tasks,
            LecturerStudentAnalyticsQueryService studentAnalytics,
            LecturerCourseDashboardQueryService courseDashboard,
            CourseEarlyWarningQueryService earlyWarnings,
            LecturerTeamAnalyticsQueryService teamAnalytics,
            AdminReadService adminReads,
            AdminDashboardReportService adminReports,
            LecturerAnalyticsAuthorizationService courseAccess,
            CommitReviewIntentRepository reviewIntents,
            BusinessWarningRepository businessWarnings,
            JiraTimeZoneProperties jiraTimeZone
    ) {
        this.projections = projections;
        this.teamMembers = teamMembers;
        this.courses = courses;
        this.identityMaps = identityMaps;
        this.commits = commits;
        this.tasks = tasks;
        this.studentAnalytics = studentAnalytics;
        this.courseDashboard = courseDashboard;
        this.earlyWarnings = earlyWarnings;
        this.teamAnalytics = teamAnalytics;
        this.adminReads = adminReads;
        this.adminReports = adminReports;
        this.courseAccess = courseAccess;
        this.reviewIntents = reviewIntents;
        this.businessWarnings = businessWarnings;
        this.jiraTimeZone = jiraTimeZone;
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.SelfProgress selfProgress(
            SagaPrincipal actor, UUID courseId, UUID projectId
    ) {
        requireStudent(actor);
        List<TeamMember> memberships = ownedMemberships(actor.localProfileId());
        List<TeamMember> matches = memberships.stream()
                .filter(membership -> matchesCourseAndProject(membership, courseId, projectId))
                .toList();
        if (matches.isEmpty()) {
            return emptySelfProgress("ZERO_MATCH", candidates(memberships));
        }
        if (matches.size() > 1) {
            return emptySelfProgress("MULTIPLE_MATCH", candidates(matches));
        }
        TeamMember membership = matches.get(0);
        Team team = membership.getTeam();
        UUID resolvedCourse = team.getCourse().getId();
        UUID resolvedProject = team.getProject() == null ? null : team.getProject().getId();
        LecturerAnalyticsResponses.StudentProgress progress = studentAnalytics.progress(
                actor, resolvedCourse, actor.localProfileId()
        );
        InternalAgentToolResponses.StudentProgress assigned = resolvedProject == null
                ? null
                : projections.studentProgress(actor, resolvedProject);
        List<Task> ownTasks = resolvedProject == null
                ? List.of()
                : tasks.findByProjectIdAndAssigneeId(resolvedProject, actor.localProfileId());
        WarningSplit split = warningSplit(
                deadlineWarnings(team.getId(), ownTasks),
                businessWarnings.findByStudentIdOrderByCreatedAtDescIdDesc(actor.localProfileId()),
                team.getId(),
                actor.localProfileId()
        );
        return new InternalAgentToolResponses.SelfProgress(
                "SINGLE_MATCH", resolvedCourse, team.getId(), resolvedProject,
                progress, assigned,
                split.confirmed(),
                split.advisories(),
                split.unsupported(),
                List.of(),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.RecentCommits recentCommits(SagaPrincipal actor) {
        requireStudent(actor);
        List<IdentityMap> active = identityMaps.findByStudentIdOrderByProvider(actor.localProfileId())
                .stream()
                .filter(mapping -> mapping.getProvider() == IntegrationProvider.GITHUB
                        && mapping.getMappingStatus() == IdentityMappingStatus.ACTIVE)
                .toList();
        if (active.isEmpty()) {
            return new InternalAgentToolResponses.RecentCommits(
                    "MISSING", "ZERO_MATCH", List.of(), List.of("ACTIVE_GITHUB_IDENTITY_MAPPING_REQUIRED")
            );
        }
        if (active.size() > 1) {
            return new InternalAgentToolResponses.RecentCommits(
                    "AMBIGUOUS", "ZERO_MATCH", List.of(), List.of("ACTIVE_GITHUB_IDENTITY_MAPPING_AMBIGUOUS")
            );
        }
        IdentityMap mapping = active.get(0);
        if (mapping.getExternalAccountId() == null || mapping.getExternalAccountId().isBlank()) {
            return new InternalAgentToolResponses.RecentCommits(
                    "MISSING", "ZERO_MATCH", List.of(), List.of("ACTIVE_GITHUB_IDENTITY_MAPPING_REQUIRED")
            );
        }
        List<CommitData> found = commits.findRecentByAuthorIdentity(
                actor.localProfileId(),
                mapping.getExternalAccountId(),
                PageRequest.of(0, RECENT_COMMIT_LIMIT)
        );
        List<RecentCommit> items = found.stream().map(this::recentCommit).toList();
        String selection = items.isEmpty() ? "ZERO_MATCH" : items.size() == 1 ? "SINGLE_MATCH" : "MULTIPLE_MATCH";
        return new InternalAgentToolResponses.RecentCommits("ACTIVE", selection, items, List.of());
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.LeaderTeamContext leaderTeamContext(
            SagaPrincipal actor, UUID teamId
    ) {
        requireStudent(actor);
        List<TeamMember> led = ownedMemberships(actor.localProfileId()).stream()
                .filter(membership -> membership.getRoleInTeam() == RoleInTeam.LEADER)
                .toList();
        List<TeamMember> matches = teamId == null
                ? led
                : led.stream().filter(membership -> teamId.equals(membership.getTeam().getId())).toList();
        if (matches.isEmpty()) {
            return emptyLeaderContext("ZERO_MATCH", candidates(led));
        }
        if (matches.size() > 1) {
            return emptyLeaderContext("MULTIPLE_MATCH", candidates(matches));
        }
        Team team = matches.get(0).getTeam();
        InternalAgentToolResponses.TeamProgress progress = projections.teamProgress(actor, team.getId());
        List<Task> teamTasks = team.getProject() == null
                ? List.of()
                : tasks.findByProjectId(team.getProject().getId());
        WarningSplit split = warningSplit(
                deadlineWarnings(team.getId(), teamTasks),
                businessWarnings.findByTeamIdOrderByCreatedAtDescIdDesc(team.getId()),
                team.getId(),
                null
        );
        return new InternalAgentToolResponses.LeaderTeamContext(
                "SINGLE_MATCH",
                team.getId(),
                progress,
                overdueTasks(team),
                split.confirmed(),
                split.unsupported(),
                split.advisories(),
                List.of(),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.LeaderTeamProgressReport leaderTeamProgressReport(
            SagaPrincipal actor, UUID teamId
    ) {
        requireStudent(actor);
        List<TeamMember> led = ownedMemberships(actor.localProfileId()).stream()
                .filter(membership -> membership.getRoleInTeam() == RoleInTeam.LEADER)
                .toList();
        List<TeamMember> matches = teamId == null
                ? led
                : led.stream().filter(membership -> teamId.equals(membership.getTeam().getId())).toList();
        if (matches.isEmpty()) {
            return emptyLeaderReport("ZERO_MATCH", candidates(led));
        }
        if (matches.size() > 1) {
            return emptyLeaderReport("MULTIPLE_MATCH", candidates(matches));
        }
        Team team = matches.get(0).getTeam();
        UUID projectId = team.getProject() == null ? null : team.getProject().getId();
        List<Task> teamTasks = projectId == null ? List.of() : tasks.findByProjectId(projectId);
        WarningSplit split = warningSplit(
                deadlineWarnings(team.getId(), teamTasks),
                businessWarnings.findByTeamIdOrderByCreatedAtDescIdDesc(team.getId()),
                team.getId(),
                null
        );
        return new InternalAgentToolResponses.LeaderTeamProgressReport(
                LEADER_REPORT_VERSION,
                LEADER_ARTIFACT_TYPE,
                "SINGLE_MATCH",
                OffsetDateTime.now(ZoneOffset.UTC),
                team.getId(),
                team.getName(),
                projectId,
                projections.teamProgress(actor, team.getId()),
                overdueTasks(team),
                projectTraceability(actor, projectId),
                operationalAggregate(projectId == null
                        ? List.of()
                        : reviewIntents.countStatusByProjectIds(List.of(projectId))),
                split.confirmed(),
                split.advisories(),
                split.unsupported(),
                List.of(),
                List.of("finalGrade", "aiRiskScore"),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.LecturerCourseContext lecturerCourseContext(
            SagaPrincipal actor, UUID courseId
    ) {
        requireLecturer(actor);
        List<Course> instructed = instructedCourses(actor);
        List<Course> matches = courseId == null
                ? instructed
                : instructed.stream().filter(course -> courseId.equals(course.getId())).toList();
        if (matches.isEmpty()) {
            return new InternalAgentToolResponses.LecturerCourseContext(
                    "ZERO_MATCH", null, null, null, null, null, null,
                    courseCandidates(instructed), List.of()
            );
        }
        if (matches.size() > 1) {
            return new InternalAgentToolResponses.LecturerCourseContext(
                    "MULTIPLE_MATCH", null, null, null, null, null, null,
                    courseCandidates(matches), List.of()
            );
        }
        Course course = matches.get(0);
        requireExactLecturerCourse(actor, course.getId());
        return new InternalAgentToolResponses.LecturerCourseContext(
                "SINGLE_MATCH",
                course.getId(),
                courseDashboard.teamsProgress(actor, course.getId()),
                courseDashboard.contributionSummary(actor, course.getId()),
                courseDashboard.trends(actor, course.getId()),
                courseDashboard.atRiskSummary(actor, course.getId()),
                earlyWarnings.get(actor, course.getId()),
                List.of(),
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.LecturerProgressReport lecturerProgressReport(
            SagaPrincipal actor, UUID courseId
    ) {
        if (actor != null && actor.applicationRole() == ApplicationRole.ADMIN) {
            return adminCourseProgressReport(actor, courseId);
        }
        requireLecturer(actor);
        List<Course> instructed = instructedCourses(actor);
        List<Course> matches = courseId == null
                ? instructed
                : instructed.stream().filter(course -> courseId.equals(course.getId())).toList();
        if (matches.size() != 1) {
            return emptyLecturerReport(
                    matches.isEmpty() ? "ZERO_MATCH" : "MULTIPLE_MATCH",
                    courseCandidates(matches.isEmpty() ? instructed : matches),
                    List.of()
            );
        }
        Course course = requireExactLecturerCourse(actor, matches.get(0).getId());
        return buildLecturerReport(actor, course);
    }

    @Transactional(readOnly = true)
    public InternalAgentToolResponses.AdminSystemReport adminSystemReport(SagaPrincipal actor) {
        if (actor == null || actor.applicationRole() != ApplicationRole.ADMIN) {
            throw new AccessDeniedException("Admin system report is available only to ADMIN");
        }
        AdminAnomaliesReportResponse anomalies = adminReports.anomalies();
        return new InternalAgentToolResponses.AdminSystemReport(
                ADMIN_REPORT_VERSION,
                ADMIN_ARTIFACT_TYPE,
                OffsetDateTime.now(ZoneOffset.UTC),
                adminReads.systemStats(),
                adminReads.integrationHealth(),
                adminReads.courseProgressOverview(null, null, null, 0, COURSE_OVERVIEW_LIMIT),
                anomalies,
                adminReports.graphProcessing(),
                operationalAggregate(reviewIntents.countStatusAll()),
                confirmedAdminWarnings(anomalies),
                ADMIN_UNSUPPORTED_SIGNALS,
                adminReviewAdvisories(),
                ADMIN_UNSUPPORTED,
                List.of()
        );
    }

    private InternalAgentToolResponses.LecturerProgressReport adminCourseProgressReport(
            SagaPrincipal actor, UUID courseId
    ) {
        if (courseId == null) {
            return emptyLecturerReport("ZERO_MATCH", List.of(), List.of("COURSE_SELECTION_REQUIRED"));
        }
        courseAccess.requireCourseAccess(actor, courseId);
        Course course = courses.findWithReportDetailsByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AccessDeniedException("You do not have access to this Course"));
        return buildLecturerReport(actor, course);
    }

    private InternalAgentToolResponses.LecturerProgressReport buildLecturerReport(
            SagaPrincipal actor, Course course
    ) {
        UUID courseId = course.getId();
        LecturerCourseDashboardResponses.TeamsProgress teamsProgress = courseDashboard.teamsProgress(actor, courseId);
        LecturerAnalyticsResponses.EarlyWarnings warnings = earlyWarnings.get(actor, courseId);
        List<TeamReportSection> sections = new ArrayList<>();
        List<LecturerAnalyticsResponses.SprintVelocity> velocities = new ArrayList<>();
        List<ConfirmedWarning> confirmed = new ArrayList<>(earlyWarningSignals(warnings));
        List<String> advisories = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        List<UUID> projectIds = new ArrayList<>();
        int included = 0;
        List<LecturerCourseDashboardResponses.TeamProgress> teams = teamsProgress == null || teamsProgress.teams() == null
                ? List.of()
                : teamsProgress.teams();
        for (LecturerCourseDashboardResponses.TeamProgress team : teams) {
            if (included >= VELOCITY_TEAM_LIMIT) {
                limitations.add("TEAM_LIMIT_EXCEEDED");
                break;
            }
            TeamReportSection section = teamSection(actor, courseId, team, limitations);
            sections.add(section);
            if (section.velocity() != null) {
                velocities.add(section.velocity());
            }
            confirmed.addAll(section.confirmedWarnings());
            advisories.addAll(section.reviewAdvisories());
            if (section.projectId() != null) {
                projectIds.add(section.projectId());
            }
            included++;
        }
        return new InternalAgentToolResponses.LecturerProgressReport(
                LECTURER_REPORT_VERSION,
                LECTURER_ARTIFACT_TYPE,
                "SINGLE_MATCH",
                OffsetDateTime.now(ZoneOffset.UTC),
                courseId,
                new CourseReportMetadata(
                        courseId,
                        course.getCourseCode(),
                        course.getName(),
                        course.getInstructor() == null ? null : course.getInstructor().getId(),
                        course.getInstructor() == null ? null : course.getInstructor().getFullName()
                ),
                List.copyOf(sections),
                teamsProgress,
                courseDashboard.contributionSummary(actor, courseId),
                courseDashboard.trends(actor, courseId),
                courseDashboard.atRiskSummary(actor, courseId),
                warnings,
                List.copyOf(velocities),
                operationalAggregate(projectIds.isEmpty()
                        ? List.of()
                        : reviewIntents.countStatusByProjectIds(projectIds)),
                List.copyOf(confirmed),
                UNSUPPORTED_WARNING_SIGNALS,
                List.copyOf(advisories),
                List.of(),
                LECTURER_UNSUPPORTED,
                List.copyOf(limitations)
        );
    }

    private TeamReportSection teamSection(
            SagaPrincipal actor,
            UUID courseId,
            LecturerCourseDashboardResponses.TeamProgress team,
            List<String> limitations
    ) {
        UUID teamId = team.teamId();
        UUID projectId = team.projectId();
        LecturerAnalyticsResponses.SprintVelocity velocity = teamAnalytics.velocity(actor, courseId, teamId);
        LecturerAnalyticsResponses.ActivityOverview activities = null;
        LecturerAnalyticsResponses.BurndownChart burndown = null;
        LocalDate end = today();
        LocalDate start = end.minusDays(ACTIVITY_WINDOW_DAYS - 1L);
        try {
            activities = teamAnalytics.overview(actor, courseId, teamId, start, end);
        } catch (ResponseStatusException ignored) {
            limitations.add("ACTIVITY_OVERVIEW_UNAVAILABLE:" + teamId);
        }
        if (team.activeSprints() != null && team.activeSprints().size() > 1) {
            limitations.add("PARALLEL_ACTIVE_SPRINTS_BURNDOWN_OMITTED:" + teamId);
        } else if (team.currentSprint() != null) {
            try {
                burndown = teamAnalytics.burndown(actor, courseId, teamId, team.currentSprint().sprintId());
            } catch (ResponseStatusException ignored) {
                limitations.add("BURNDOWN_UNAVAILABLE:" + teamId);
            }
        }
        InternalAgentToolResponses.ContributionSnapshot contribution = projectId == null
                ? null
                : projections.teamContribution(actor, teamId);
        TraceabilitySummary traceability = null;
        if (projectId != null) {
            ProjectTraceabilityResponse trace = projections.projectTraceability(actor, projectId);
            if (trace != null) {
                traceability = new TraceabilitySummary(
                        projectId,
                        trace.timeline() == null ? 0 : trace.timeline().size(),
                        trace.truncated(),
                        List.of()
                );
            }
        }
        List<Task> teamTasks = projectId == null ? List.of() : tasks.findByProjectId(projectId);
        WarningSplit split = warningSplit(
                deadlineWarnings(teamId, teamTasks),
                businessWarnings.findByTeamIdOrderByCreatedAtDescIdDesc(teamId),
                teamId,
                null
        );
        return new TeamReportSection(
                teamId,
                team.teamName(),
                projectId,
                team,
                contribution,
                velocity,
                activities,
                burndown,
                traceability,
                split.confirmed(),
                split.advisories(),
                split.unsupported(),
                List.of()
        );
    }

    private InternalAgentToolResponses.SelfProgress emptySelfProgress(
            String selection, List<ResourceTeamContext> candidates
    ) {
        return new InternalAgentToolResponses.SelfProgress(
                selection, null, null, null, null, null,
                List.of(), List.of(), UNSUPPORTED_WARNING_SIGNALS, candidates, List.of()
        );
    }

    private InternalAgentToolResponses.LeaderTeamContext emptyLeaderContext(
            String selection, List<ResourceTeamContext> candidates
    ) {
        return new InternalAgentToolResponses.LeaderTeamContext(
                selection, null, null, List.of(),
                List.of(), UNSUPPORTED_WARNING_SIGNALS, List.of(),
                candidates, List.of()
        );
    }

    private InternalAgentToolResponses.LeaderTeamProgressReport emptyLeaderReport(
            String selection, List<ResourceTeamContext> candidates
    ) {
        return new InternalAgentToolResponses.LeaderTeamProgressReport(
                LEADER_REPORT_VERSION,
                LEADER_ARTIFACT_TYPE,
                selection,
                OffsetDateTime.now(ZoneOffset.UTC),
                null, null, null, null, List.of(), null, emptyOperational(),
                List.of(), List.of(), UNSUPPORTED_WARNING_SIGNALS,
                candidates, List.of("finalGrade", "aiRiskScore"), List.of()
        );
    }

    private InternalAgentToolResponses.LecturerProgressReport emptyLecturerReport(
            String selection, List<CourseContext> candidates, List<String> limitations
    ) {
        return new InternalAgentToolResponses.LecturerProgressReport(
                LECTURER_REPORT_VERSION,
                LECTURER_ARTIFACT_TYPE,
                selection,
                OffsetDateTime.now(ZoneOffset.UTC),
                null, null, List.of(), null, null, null, null, null, List.of(),
                emptyOperational(),
                List.of(),
                UNSUPPORTED_WARNING_SIGNALS,
                List.of(),
                candidates,
                LECTURER_UNSUPPORTED,
                limitations
        );
    }

    private Course requireExactLecturerCourse(SagaPrincipal actor, UUID courseId) {
        Course course = courses.findWithReportDetailsByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AccessDeniedException("You do not have access to this Course"));
        if (actor.applicationRole() != ApplicationRole.LECTURER
                || course.getInstructor() == null
                || !actor.localProfileId().equals(course.getInstructor().getId())) {
            throw new AccessDeniedException("You do not have access to this Course");
        }
        return course;
    }

    private List<Course> instructedCourses(SagaPrincipal actor) {
        return courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(actor.localProfileId());
    }

    private List<TeamMember> ownedMemberships(UUID studentId) {
        return teamMembers.findAgentContextsByStudentId(studentId);
    }

    private boolean matchesCourseAndProject(TeamMember membership, UUID courseId, UUID projectId) {
        Team team = membership.getTeam();
        if (team == null || team.getCourse() == null) {
            return false;
        }
        if (courseId != null && !courseId.equals(team.getCourse().getId())) {
            return false;
        }
        if (projectId != null && (team.getProject() == null || !projectId.equals(team.getProject().getId()))) {
            return false;
        }
        return true;
    }

    private List<ResourceTeamContext> candidates(List<TeamMember> memberships) {
        return memberships.stream()
                .map(membership -> projectionsResourceTeam(membership.getTeam(), membership.getRoleInTeam()))
                .toList();
    }

    private ResourceTeamContext projectionsResourceTeam(Team team, RoleInTeam role) {
        InternalAgentToolResponses.ResourceProjectContext project = team.getProject() == null
                ? null
                : new InternalAgentToolResponses.ResourceProjectContext(
                        team.getProject().getId(), team.getProject().getName()
                );
        return new ResourceTeamContext(team.getId(), team.getName(), role, project);
    }

    private List<CourseContext> courseCandidates(List<Course> values) {
        return values.stream()
                .map(course -> new CourseContext(
                        course.getId(), course.getCourseCode(), course.getName(), List.of()
                ))
                .toList();
    }

    private List<OverdueTaskEvidence> overdueTasks(Team team) {
        if (team.getProject() == null) {
            return List.of();
        }
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        return tasks.findByProjectId(team.getProject().getId()).stream()
                .filter(task -> task.getDeletedAt() == null
                        && task.getDueDate() != null
                        && task.getDueDate().isBefore(nowUtc)
                        && task.getStatus() != TaskStatus.DONE)
                .sorted(Comparator.comparing(Task::getDueDate).thenComparing(Task::getId))
                .limit(OVERDUE_TASK_LIMIT)
                .map(task -> new OverdueTaskEvidence(
                        task.getId(),
                        task.getTitle(),
                        task.getAssignee() == null ? null : task.getAssignee().getId(),
                        task.getAssignee() == null ? null : task.getAssignee().getFullName(),
                        "OVERDUE_TASK",
                        "Task is not DONE and dueDate is before now",
                        task.getDueDate()
                ))
                .toList();
    }

    private List<ConfirmedWarning> deadlineWarnings(UUID teamId, List<Task> source) {
        LocalDate today = today();
        return source.stream()
                .map(task -> {
                    String signal = AgentDeadlineClassifier.classify(task, today);
                    if (signal == null) {
                        return null;
                    }
                    return new ConfirmedWarning(
                            signal,
                            teamId,
                            task.getId(),
                            task.getAssignee() == null ? null : task.getAssignee().getId(),
                            task.getAssignee() == null ? null : task.getAssignee().getFullName(),
                            "Date-only dueDate vs Jira zone today",
                            task.getDueDate()
                    );
                })
                .filter(warning -> warning != null)
                .sorted(Comparator.comparing(ConfirmedWarning::dueDate).thenComparing(ConfirmedWarning::taskId))
                .limit(OVERDUE_TASK_LIMIT)
                .toList();
    }

    private List<ConfirmedWarning> earlyWarningSignals(LecturerAnalyticsResponses.EarlyWarnings source) {
        if (source == null || source.warnings() == null) {
            return List.of();
        }
        return source.warnings().stream()
                .map(warning -> new ConfirmedWarning(
                        warning.warningType(),
                        warning.teamId(),
                        warning.taskId(),
                        warning.studentId(),
                        null,
                        warning.message(),
                        warning.dueDate()
                ))
                .toList();
    }

    private List<ConfirmedWarning> confirmedAdminWarnings(AdminAnomaliesReportResponse anomalies) {
        List<ConfirmedWarning> confirmed = new ArrayList<>(anomalies == null || anomalies.signals() == null
                ? List.of()
                : anomalies.signals().stream()
                        .filter(signal -> signal.type() == AdminAnomalySignalType.OVERDUE_TASK
                                && signal.supportStatus() == AdminReportSupportStatus.SUPPORTED)
                        .map(signal -> new ConfirmedWarning(
                                "OVERDUE_TASK",
                                null,
                                null,
                                null,
                                null,
                                "Authoritative overdue assigned team-member task aggregate count=" + signal.count(),
                                null
                        ))
                        .toList());
        for (NotificationType type : List.of(
                NotificationType.COMMIT_REVIEW_NEEDS_CHANGES,
                NotificationType.MEMBER_NO_RECENT_ACTIVITY_3D,
                NotificationType.TEAM_NO_RECENT_ACTIVITY_3D,
                NotificationType.SPRINT_PROGRESS_BEHIND,
                NotificationType.REPEATED_COMMIT_ISSUES
        )) {
            long count = businessWarnings.countByWarningType(type);
            if (count <= 0) {
                continue;
            }
            confirmed.add(new ConfirmedWarning(
                    type.name(),
                    null,
                    null,
                    null,
                    null,
                    "Supported count=" + count,
                    null
            ));
        }
        return List.copyOf(confirmed);
    }

    private List<String> adminReviewAdvisories() {
        List<String> advisories = new ArrayList<>();
        for (NotificationType type : List.of(
                NotificationType.UNLINKED_COMMIT_ADVISORY,
                NotificationType.HISTORICAL_REVIEW_DIGEST
        )) {
            long count = businessWarnings.countByWarningType(type);
            if (count > 0) {
                advisories.add(type.name() + " count=" + count);
            }
        }
        return List.copyOf(advisories);
    }

    private WarningSplit warningSplit(
            List<ConfirmedWarning> deadlines,
            List<BusinessWarning> source,
            UUID teamId,
            UUID studentId
    ) {
        List<ConfirmedWarning> confirmed = new ArrayList<>(deadlines == null ? List.of() : deadlines);
        List<String> advisories = new ArrayList<>();
        if (source != null) {
            for (BusinessWarning warning : source) {
                if (warning == null) {
                    continue;
                }
                if (teamId != null && warning.getTeamId() != null && !teamId.equals(warning.getTeamId())) {
                    continue;
                }
                if (studentId != null && warning.getStudentId() != null
                        && !studentId.equals(warning.getStudentId())) {
                    continue;
                }
                if (warning.getCategory() == BusinessWarningCategory.ADVISORY) {
                    advisories.add(advisoryText(warning));
                    continue;
                }
                confirmed.add(toConfirmed(warning));
            }
        }
        return new WarningSplit(
                List.copyOf(confirmed),
                List.copyOf(advisories),
                UNSUPPORTED_WARNING_SIGNALS
        );
    }

    private ConfirmedWarning toConfirmed(BusinessWarning warning) {
        StringBuilder evidence = new StringBuilder(warning.getEvidenceSummary());
        if (warning.getCommitSha() != null && !warning.getCommitSha().isBlank()) {
            evidence.append(" sha=").append(warning.getCommitSha());
        }
        if (warning.getProgressMode() != null) {
            evidence.append(" mode=").append(warning.getProgressMode().name());
        }
        if (warning.getSeverity() != null) {
            evidence.append(" severity=").append(warning.getSeverity().name());
        }
        return new ConfirmedWarning(
                warning.getWarningType().name(),
                warning.getTeamId(),
                null,
                warning.getStudentId(),
                null,
                evidence.toString(),
                null
        );
    }

    private String advisoryText(BusinessWarning warning) {
        StringBuilder text = new StringBuilder(warning.getWarningType().name());
        text.append(": ").append(warning.getEvidenceSummary());
        if (warning.getCommitSha() != null && !warning.getCommitSha().isBlank()) {
            text.append(" sha=").append(warning.getCommitSha());
        }
        return text.toString();
    }

    private TraceabilitySummary projectTraceability(SagaPrincipal actor, UUID projectId) {
        if (projectId == null) {
            return null;
        }
        ProjectTraceabilityResponse trace = projections.projectTraceability(actor, projectId);
        if (trace == null) {
            return null;
        }
        return new TraceabilitySummary(
                projectId,
                trace.timeline() == null ? 0 : trace.timeline().size(),
                trace.truncated(),
                List.of()
        );
    }

    private record WarningSplit(
            List<ConfirmedWarning> confirmed,
            List<String> advisories,
            List<String> unsupported
    ) {
    }

    private CommitReviewOperationalAggregate operationalAggregate(List<Object[]> rows) {
        Map<CommitReviewIntentStatus, Long> counts = new EnumMap<>(CommitReviewIntentStatus.class);
        for (CommitReviewIntentStatus status : CommitReviewIntentStatus.values()) {
            counts.put(status, 0L);
        }
        if (rows != null) {
            for (Object[] row : rows) {
                if (row == null || row.length < 2 || !(row[0] instanceof CommitReviewIntentStatus status)) {
                    continue;
                }
                long count = row[1] instanceof Number number ? number.longValue() : 0L;
                counts.put(status, count);
            }
        }
        return new CommitReviewOperationalAggregate(
                true,
                counts.get(CommitReviewIntentStatus.PENDING),
                counts.get(CommitReviewIntentStatus.STARTING),
                counts.get(CommitReviewIntentStatus.STARTED),
                counts.get(CommitReviewIntentStatus.RUNNING),
                counts.get(CommitReviewIntentStatus.WAITING_RETRY),
                counts.get(CommitReviewIntentStatus.COMPLETED),
                counts.get(CommitReviewIntentStatus.FAILED),
                counts.get(CommitReviewIntentStatus.CANCELLED)
        );
    }

    private CommitReviewOperationalAggregate emptyOperational() {
        return new CommitReviewOperationalAggregate(true, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(jiraTimeZone.timeZone()));
    }

    private RecentCommit recentCommit(CommitData commit) {
        GitRepo repository = commit.getRepo();
        UUID projectId = repository == null || repository.getProject() == null
                ? null
                : repository.getProject().getId();
        String sha = commit.getShaHash() == null ? "" : commit.getShaHash();
        String shortSha = sha.length() <= 7 ? sha : sha.substring(0, 7);
        return new RecentCommit(
                projectId,
                repository == null ? null : repository.getRepositoryId(),
                sha,
                shortSha,
                commit.getMessage(),
                commit.getTimestamp()
        );
    }

    private void requireStudent(SagaPrincipal actor) {
        if (actor == null || actor.applicationRole() != ApplicationRole.STUDENT) {
            throw new AccessDeniedException("This tool is available only to the current Student");
        }
    }

    private void requireLecturer(SagaPrincipal actor) {
        if (actor == null || actor.applicationRole() != ApplicationRole.LECTURER) {
            throw new AccessDeniedException("This tool is available only to the current Lecturer");
        }
    }
}
