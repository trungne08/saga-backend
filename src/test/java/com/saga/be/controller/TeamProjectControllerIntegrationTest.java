package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectType;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.ProjectTypeRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class TeamProjectControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private ProjectTypeRepository projectTypeRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @MockitoBean
    private AuthenticationAuditService auditService;

    @Test
    void createWithoutProjectTypeIdReturnsBadRequestWithSafeCode() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/teams/{teamId}/projects", fixture.team().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No type project\"}")
                        .with(authentication(leaderAuth(fixture)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PROJECT_TYPE_REQUIRED"));
    }

    @Test
    void createWithUnknownProjectTypeIdReturnsControlledNotFound() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/teams/{teamId}/projects", fixture.team().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unknown type project\",\"projectTypeId\":\"" + UUID.randomUUID() + "\"}")
                        .with(authentication(leaderAuth(fixture)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_TYPE_NOT_FOUND"));
    }

    @Test
    void createWithValidProjectTypeIdPersistsExactTypeAndReturnsSummary() throws Exception {
        Fixture fixture = fixture();
        ProjectType projectType = projectTypeRepository.save(ProjectType.builder()
                .code(unique("RESEARCH"))
                .name("Research")
                .build());

        mockMvc.perform(post("/api/teams/{teamId}/projects", fixture.team().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Typed project\",\"projectTypeId\":\"" + projectType.getId() + "\"}")
                        .with(authentication(leaderAuth(fixture)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectType.projectTypeId").value(projectType.getId().toString()))
                .andExpect(jsonPath("$.projectType.code").value(projectType.getCode()));

        Team reloadedTeam = teamRepository.findByCourseIdAndName(fixture.course().getId(), fixture.team().getName())
                .orElseThrow();
        Project reloadedProject = projectRepository.findById(reloadedTeam.getProject().getId()).orElseThrow();
        assertEquals(projectType.getId(), reloadedProject.getProjectType().getId());
    }

    @Test
    void legacyProjectWithoutProjectTypeReadsSafelyAsNull() throws Exception {
        Fixture fixture = fixture();
        Project legacyProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course())
                .name("Legacy project")
                .build());
        Team legacyTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course())
                .project(legacyProject)
                .name(unique("Legacy team"))
                .build());
        assertNull(legacyProject.getProjectType());

        mockMvc.perform(get("/api/projects/{projectId}", legacyProject.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectType").doesNotExist());
    }

    private Fixture fixture() {
        Lecturer instructor = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OWNER-SUB"))
                .email(unique("owner") + "@example.test")
                .fullName("Owner Lecturer")
                .build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor)
                .courseCode(unique("COURSE"))
                .name("Project type course")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .name(unique("Team"))
                .build());
        Student leader = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique("LEADER-SUB"))
                .studentCode(unique("LEADER-CODE"))
                .email(unique("leader") + "@example.test")
                .fullName("Leader Student")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(leader)
                .roleInTeam(RoleInTeam.LEADER)
                .build());
        return new Fixture(course, team, leader);
    }

    private Authentication leaderAuth(Fixture fixture) {
        return authenticationFor(ApplicationRole.STUDENT, fixture.leader().getId());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-team-project",
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                localProfileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record Fixture(Course course, Team team, Student leader) {
    }
}
