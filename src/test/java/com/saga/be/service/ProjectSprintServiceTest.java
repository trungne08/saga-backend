package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.SprintListState;
import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.Lecturer;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.entity.enums.AccountStatus;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProjectSprintServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private SprintRepository sprintRepository;

    private ProjectSprintService service;

    @BeforeEach
    void setUp() {
        service = new ProjectSprintService(
                projectRepository,
                teamRepository,
                teamMemberRepository,
                sprintRepository
        );
    }

    @Test
    void listsProjectSprintsForLecturerAndReturnsTeamId() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();

        Lecturer lecturer = entityWithId(new Lecturer(), lecturerId);
        Course course = entityWithId(new Course(), UUID.randomUUID());
        course.setInstructor(lecturer);

        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);

        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        Sprint sprintOne = entityWithId(new Sprint(), UUID.randomUUID());
        sprintOne.setName("Sprint 1");
        sprintOne.setState("future");
        sprintOne.setStartDate(LocalDateTime.parse("2026-08-01T02:00:00"));
        sprintOne.setEndDate(LocalDateTime.parse("2026-08-15T02:00:00"));
        Sprint sprintTwo = entityWithId(new Sprint(), UUID.randomUUID());
        sprintTwo.setName("Sprint 2");
        sprintTwo.setState("active");
        Sprint sprintThree = entityWithId(new Sprint(), UUID.randomUUID());
        sprintThree.setName("Sprint 3");
        sprintThree.setState("closed");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId))
                .thenReturn(List.of(sprintOne, sprintTwo, sprintThree));

        SprintListResponse response = service.getByProject(studentOrLecturerPrincipal(ApplicationRole.LECTURER, lecturerId), projectId);

        assertEquals(projectId, response.projectId());
        assertEquals(teamId, response.teamId());
        assertEquals(SprintListState.READY, response.state());
        assertEquals(3, response.sprints().size());
        assertEquals("Sprint 1", response.sprints().get(0).sprintName());
        assertEquals("future", response.sprints().get(0).state());
        assertEquals("active", response.sprints().get(1).state());
        assertEquals("closed", response.sprints().get(2).state());
        assertEquals(
                LocalDateTime.parse("2026-08-01T02:00:00"),
                response.sprints().get(0).startDate()
        );
        assertEquals(
                LocalDateTime.parse("2026-08-15T02:00:00"),
                response.sprints().get(0).endDate()
        );
    }

    @Test
    void listsTeamSprintsForStudentMember() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        Course course = entityWithId(new Course(), UUID.randomUUID());
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);

        Team team = entityWithId(new Team(), teamId);
        team.setCourse(course);
        team.setProject(project);

        TeamMember membership = entityWithId(new TeamMember(), UUID.randomUUID());
        membership.setTeam(team);
        membership.setStudent(entityWithId(new Student(), studentId));

        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setName("Sprint 1");
        sprint.setState("closed");

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndStudentId(teamId, studentId)).thenReturn(true);
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));

        SprintListResponse response = service.getByTeam(studentOrLecturerPrincipal(ApplicationRole.STUDENT, studentId), teamId);

        assertEquals(projectId, response.projectId());
        assertEquals(teamId, response.teamId());
        assertEquals(SprintListState.READY, response.state());
        assertEquals(1, response.sprints().size());
        assertEquals("closed", response.sprints().get(0).state());
    }

    @Test
    void reflectsCanonicalSprintStateChangesInProjectList() {
        UUID projectId = UUID.randomUUID();
        Project project = entityWithId(new Project(), projectId);
        Team team = entityWithId(new Team(), UUID.randomUUID());
        team.setProject(project);
        Sprint sprint = entityWithId(new Sprint(), UUID.randomUUID());
        sprint.setState("future");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId))
                .thenReturn(List.of(sprint));
        SagaPrincipal admin = studentOrLecturerPrincipal(ApplicationRole.ADMIN, UUID.randomUUID());

        assertEquals("future", service.getByProject(admin, projectId).sprints().get(0).state());
        sprint.setState("active");
        assertEquals("active", service.getByProject(admin, projectId).sprints().get(0).state());
        sprint.setState("closed");
        assertEquals("closed", service.getByProject(admin, projectId).sprints().get(0).state());
    }

    @Test
    void returnsProjectNotCreatedAfterAuthorizationForAccessibleTeam() {
        UUID teamId = UUID.randomUUID();
        Team team = entityWithId(new Team(), teamId);

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));

        SprintListResponse response = service.getByTeam(
                studentOrLecturerPrincipal(ApplicationRole.ADMIN, UUID.randomUUID()), teamId);

        assertEquals(null, response.projectId());
        assertEquals(teamId, response.teamId());
        assertEquals(SprintListState.PROJECT_NOT_CREATED, response.state());
        assertEquals(List.of(), response.sprints());
        verifyNoInteractions(sprintRepository);
    }

    @Test
    void deniesUnauthorizedActorBeforeReturningProjectNotCreated() {
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Team team = entityWithId(new Team(), teamId);
        team.setCourse(entityWithId(new Course(), UUID.randomUUID()));

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndStudentId(teamId, studentId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getByTeam(
                studentOrLecturerPrincipal(ApplicationRole.STUDENT, studentId), teamId));

        verifyNoInteractions(sprintRepository);
    }

    @Test
    void returnsEmptyStateWhenProjectHasNoSprints() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        Team team = entityWithId(new Team(), teamId);
        team.setProject(entityWithId(new Project(), projectId));

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of());

        SprintListResponse response = service.getByTeam(
                studentOrLecturerPrincipal(ApplicationRole.ADMIN, UUID.randomUUID()), teamId);

        assertEquals(SprintListState.EMPTY, response.state());
        assertEquals(List.of(), response.sprints());
    }

    @Test
    void marksMissingTeamWithStableMachineReadableReason() {
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getByTeam(
                studentOrLecturerPrincipal(ApplicationRole.ADMIN, UUID.randomUUID()), teamId));

        assertEquals(404, exception.getStatusCode().value());
        assertEquals("TEAM_NOT_FOUND", exception.getReason());
    }

    @Test
    void rejectsLecturerWhoOwnsAnotherCourse() {
        UUID projectId = UUID.randomUUID();
        Lecturer owningLecturer = entityWithId(new Lecturer(), UUID.randomUUID());
        Lecturer otherLecturer = entityWithId(new Lecturer(), UUID.randomUUID());
        Course projectCourse = entityWithId(new Course(), UUID.randomUUID());
        projectCourse.setInstructor(owningLecturer);
        Course otherCourse = entityWithId(new Course(), UUID.randomUUID());
        otherCourse.setInstructor(otherLecturer);
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(projectCourse);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThrows(
                AccessDeniedException.class,
                () -> service.getByProject(
                        studentOrLecturerPrincipal(ApplicationRole.LECTURER, otherLecturer.getId()),
                        projectId
                )
        );
    }

    @Test
    void rejectsStudentWhoseMembershipIsOnlyInAnotherTeamOfTheCourse() {
        UUID projectId = UUID.randomUUID();
        UUID owningTeamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Course course = entityWithId(new Course(), UUID.randomUUID());
        Project project = entityWithId(new Project(), projectId);
        project.setCourse(course);
        Team owningTeam = entityWithId(new Team(), owningTeamId);
        owningTeam.setCourse(course);
        owningTeam.setProject(project);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(owningTeam));
        when(teamMemberRepository.existsByTeamIdAndStudentId(owningTeamId, studentId)).thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> service.getByProject(
                        studentOrLecturerPrincipal(ApplicationRole.STUDENT, studentId),
                        projectId
                )
        );
    }

    private SagaPrincipal studentOrLecturerPrincipal(ApplicationRole role, UUID profileId) {
        return new SagaPrincipal(
                "sub",
                "user@saga.test",
                "User",
                role,
                profileId,
                AccountStatus.ACTIVE
        );
    }

    private <T extends com.saga.be.entity.BaseEntity> T entityWithId(T entity, UUID id) {
        entity.setId(id);
        return entity;
    }
}
