package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.AdminAnomaliesReportResponse;
import com.saga.be.dto.response.AdminGraphProcessingReportResponse;
import com.saga.be.dto.response.AdminSystemStatsResponse;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.LecturerCourseDashboardResponses;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.AdminAnomalySignalType;
import com.saga.be.entity.enums.AdminReportSupportStatus;
import com.saga.be.entity.enums.CommitReviewIntentStatus;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

class AgentRoleAwareProjectionServiceTest {

    @Test
    void memberSelfProgressUsesCurrentActorOnlyAndDoesNotPickFirst() {
        SagaPrincipal student = student();
        UUID firstCourse = UUID.randomUUID();
        UUID secondCourse = UUID.randomUUID();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(team(course(firstCourse, "PRN231"), "A", project()), RoleInTeam.MEMBER),
                membership(team(course(secondCourse, "SWP391"), "B", project()), RoleInTeam.MEMBER)
        ));
        LecturerStudentAnalyticsQueryService analytics = mock(LecturerStudentAnalyticsQueryService.class);
        AgentRoleAwareProjectionService service = service(memberships, analytics, mock(IdentityMapRepository.class));

        InternalAgentToolResponses.SelfProgress ambiguous = service.selfProgress(student, null, null);
        assertEquals("MULTIPLE_MATCH", ambiguous.selectionState());
        verifyNoInteractions(analytics);

        when(analytics.progress(student, firstCourse, student.localProfileId())).thenReturn(
                new LecturerAnalyticsResponses.StudentProgress(
                        firstCourse, student.localProfileId(), UUID.randomUUID(), UUID.randomUUID(),
                        1, 0, 0.0, 0, java.util.Map.of(), 0
                )
        );
        InternalAgentToolResponses.SelfProgress resolved = service.selfProgress(student, firstCourse, null);
        assertEquals("SINGLE_MATCH", resolved.selectionState());
        assertEquals(firstCourse, resolved.courseId());
        assertEquals(student.localProfileId(), resolved.progress().studentId());
        verify(analytics).progress(student, firstCourse, student.localProfileId());
    }

    @Test
    void memberCannotUseLeaderTeamContext() {
        SagaPrincipal student = student();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(team(course(UUID.randomUUID(), "SE"), "Team", project()), RoleInTeam.MEMBER)
        ));
        AgentToolProjectionService projections = mock(AgentToolProjectionService.class);
        AgentRoleAwareProjectionService service = service(
                memberships, mock(LecturerStudentAnalyticsQueryService.class),
                mock(IdentityMapRepository.class), projections
        );

        InternalAgentToolResponses.LeaderTeamContext result = service.leaderTeamContext(student, null);
        assertEquals("ZERO_MATCH", result.selectionState());
        verifyNoInteractions(projections);

        InternalAgentToolResponses.LeaderTeamProgressReport report = service.leaderTeamProgressReport(student, null);
        assertEquals("ZERO_MATCH", report.selectionState());
        assertEquals("LEADER_TEAM_PROGRESS_REPORT", report.artifactType());
        assertTrue(report.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
    }

    @Test
    void leaderTeamProgressReportDoesNotPickFirstAndIncludesWarningTiers() {
        SagaPrincipal student = student();
        Team first = team(course(UUID.randomUUID(), "SE"), "Lead A", project());
        Team second = team(course(UUID.randomUUID(), "SE"), "Lead B", project());
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(first, RoleInTeam.LEADER),
                membership(second, RoleInTeam.LEADER)
        ));
        AgentToolProjectionService projections = mock(AgentToolProjectionService.class);
        AgentRoleAwareProjectionService service = service(
                memberships, mock(LecturerStudentAnalyticsQueryService.class),
                mock(IdentityMapRepository.class), projections
        );

        InternalAgentToolResponses.LeaderTeamProgressReport ambiguous =
                service.leaderTeamProgressReport(student, null);
        assertEquals("MULTIPLE_MATCH", ambiguous.selectionState());
        verifyNoInteractions(projections);

        when(projections.teamProgress(student, first.getId())).thenReturn(
                new InternalAgentToolResponses.TeamProgress(
                        first.getId(), first.getProject().getId(), 0, false, java.util.Map.of(),
                        new InternalAgentToolResponses.ContributionSnapshot(
                                first.getId(), first.getProject().getId(), null, List.of()
                        ),
                        List.of()
                )
        );
        InternalAgentToolResponses.LeaderTeamProgressReport exact =
                service.leaderTeamProgressReport(student, first.getId());
        assertEquals("SINGLE_MATCH", exact.selectionState());
        assertEquals(first.getId(), exact.teamId());
        assertEquals("LEADER_TEAM_PROGRESS_REPORT", exact.artifactType());
        assertTrue(exact.unsupportedFields().contains("finalGrade"));
        assertTrue(exact.unsupportedFields().contains("aiRiskScore"));
        assertTrue(exact.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        verify(projections).teamProgress(student, first.getId());
    }

    @Test
    void leaderExactTeamResolvesAndMultiTeamDoesNotPickFirst() {
        SagaPrincipal student = student();
        Team first = team(course(UUID.randomUUID(), "SE"), "Lead A", project());
        Team second = team(course(UUID.randomUUID(), "SE"), "Lead B", project());
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(first, RoleInTeam.LEADER),
                membership(second, RoleInTeam.LEADER)
        ));
        AgentToolProjectionService projections = mock(AgentToolProjectionService.class);
        AgentRoleAwareProjectionService service = service(
                memberships, mock(LecturerStudentAnalyticsQueryService.class),
                mock(IdentityMapRepository.class), projections
        );

        InternalAgentToolResponses.LeaderTeamContext ambiguous = service.leaderTeamContext(student, null);
        assertEquals("MULTIPLE_MATCH", ambiguous.selectionState());
        verifyNoInteractions(projections);

        when(projections.teamProgress(student, first.getId())).thenReturn(
                new InternalAgentToolResponses.TeamProgress(
                        first.getId(), first.getProject().getId(), 0, false, java.util.Map.of(),
                        new InternalAgentToolResponses.ContributionSnapshot(
                                first.getId(), first.getProject().getId(), null, List.of()
                        ),
                        List.of()
                )
        );
        InternalAgentToolResponses.LeaderTeamContext exact = service.leaderTeamContext(student, first.getId());
        assertEquals("SINGLE_MATCH", exact.selectionState());
        assertEquals(first.getId(), exact.teamId());
    }

    @Test
    void recentCommitsRequireActiveIdentityMappingAndStayBounded() {
        SagaPrincipal student = student();
        IdentityMapRepository maps = mock(IdentityMapRepository.class);
        when(maps.findByStudentIdOrderByProvider(student.localProfileId())).thenReturn(List.of());
        AgentRoleAwareProjectionService missing = service(
                mock(TeamMemberRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                maps
        );
        InternalAgentToolResponses.RecentCommits emptyMapping = missing.recentCommits(student);
        assertEquals("MISSING", emptyMapping.mappingState());
        assertEquals("ZERO_MATCH", emptyMapping.selectionState());

        IdentityMap first = IdentityMap.builder()
                .provider(IntegrationProvider.GITHUB)
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .externalAccountId("111")
                .build();
        IdentityMap second = IdentityMap.builder()
                .provider(IntegrationProvider.GITHUB)
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .externalAccountId("222")
                .build();
        when(maps.findByStudentIdOrderByProvider(student.localProfileId())).thenReturn(List.of(first, second));
        InternalAgentToolResponses.RecentCommits ambiguous = missing.recentCommits(student);
        assertEquals("AMBIGUOUS", ambiguous.mappingState());
        assertTrue(ambiguous.commits().isEmpty());

        IdentityMap inactive = IdentityMap.builder()
                .provider(IntegrationProvider.GITHUB)
                .mappingStatus(IdentityMappingStatus.DISCONNECTED)
                .externalAccountId("111")
                .build();
        when(maps.findByStudentIdOrderByProvider(student.localProfileId())).thenReturn(List.of(inactive));
        InternalAgentToolResponses.RecentCommits inactiveMapping = missing.recentCommits(student);
        assertEquals("MISSING", inactiveMapping.mappingState());
        assertTrue(inactiveMapping.commits().isEmpty());
    }

    @Test
    void recentCommitsUseCanonicalAuthorIdentityWhenMappingIsActive() {
        SagaPrincipal student = student();
        IdentityMapRepository maps = mock(IdentityMapRepository.class);
        CommitDataRepository commits = mock(CommitDataRepository.class);
        IdentityMap mapping = IdentityMap.builder()
                .provider(IntegrationProvider.GITHUB)
                .mappingStatus(IdentityMappingStatus.ACTIVE)
                .externalAccountId("99")
                .build();
        when(maps.findByStudentIdOrderByProvider(student.localProfileId())).thenReturn(List.of(mapping));
        GitRepo repo = new GitRepo();
        repo.setRepositoryId(42L);
        Project project = project();
        repo.setProject(project);
        CommitData commit = new CommitData();
        commit.setRepo(repo);
        commit.setShaHash("abcdef0123456789abcdef0123456789abcdef01");
        commit.setMessage("fix login");
        when(commits.findRecentByAuthorIdentity(
                student.localProfileId(), "99", PageRequest.of(0, 10)
        )).thenReturn(List.of(commit));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                mock(CourseRepository.class),
                maps,
                commits,
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.RecentCommits result = service.recentCommits(student);
        assertEquals("ACTIVE", result.mappingState());
        assertEquals("SINGLE_MATCH", result.selectionState());
        assertEquals(project.getId(), result.commits().get(0).projectId());
        assertEquals(42L, result.commits().get(0).providerRepositoryId());
        assertEquals("abcdef0", result.commits().get(0).shortSha());
    }

    @Test
    void lecturerReportIsExactInstructedCourseAndMultiCourseIsAmbiguity() {
        UUID lecturerId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        Course first = course(UUID.randomUUID(), "PRN231");
        Course second = course(UUID.randomUUID(), "SWP391");
        Lecturer instructor = new Lecturer();
        instructor.setId(lecturerId);
        instructor.setFullName("Lecturer");
        first.setInstructor(instructor);
        CourseRepository courses = mock(CourseRepository.class);
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of(first, second));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerProgressReport ambiguous = service.lecturerProgressReport(lecturer, null);
        assertEquals("MULTIPLE_MATCH", ambiguous.selectionState());
        assertEquals(AgentRoleAwareProjectionService.LECTURER_REPORT_VERSION, ambiguous.projectionVersion());
        assertEquals("LECTURER_PROGRESS_REPORT", ambiguous.artifactType());
        assertTrue(ambiguous.unsupportedFields().contains("finalGrade"));
        assertTrue(ambiguous.unsupportedFields().contains("aiRiskScore"));
        assertTrue(ambiguous.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        assertFalse(ambiguous.unsupportedSignals().contains("MEMBER_NO_RECENT_ACTIVITY_3D"));
        assertFalse(ambiguous.unsupportedSignals().contains("REPEATED_COMMIT_ISSUES"));

        LecturerCourseDashboardQueryService dashboard = mock(LecturerCourseDashboardQueryService.class);
        CourseEarlyWarningQueryService warnings = mock(CourseEarlyWarningQueryService.class);
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of(first));
        when(courses.findWithReportDetailsByIdAndDeletedAtIsNull(first.getId())).thenReturn(Optional.of(first));
        when(dashboard.teamsProgress(lecturer, first.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.TeamsProgress(first.getId(), List.of()));
        when(dashboard.contributionSummary(lecturer, first.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.ContributionSummary(
                        first.getId(), 0, null, 0, null, null
                ));
        when(dashboard.trends(lecturer, first.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.Trends(first.getId(), List.of()));
        when(dashboard.atRiskSummary(lecturer, first.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.AtRiskSummary(
                        first.getId(), 0, 0, 0, java.util.Map.of(), List.of()
                ));
        when(warnings.get(lecturer, first.getId()))
                .thenReturn(new LecturerAnalyticsResponses.EarlyWarnings(first.getId(), List.of()));
        AgentRoleAwareProjectionService resolvedService = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                dashboard,
                warnings,
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );
        InternalAgentToolResponses.LecturerProgressReport resolved = resolvedService.lecturerProgressReport(lecturer, first.getId());
        assertEquals("SINGLE_MATCH", resolved.selectionState());
        assertEquals(first.getId(), resolved.courseId());
        assertEquals(first.getId(), resolved.course().courseId());
        assertEquals("PRN231", resolved.course().courseCode());
        assertEquals("LECTURER_PROGRESS_REPORT", resolved.artifactType());
        assertTrue(resolved.confirmedWarnings().isEmpty());
        assertTrue(resolved.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        assertFalse(resolved.unsupportedSignals().contains("AUTO_COMMIT_REVIEW_RESULT_WARNING"));
        assertTrue(resolved.reviewAdvisories().isEmpty());
        assertEquals(true, resolved.commitReviewOperational().resultWarningIntegrationConfirmed());
    }

    @Test
    void lecturerCannotReadUninstructedCourseReport() {
        UUID lecturerId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        Course other = course(UUID.randomUUID(), "XXX");
        Lecturer someoneElse = new Lecturer();
        someoneElse.setId(UUID.randomUUID());
        other.setInstructor(someoneElse);
        CourseRepository courses = mock(CourseRepository.class);
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of());
        when(courses.findWithReportDetailsByIdAndDeletedAtIsNull(other.getId())).thenReturn(Optional.of(other));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerProgressReport zero = service.lecturerProgressReport(lecturer, other.getId());
        assertEquals("ZERO_MATCH", zero.selectionState());
    }

    @Test
    void adminReportIsAdminOnlyAndKeepsUnsupportedSignalsTbd() {
        SagaPrincipal admin = new SagaPrincipal(
                "admin-sub", "admin@example.test", "Admin",
                ApplicationRole.ADMIN, UUID.randomUUID(), null
        );
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        AdminReadService reads = mock(AdminReadService.class);
        AdminDashboardReportService reports = mock(AdminDashboardReportService.class);
        when(reads.systemStats()).thenReturn(new AdminSystemStatsResponse(1, 1, 1, 1, 0, 0, OffsetDateTime.now()));
        when(reads.courseProgressOverview(null, null, null, 0, 50)).thenReturn(new PageImpl<>(List.of()));
        when(reports.anomalies()).thenReturn(new AdminAnomaliesReportResponse(
                OffsetDateTime.now(),
                List.of(new AdminAnomaliesReportResponse.AdminAnomalySignalResponse(
                        AdminAnomalySignalType.MSR, AdminReportSupportStatus.TBD, null
                ))
        ));
        when(reports.graphProcessing()).thenReturn(new AdminGraphProcessingReportResponse(
                OffsetDateTime.now(), 7, true, null, List.of()
        ));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                mock(CourseRepository.class),
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                reads,
                reports,
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        assertThrows(AccessDeniedException.class, () -> service.adminSystemReport(lecturer));
        InternalAgentToolResponses.AdminSystemReport report = service.adminSystemReport(admin);
        assertEquals(AgentRoleAwareProjectionService.ADMIN_REPORT_VERSION, report.projectionVersion());
        assertEquals("ADMIN_SYSTEM_REPORT", report.artifactType());
        assertEquals(AdminReportSupportStatus.TBD, report.anomalies().signals().get(0).supportStatus());
        assertEquals(null, report.anomalies().signals().get(0).count());
        assertEquals(false, report.graphProcessing().historySupported());
        assertTrue(report.graphProcessing().points().isEmpty());
        assertTrue(report.unsupportedFields().contains("graphProcessingHistory"));
        assertTrue(report.unsupportedSignals().contains("MSR"));
        assertTrue(report.unsupportedSignals().contains("DEADLINE_PROCESS"));
        assertTrue(report.unsupportedSignals().contains("SNA_ISOLATION"));
        assertTrue(report.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        assertTrue(report.confirmedWarnings().isEmpty());
        assertEquals(true, report.commitReviewOperational().resultWarningIntegrationConfirmed());
    }

    @Test
    void lecturerCourseContextUsesDashboardNotIdentityPrompt() {
        UUID lecturerId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        Course course = course(UUID.randomUUID(), "PRN231");
        Lecturer instructor = new Lecturer();
        instructor.setId(lecturerId);
        course.setInstructor(instructor);
        CourseRepository courses = mock(CourseRepository.class);
        LecturerCourseDashboardQueryService dashboard = mock(LecturerCourseDashboardQueryService.class);
        CourseEarlyWarningQueryService warnings = mock(CourseEarlyWarningQueryService.class);
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of(course));
        when(courses.findWithReportDetailsByIdAndDeletedAtIsNull(course.getId())).thenReturn(Optional.of(course));
        when(dashboard.teamsProgress(lecturer, course.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.TeamsProgress(course.getId(), List.of()));
        when(dashboard.contributionSummary(lecturer, course.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.ContributionSummary(
                        course.getId(), 0, null, 0, null, null
                ));
        when(dashboard.trends(lecturer, course.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.Trends(course.getId(), List.of()));
        when(dashboard.atRiskSummary(lecturer, course.getId()))
                .thenReturn(new LecturerCourseDashboardResponses.AtRiskSummary(
                        course.getId(), 0, 0, 0, java.util.Map.of(), List.of()
                ));
        when(warnings.get(lecturer, course.getId()))
                .thenReturn(new LecturerAnalyticsResponses.EarlyWarnings(course.getId(), List.of()));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                dashboard,
                warnings,
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerCourseContext result = service.lecturerCourseContext(lecturer, null);
        assertEquals("SINGLE_MATCH", result.selectionState());
        assertEquals(course.getId(), result.courseId());
    }

    @Test
    void memberCannotReadLecturerOrAdminReports() {
        SagaPrincipal member = student();
        AgentRoleAwareProjectionService service = service(
                mock(TeamMemberRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(IdentityMapRepository.class)
        );
        assertThrows(AccessDeniedException.class, () -> service.lecturerProgressReport(member, UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> service.adminSystemReport(member));
        assertThrows(AccessDeniedException.class, () -> service.lecturerCourseContext(member, null));
    }

    @Test
    void leaderCannotReadTeamTheyDoNotLead() {
        SagaPrincipal student = student();
        Team led = team(course(UUID.randomUUID(), "SE"), "Led", project());
        Team other = team(course(UUID.randomUUID(), "SE"), "Other", project());
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(led, RoleInTeam.LEADER)
        ));
        AgentToolProjectionService projections = mock(AgentToolProjectionService.class);
        AgentRoleAwareProjectionService service = service(
                memberships, mock(LecturerStudentAnalyticsQueryService.class),
                mock(IdentityMapRepository.class), projections
        );

        InternalAgentToolResponses.LeaderTeamContext result = service.leaderTeamContext(student, other.getId());
        assertEquals("ZERO_MATCH", result.selectionState());
        verifyNoInteractions(projections);
    }

    @Test
    void memberCannotReadAnotherCourseAsSelfProgress() {
        SagaPrincipal student = student();
        UUID ownCourse = UUID.randomUUID();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(List.of(
                membership(team(course(ownCourse, "SE"), "Mine", project()), RoleInTeam.MEMBER)
        ));
        LecturerStudentAnalyticsQueryService analytics = mock(LecturerStudentAnalyticsQueryService.class);
        AgentRoleAwareProjectionService service = service(memberships, analytics, mock(IdentityMapRepository.class));

        InternalAgentToolResponses.SelfProgress result = service.selfProgress(student, UUID.randomUUID(), null);
        assertEquals("ZERO_MATCH", result.selectionState());
        verifyNoInteractions(analytics);
    }

    @Test
    void adminCourseReportRequiresCourseIdAndDoesNotPickFirst() {
        SagaPrincipal admin = new SagaPrincipal(
                "admin-sub", "admin@example.test", "Admin",
                ApplicationRole.ADMIN, UUID.randomUUID(), null
        );
        LecturerCourseDashboardQueryService dashboard = mock(LecturerCourseDashboardQueryService.class);
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                mock(CourseRepository.class),
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                dashboard,
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerProgressReport missing = service.lecturerProgressReport(admin, null);
        assertEquals("ZERO_MATCH", missing.selectionState());
        assertTrue(missing.dataLimitations().contains("COURSE_SELECTION_REQUIRED"));
        verifyNoInteractions(dashboard);
    }

    @Test
    void adminCanReadInstructedCourseReportWhenCourseAccessAllows() {
        UUID courseId = UUID.randomUUID();
        SagaPrincipal admin = new SagaPrincipal(
                "admin-sub", "admin@example.test", "Admin",
                ApplicationRole.ADMIN, UUID.randomUUID(), null
        );
        Course course = course(courseId, "PRN231");
        Lecturer instructor = new Lecturer();
        instructor.setId(UUID.randomUUID());
        instructor.setFullName("Lecturer");
        course.setInstructor(instructor);
        CourseRepository courses = mock(CourseRepository.class);
        LecturerAnalyticsAuthorizationService authorization = mock(LecturerAnalyticsAuthorizationService.class);
        LecturerCourseDashboardQueryService dashboard = mock(LecturerCourseDashboardQueryService.class);
        CourseEarlyWarningQueryService warnings = mock(CourseEarlyWarningQueryService.class);
        when(authorization.requireCourseAccess(admin, courseId)).thenReturn(course);
        when(courses.findWithReportDetailsByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(dashboard.teamsProgress(admin, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.TeamsProgress(courseId, List.of()));
        when(dashboard.contributionSummary(admin, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.ContributionSummary(
                        courseId, 0, null, 0, null, null
                ));
        when(dashboard.trends(admin, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.Trends(courseId, List.of()));
        when(dashboard.atRiskSummary(admin, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.AtRiskSummary(
                        courseId, 0, 0, 0, java.util.Map.of(), List.of()
                ));
        when(warnings.get(admin, courseId))
                .thenReturn(new LecturerAnalyticsResponses.EarlyWarnings(courseId, List.of()));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                dashboard,
                warnings,
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                authorization,
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerProgressReport report = service.lecturerProgressReport(admin, courseId);
        assertEquals("SINGLE_MATCH", report.selectionState());
        assertEquals(courseId, report.courseId());
        assertEquals("saga-lecturer-course-report-context-v1", report.projectionVersion());
    }

    @Test
    void lecturerReportIncludesSupportedDeadlineAndEarlyWarningsOnly() {
        UUID lecturerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        Course course = course(courseId, "PRN231");
        Lecturer instructor = new Lecturer();
        instructor.setId(lecturerId);
        course.setInstructor(instructor);
        CourseRepository courses = mock(CourseRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        LecturerCourseDashboardQueryService dashboard = mock(LecturerCourseDashboardQueryService.class);
        CourseEarlyWarningQueryService warnings = mock(CourseEarlyWarningQueryService.class);
        LecturerTeamAnalyticsQueryService teamAnalytics = mock(LecturerTeamAnalyticsQueryService.class);
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of(course));
        when(courses.findWithReportDetailsByIdAndDeletedAtIsNull(courseId)).thenReturn(Optional.of(course));
        when(dashboard.teamsProgress(lecturer, courseId)).thenReturn(new LecturerCourseDashboardResponses.TeamsProgress(
                courseId,
                List.of(new LecturerCourseDashboardResponses.TeamProgress(
                        teamId, "Team A", projectId, null, List.of(),
                        0, 0, 0, 0, 0, 0, null
                ))
        ));
        when(dashboard.contributionSummary(lecturer, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.ContributionSummary(
                        courseId, 1, null, 1, null, null
                ));
        when(dashboard.trends(lecturer, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.Trends(courseId, List.of()));
        when(dashboard.atRiskSummary(lecturer, courseId))
                .thenReturn(new LecturerCourseDashboardResponses.AtRiskSummary(
                        courseId, 0, 0, 0, java.util.Map.of(), List.of()
                ));
        when(warnings.get(lecturer, courseId)).thenReturn(new LecturerAnalyticsResponses.EarlyWarnings(
                courseId,
                List.of(new LecturerAnalyticsResponses.EarlyWarning(
                        UUID.randomUUID(), teamId, "OVERDUE_TASK", null,
                        LocalDateTime.now(ZoneOffset.UTC), "Nhiệm vụ đã quá hạn và chưa hoàn thành",
                        taskId, LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
                ))
        ));
        when(teamAnalytics.velocity(lecturer, courseId, teamId))
                .thenReturn(new LecturerAnalyticsResponses.SprintVelocity(courseId, teamId, List.of()));
        when(teamAnalytics.overview(org.mockito.ArgumentMatchers.eq(lecturer), org.mockito.ArgumentMatchers.eq(courseId),
                org.mockito.ArgumentMatchers.eq(teamId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LecturerAnalyticsResponses.ActivityOverview(
                        courseId, teamId, LocalDate.now(), LocalDate.now(), List.of(),
                        new LecturerAnalyticsResponses.ActivityTotals(0, 0, 0, 0, 0, 0, 0)
                ));
        Task dueToday = Task.builder()
                .title("Due today")
                .status(TaskStatus.TODO)
                .dueDate(LocalDate.now(ZoneOffset.UTC).atStartOfDay())
                .assignee(Student.builder().fullName("An").build())
                .build();
        dueToday.setId(UUID.randomUUID());
        dueToday.getAssignee().setId(UUID.randomUUID());
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(dueToday));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                courses,
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                taskRepository,
                mock(LecturerStudentAnalyticsQueryService.class),
                dashboard,
                warnings,
                teamAnalytics,
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.LecturerProgressReport report = service.lecturerProgressReport(lecturer, courseId);
        assertEquals(1, report.teamSections().size());
        assertTrue(report.confirmedWarnings().stream().anyMatch(warning -> "OVERDUE_TASK".equals(warning.signal())));
        assertTrue(report.confirmedWarnings().stream().anyMatch(warning -> "TASK_DUE_TODAY".equals(warning.signal())));
        assertFalse(report.confirmedWarnings().stream().anyMatch(warning ->
                "AUTO_COMMIT_REVIEW_RESULT_WARNING".equals(warning.signal())
        ));
        assertTrue(report.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        assertFalse(report.unsupportedSignals().contains("MEMBER_NO_RECENT_ACTIVITY_3D"));
        assertTrue(report.reviewAdvisories().isEmpty());
    }

    @Test
    void adminReportIncludesOperationalReviewCountsNotResultWarnings() {
        SagaPrincipal admin = new SagaPrincipal(
                "admin-sub", "admin@example.test", "Admin",
                ApplicationRole.ADMIN, UUID.randomUUID(), null
        );
        AdminReadService reads = mock(AdminReadService.class);
        AdminDashboardReportService reports = mock(AdminDashboardReportService.class);
        CommitReviewIntentRepository intents = mock(CommitReviewIntentRepository.class);
        when(reads.systemStats()).thenReturn(new AdminSystemStatsResponse(1, 1, 1, 1, 0, 0, OffsetDateTime.now()));
        when(reads.courseProgressOverview(null, null, null, 0, 50)).thenReturn(new PageImpl<>(List.of()));
        when(reports.anomalies()).thenReturn(new AdminAnomaliesReportResponse(
                OffsetDateTime.now(),
                List.of(new AdminAnomaliesReportResponse.AdminAnomalySignalResponse(
                        AdminAnomalySignalType.OVERDUE_TASK, AdminReportSupportStatus.SUPPORTED, 4L
                ))
        ));
        when(reports.graphProcessing()).thenReturn(new AdminGraphProcessingReportResponse(
                OffsetDateTime.now(), 7, true, null, List.of()
        ));
        when(intents.countStatusAll()).thenReturn(List.of(
                new Object[]{CommitReviewIntentStatus.COMPLETED, 3L},
                new Object[]{CommitReviewIntentStatus.FAILED, 1L}
        ));
        AgentRoleAwareProjectionService service = new AgentRoleAwareProjectionService(
                mock(AgentToolProjectionService.class),
                mock(TeamMemberRepository.class),
                mock(CourseRepository.class),
                mock(IdentityMapRepository.class),
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                mock(LecturerStudentAnalyticsQueryService.class),
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                reads,
                reports,
                mock(LecturerAnalyticsAuthorizationService.class),
                intents,
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );

        InternalAgentToolResponses.AdminSystemReport report = service.adminSystemReport(admin);
        assertEquals(3L, report.commitReviewOperational().completed());
        assertEquals(1L, report.commitReviewOperational().failed());
        assertEquals(true, report.commitReviewOperational().resultWarningIntegrationConfirmed());
        assertTrue(report.confirmedWarnings().stream().anyMatch(warning -> "OVERDUE_TASK".equals(warning.signal())));
        assertFalse(report.confirmedWarnings().stream().anyMatch(warning ->
                "AUTO_COMMIT_REVIEW_RESULT_WARNING".equals(warning.signal())
        ));
        assertTrue(report.unsupportedSignals().contains("INACTIVITY_GRACE_PERIOD=TBD_PRODUCT"));
        assertTrue(report.unsupportedSignals().contains("MSR"));
    }

    private AgentRoleAwareProjectionService service(
            TeamMemberRepository memberships,
            LecturerStudentAnalyticsQueryService analytics,
            IdentityMapRepository maps
    ) {
        return service(memberships, analytics, maps, mock(AgentToolProjectionService.class));
    }

    private AgentRoleAwareProjectionService service(
            TeamMemberRepository memberships,
            LecturerStudentAnalyticsQueryService analytics,
            IdentityMapRepository maps,
            AgentToolProjectionService projections
    ) {
        return new AgentRoleAwareProjectionService(
                projections,
                memberships,
                mock(CourseRepository.class),
                maps,
                mock(CommitDataRepository.class),
                mock(TaskRepository.class),
                analytics,
                mock(LecturerCourseDashboardQueryService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(LecturerTeamAnalyticsQueryService.class),
                mock(AdminReadService.class),
                mock(AdminDashboardReportService.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(CommitReviewIntentRepository.class),
                mock(BusinessWarningRepository.class),
                new com.saga.be.config.JiraTimeZoneProperties("UTC")
        );
    }

    private SagaPrincipal student() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT,
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                AccountStatus.ACTIVE
        );
    }

    private Course course(UUID id, String code) {
        Course value = Course.builder().courseCode(code).name(code).build();
        value.setId(id);
        return value;
    }

    private Project project() {
        Project value = Project.builder().name("P").build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Team team(Course course, String name, Project project) {
        Team value = Team.builder().course(course).name(name).project(project).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private TeamMember membership(Team team, RoleInTeam role) {
        TeamMember value = TeamMember.builder().team(team).roleInTeam(role).build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
