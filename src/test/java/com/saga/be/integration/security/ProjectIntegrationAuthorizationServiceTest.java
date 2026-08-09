package com.saga.be.integration.security;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectIntegrationAuthorizationServiceTest {

    private ProjectRepository projectRepository;
    private TeamRepository teamRepository;
    private TeamMemberRepository memberRepository;
    private AuthenticationAuditService auditService;
    private ProjectIntegrationAuthorizationService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        teamRepository = mock(TeamRepository.class);
        memberRepository = mock(TeamMemberRepository.class);
        auditService = mock(AuthenticationAuditService.class);
        service = new ProjectIntegrationAuthorizationService(
                projectRepository,
                teamRepository,
                memberRepository,
                auditService
        );
    }

    @Test
    void exactTeamLeaderMayManageProjectIntegration() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Project project = project(projectId);
        Team team = team(teamId, project, null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId))
                .thenReturn(Optional.of(team));
        when(memberRepository.existsByTeamIdAndStudentIdAndRoleInTeam(
                teamId,
                studentId,
                RoleInTeam.LEADER
        )).thenReturn(true);

        assertSame(
                project,
                service.requireProjectManager(
                        principal(ApplicationRole.STUDENT, studentId),
                        projectId
                )
        );
    }

    @Test
    void ordinaryTeamMemberCannotManageProjectIntegration() {
        UUID projectId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Project project = project(projectId);
        Team team = team(teamId, project, null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId))
                .thenReturn(Optional.of(team));

        assertThrows(
                IntegrationException.class,
                () -> service.requireProjectManager(
                        principal(ApplicationRole.STUDENT, studentId),
                        projectId
                )
        );
        verify(memberRepository).existsByTeamIdAndStudentIdAndRoleInTeam(
                teamId,
                studentId,
                RoleInTeam.LEADER
        );
    }

    @Test
    void assignedCourseLecturerMayReviewAndRepair() {
        UUID projectId = UUID.randomUUID();
        UUID lecturerId = UUID.randomUUID();
        Project project = project(projectId);
        Lecturer lecturer = Lecturer.builder().fullName("Lecturer").build();
        lecturer.setId(lecturerId);
        Course course = Course.builder().instructor(lecturer).build();
        Team team = team(UUID.randomUUID(), project, course);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId))
                .thenReturn(Optional.of(team));

        assertSame(
                project,
                service.requireProjectManager(
                        principal(ApplicationRole.LECTURER, lecturerId),
                        projectId
                )
        );
    }

    @Test
    void unrelatedLecturerCannotManageProjectIntegration() {
        UUID projectId = UUID.randomUUID();
        Lecturer lecturer = Lecturer.builder().fullName("Owner").build();
        lecturer.setId(UUID.randomUUID());
        Project project = project(projectId);
        Team team = team(
                UUID.randomUUID(),
                project,
                Course.builder().instructor(lecturer).build()
        );
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId))
                .thenReturn(Optional.of(team));

        assertThrows(
                IntegrationException.class,
                () -> service.requireProjectManager(
                        principal(ApplicationRole.LECTURER, UUID.randomUUID()),
                        projectId
                )
        );
    }

    @Test
    void adminMayManageAnyTeamProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        Team team = team(UUID.randomUUID(), project, null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(project));
        when(teamRepository.findByProjectId(projectId))
                .thenReturn(Optional.of(team));

        SagaPrincipal admin = principal(
                ApplicationRole.ADMIN,
                UUID.randomUUID()
        );
        assertSame(
                project,
                service.requireProjectManager(
                        admin,
                        projectId
                )
        );
        verify(auditService).recordRequiredIntegrationEvent(
                admin,
                "PROJECT_INTEGRATION_ADMIN_OVERRIDE",
                "TEAM",
                team.getId(),
                "AUTHORIZED",
                null
        );
    }

    private Project project(UUID id) {
        Project project = Project.builder().name("Project").build();
        project.setId(id);
        return project;
    }

    private Team team(UUID id, Project project, Course course) {
        Team team = Team.builder().project(project).course(course).build();
        team.setId(id);
        return team;
    }

    private SagaPrincipal principal(ApplicationRole role, UUID profileId) {
        return new SagaPrincipal(
                role + "-sub",
                role + "@example.com",
                role.name(),
                role,
                profileId,
                AccountStatus.ACTIVE
        );
    }
}
