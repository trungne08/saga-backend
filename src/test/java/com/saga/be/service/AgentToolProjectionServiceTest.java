package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.IntegrationException;
import com.saga.be.helper.StudentIdentityNormalizer;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

class AgentToolProjectionServiceTest {

    @Test
    void studentResourceContextReturnsControlledZeroMatchWithoutInventingProject() {
        SagaPrincipal student = student();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(
                List.of(membership(team(course("SE-1", "Course 1"), "Team A", null)))
        );

        InternalAgentToolResponses.ResourceContext result = contextService(
                mock(TeamRepository.class), memberships, mock(CourseRepository.class),
                mock(TeamContributionService.class)
        ).resourceContext(student);

        assertEquals("ZERO_MATCH", result.selectionState());
        assertEquals(1, result.totalTeams());
        assertEquals(0, result.totalProjects());
        assertEquals(null, result.courses().get(0).teams().get(0).project());
    }

    @Test
    void studentResourceContextReturnsSingleAndMultipleProjectStatesDeterministically() {
        SagaPrincipal student = student();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Course firstCourse = course("SE-1", "Course 1");
        Team first = team(firstCourse, "Team A", project("Project A"));
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(
                List.of(membership(first))
        );
        AgentToolProjectionService service = contextService(
                mock(TeamRepository.class), memberships, mock(CourseRepository.class),
                mock(TeamContributionService.class)
        );

        assertEquals("SINGLE_MATCH", service.resourceContext(student).selectionState());

        Team second = team(course("SE-2", "Course 2"), "Team B", project("Project B"));
        when(memberships.findAgentContextsByStudentId(student.localProfileId())).thenReturn(
                List.of(membership(first), membership(second))
        );
        InternalAgentToolResponses.ResourceContext multiple = service.resourceContext(student);
        assertEquals("MULTIPLE_MATCH", multiple.selectionState());
        assertEquals(2, multiple.totalProjects());
        assertEquals("Project A", multiple.courses().get(0).teams().get(0).project().projectName());
    }

