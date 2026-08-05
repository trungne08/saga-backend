package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.saga.be.dto.response.SprintListResponse;
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
        sprintOne.setStartDate(LocalDateTime.parse("2026-08-01T02:00:00"));
        sprintOne.setEndDate(LocalDateTime.parse("2026-08-15T02:00:00"));
        Sprint sprintTwo = entityWithId(new Sprint(), UUID.randomUUID());
        sprintTwo.setName("Sprint 2");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId))
                .thenReturn(List.of(sprintOne, sprintTwo));

        SprintListResponse response = service.getByProject(studentOrLecturerPrincipal(ApplicationRole.LECTURER, lecturerId), projectId);

        assertEquals(projectId, response.projectId());
        assertEquals(teamId, response.teamId());
        assertEquals(2, response.sprints().size());
        assertEquals("Sprint 1", response.sprints().get(0).sprintName());
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

        when(teamRepository.findWithCourseAndInstructorById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeamIdAndStudentId(teamId, studentId)).thenReturn(true);
        when(sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)).thenReturn(List.of(sprint));

        SprintListResponse response = service.getByTeam(studentOrLecturerPrincipal(ApplicationRole.STUDENT, studentId), teamId);

        assertEquals(projectId, response.projectId());
        assertEquals(teamId, response.teamId());
        assertEquals(1, response.sprints().size());
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
