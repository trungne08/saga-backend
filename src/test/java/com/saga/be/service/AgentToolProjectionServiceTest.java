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
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
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
                mock(CourseEarlyWarningQueryService.class)
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
                mock(CourseEarlyWarningQueryService.class)
        );
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