    @Test
    void lecturerResourceContextContainsOnlyInstructorCoursesAndTheirTeams() {
        UUID lecturerId = UUID.randomUUID();
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, lecturerId, AccountStatus.ACTIVE
        );
        CourseRepository courses = mock(CourseRepository.class);
        TeamRepository teams = mock(TeamRepository.class);
        Course first = course("SE-1", "Course 1");
        Course second = course("SE-2", "Course 2");
        when(courses.findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId))
                .thenReturn(List.of(first, second));
        when(teams.findByCourseIdOrderByNameAscIdAsc(first.getId()))
                .thenReturn(List.of(team(first, "Team A", project("Project A"))));
        when(teams.findByCourseIdOrderByNameAscIdAsc(second.getId()))
                .thenReturn(List.of(team(second, "Team B", null)));

        InternalAgentToolResponses.ResourceContext result = contextService(
                teams, mock(TeamMemberRepository.class), courses,
                mock(TeamContributionService.class)
        ).resourceContext(lecturer);

        assertEquals("MULTIPLE_MATCH", result.selectionState());
        assertEquals(2, result.totalCourses());
        assertEquals(2, result.totalTeams());
        verify(courses).findByInstructorIdAndDeletedAtIsNullOrderByCourseCodeAscIdAsc(lecturerId);
    }

    @Test
    void studentContributionFailsClosedForAnotherTeamBeforeAggregateRead() {
        SagaPrincipal student = student();
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        TeamContributionService contributions = mock(TeamContributionService.class);
        UUID otherTeamId = UUID.randomUUID();
        when(memberships.findByTeamIdAndStudentId(otherTeamId, student.localProfileId()))
                .thenReturn(Optional.empty());

        AgentToolProjectionService service = contextService(
                mock(TeamRepository.class), memberships, mock(CourseRepository.class), contributions
        );

        assertThrows(AccessDeniedException.class, () -> service.studentContribution(student, otherTeamId));
        verifyNoInteractions(contributions);
    }

    @Test
    void studentProgressIsBoundToCurrentStudentProfileWithoutInventedScore() {
        UUID projectId = UUID.randomUUID();
        SagaPrincipal student = new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        when(tasks.getTasks(
                student, projectId, null, null, student.localProfileId(), null,
                "externalKey", "asc", 0, 50
        )).thenReturn(new PageImpl<>(List.of()));
        AgentToolProjectionService service = service(
                mock(ProjectDetailService.class), tasks, mock(CommitReviewContextReader.class)
        );

        InternalAgentToolResponses.StudentProgress result = service.studentProgress(
                student, projectId
        );

        assertEquals(student.localProfileId(), result.studentId());
        assertEquals(0, result.totalAssignedTasks());
        verify(tasks).getTasks(
                student, projectId, null, null, student.localProfileId(), null,
                "externalKey", "asc", 0, 50
        );
    }

    @Test
    void lecturerCannotUseStudentPersonalProgressProjection() {
        SagaPrincipal lecturer = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        AgentToolProjectionService service = service(
                mock(ProjectDetailService.class),
                mock(ProjectTaskReadService.class),
                mock(CommitReviewContextReader.class)
        );

        assertThrows(
                AccessDeniedException.class,
                () -> service.studentProgress(lecturer, UUID.randomUUID())
        );
    }

    @Test
    void commitReviewTargetReauthorizesProjectAndUsesExactLocalCommitIdentity() {
        UUID projectId = UUID.randomUUID();
        String sha = "ABCDEF0123456789ABCDEF0123456789ABCDEF01";
        SagaPrincipal actor = new SagaPrincipal(
                "lecturer-sub", "lecturer@example.test", "Lecturer",
                ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE
        );
        ProjectDetailService projects = mock(ProjectDetailService.class);
        CommitReviewContextReader reviews = mock(CommitReviewContextReader.class);
        when(reviews.load(projectId, 42L, sha.toLowerCase())).thenReturn(
                new CommitReviewContextReader.SourceSnapshot(
                        projectId,
                        new CommitReviewContextReader.RepositorySnapshot(
                                UUID.randomUUID(), 42L, "GITHUB", "owner", "repo",
                                "owner/repo", 7L
                        ),
                        new CommitReviewContextReader.CommitSnapshot(
                                UUID.randomUUID(), sha.toLowerCase(), "message",
                                LocalDateTime.now(), 1, 1, 1
                        ),
                        List.of(),
                        false
                )
        );
        AgentToolProjectionService service = service(
                projects,
                mock(ProjectTaskReadService.class),
                reviews
        );

        InternalAgentToolResponses.CommitReviewTarget result = service.commitReviewTarget(
                actor, projectId, 42L, sha
        );

        verify(projects).get(actor, projectId);
        verify(reviews).load(projectId, 42L, sha.toLowerCase());
        assertEquals(projectId, result.projectId());
        assertEquals(42L, result.repositoryId());
        assertEquals(sha.toLowerCase(), result.commitSha());
    }

    @Test
    void resolveAssigneeMatchesExactUniqueStudentCode() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Student target = student("Le Hoang Hai", "SE123456");
        Student other = student("Nguyen Van A", "SE654321");
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(
                List.of(membership(team, target), membership(team, other))
        );
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, null, "se123456"
        );

        verify(authorization).requireProjectManager(actor, projectId);
        assertEquals("MATCHED", result.matchState());
        assertEquals(1, result.candidates().size());
        assertEquals(target.getId(), result.candidates().get(0).studentId());
    }

    @Test
    void resolveAssigneeMatchesExactUniqueFullNameWithinOwningTeamOnly() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Student target = student("Le Hoang Hai", "SE123456");
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(List.of(membership(team, target)));
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, "  le   hoang hai ", null
        );

        assertEquals("MATCHED", result.matchState());
        assertEquals(target.getId(), result.candidates().get(0).studentId());
        assertEquals("SE123456", result.candidates().get(0).studentCode());
    }

    @Test
    void resolveAssigneeReturnsControlledNotFoundWhenFullNameAndStudentCodeReferToDifferentStudents() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Student codeOwner = student("Le Hoang Hai", "SE123456");
        Student nameOwner = student("Nguyen Van A", "SE654321");
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(
                List.of(membership(team, codeOwner), membership(team, nameOwner))
        );
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, "Nguyen Van A", "SE123456"
        );

        assertEquals("NOT_FOUND", result.matchState());
        assertEquals(0, result.candidates().size());
    }

    @Test
    void resolveAssigneeMatchesWhenFullNameAndStudentCodeReferToTheSameStudent() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Student target = student("Le Hoang Hai", "SE123456");
        Student other = student("Nguyen Van A", "SE654321");
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(
                List.of(membership(team, target), membership(team, other))
        );
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, "Le Hoang Hai", "SE123456"
        );

        assertEquals("MATCHED", result.matchState());
        assertEquals(target.getId(), result.candidates().get(0).studentId());
    }

    @Test
    void resolveAssigneeReturnsMultipleMatchWithoutPickingFirstOnDuplicateFullName() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        Student first = student("Le Hoang Hai", "SE123456");
        Student second = student("Le Hoang Hai", "SE654321");
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(
                List.of(membership(team, first), membership(team, second))
        );
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, "Le Hoang Hai", null
        );

        assertEquals("MULTIPLE_MATCH", result.matchState());
        assertEquals(2, result.candidates().size());
    }

    @Test
    void resolveAssigneeReturnsNotFoundWithoutFuzzyFallback() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(memberships.findByTeamId(team.getId())).thenReturn(
                List.of(membership(team, student("Nguyen Van A", "SE654321")))
        );
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        InternalAgentToolResponses.AssigneeResolution result = service.resolveAssignee(
                actor, projectId, "Le Hoang Hai", null
        );

        assertEquals("NOT_FOUND", result.matchState());
        assertEquals(0, result.candidates().size());
    }

    @Test
    void resolveAssigneeRejectsEmptyQueryAfterReauthorization() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        Team team = team(course("SE-1", "Course 1"), "Team A", null);
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.resolveAssignee(actor, projectId, " ", null)
        );

        assertEquals("AGENT_ASSIGNEE_RESOLVE_EMPTY", failure.getCode());
        verify(authorization).requireProjectManager(actor, projectId);
        verifyNoInteractions(memberships);
    }

    @Test
    void resolveAssigneeFailsClosedWhenActorIsNotAuthorizedForProject() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorization).requireProjectManager(actor, projectId);
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        assertThrows(
                AccessDeniedException.class,
                () -> service.resolveAssignee(actor, projectId, "Le Hoang Hai", null)
        );
        verifyNoInteractions(teams, memberships);
    }

    @Test
    void resolveAssigneeReturnsControlledConflictWhenProjectHasNoTeam() {
        SagaPrincipal actor = student();
        UUID projectId = UUID.randomUUID();
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        TeamRepository teams = mock(TeamRepository.class);
        TeamMemberRepository memberships = mock(TeamMemberRepository.class);
        when(teams.findByProjectId(projectId)).thenReturn(Optional.empty());
        AgentToolProjectionService service = assigneeService(teams, memberships, authorization);

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.resolveAssignee(actor, projectId, "Le Hoang Hai", null)
        );

        assertEquals("PROJECT_TEAM_MISSING", failure.getCode());
        verifyNoInteractions(memberships);
    }

    private AgentToolProjectionService service(
            ProjectDetailService projects,
            ProjectTaskReadService tasks,
            CommitReviewContextReader reviews
    ) {
        return new AgentToolProjectionService(
                projects,
                tasks,
                mock(TeamContributionService.class),
                mock(GitHubTraceabilityService.class),
                mock(TeamRepository.class),
                mock(TeamMemberRepository.class),
                mock(CourseRepository.class),
                mock(GitRepoRepository.class),
                mock(DocumentRepository.class),
                reviews,
                mock(ProjectSprintService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(ProjectIntegrationAuthorizationService.class),
                new StudentIdentityNormalizer()
        );
    }

    private AgentToolProjectionService contextService(
            TeamRepository teams,
            TeamMemberRepository memberships,
            CourseRepository courses,
            TeamContributionService contributions
    ) {
        return new AgentToolProjectionService(
                mock(ProjectDetailService.class),
                mock(ProjectTaskReadService.class),
                contributions,
                mock(GitHubTraceabilityService.class),
                teams,
                memberships,
                courses,
                mock(GitRepoRepository.class),
                mock(DocumentRepository.class),
                mock(CommitReviewContextReader.class),
                mock(ProjectSprintService.class),
                mock(CourseEarlyWarningQueryService.class),
                mock(ProjectIntegrationAuthorizationService.class),
                new StudentIdentityNormalizer()
        );
    }

    private AgentToolProjectionService assigneeService(
            TeamRepository teams,
            TeamMemberRepository memberships,
            ProjectIntegrationAuthorizationService authorization
    ) {
        return new AgentToolProjectionService(
                mock(ProjectDetailService.class),
                mock(ProjectTaskReadService.class),
                mock(TeamContributionService.class),
                mock(GitHubTraceabilityService.class),
                teams,
                memberships,
                mock(CourseRepository.class),
                mock(GitRepoRepository.class),
                mock(DocumentRepository.class),
                mock(CommitReviewContextReader.class),
                mock(ProjectSprintService.class),
                mock(CourseEarlyWarningQueryService.class),
                authorization,
                new StudentIdentityNormalizer()
        );
    }

    private Student student(String fullName, String studentCode) {
        Student value = Student.builder().fullName(fullName).studentCode(studentCode).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private TeamMember membership(Team team, Student student) {
        TeamMember value = TeamMember.builder().team(team).student(student).roleInTeam(RoleInTeam.MEMBER).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private SagaPrincipal student() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }

    private Course course(String code, String name) {
        Course value = Course.builder().courseCode(code).name(name).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Project project(String name) {
        Project value = Project.builder().name(name).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private Team team(Course course, String name, Project project) {
        Team value = Team.builder().course(course).name(name).project(project).build();
        value.setId(UUID.randomUUID());
        return value;
    }

    private TeamMember membership(Team team) {
        TeamMember value = TeamMember.builder().team(team).roleInTeam(RoleInTeam.MEMBER).build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
