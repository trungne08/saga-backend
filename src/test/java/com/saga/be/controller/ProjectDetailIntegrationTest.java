package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.dto.request.ProjectUpdateRequest;
import com.saga.be.entity.Course;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class ProjectDetailIntegrationTest {

    private static final String SECRET_TOKEN = "encrypted-access-token-must-not-leak";
    private static final String SECRET_WEBHOOK = "webhook-secret-must-not-leak";
    private static final String SECRET_COGNITO = "cognito-sub-must-not-leak";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private JiraBoardRepository jiraBoardRepository;

    @MockitoBean
    private JiraProviderClient jiraProviderClient;

    @MockitoBean
    private GitHubProviderClient gitHubProviderClient;

    @MockitoBean
    private AuthenticationAuditService auditService;

    @AfterEach
    void noProviderWasCalled() {
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
    }

    @Test
    void getAllowsAdminOwnerLecturerAndEveryMemberOfOwningTeamWithoutCsrf() throws Exception {
        Fixture fixture = fixture();

        getAs(fixture, ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isOk());
        getAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId())
                .andExpect(status().isOk());
        getAs(fixture, ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        getAs(fixture, ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
    }

    @Test
    void getRejectsLecturerAndStudentOutsideOwningScopeAndAnonymous() throws Exception {
        Fixture fixture = fixture();
        Lecturer otherLecturer = saveLecturer("OTHER-LECTURER");
        Student otherStudent = saveStudent("OTHER-STUDENT");
        Project otherProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course())
                .name("Other project")
                .build());
        Team otherTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course())
                .project(otherProject)
                .name("Other team")
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(otherTeam)
                .student(otherStudent)
                .roleInTeam(RoleInTeam.MEMBER)
                .build());

        getAs(fixture, ApplicationRole.LECTURER, otherLecturer.getId())
                .andExpect(status().isForbidden());
        getAs(fixture, ApplicationRole.STUDENT, otherStudent.getId())
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/projects/{projectId}", fixture.project().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReturnsSafeTeamOwnedMetadataAndNoInternalOrIntegrationFields() throws Exception {
        Fixture fixture = fixture();
        saveBoard(fixture.project());

        mockMvc.perform(get("/api/projects/{projectId}", fixture.project().getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(fixture.project().getId().toString()))
                .andExpect(jsonPath("$.name").value("Project metadata"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.team.teamId").value(fixture.team().getId().toString()))
                .andExpect(jsonPath("$.team.teamName").value("Owning team"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.repositoryUrl").doesNotExist())
                .andExpect(jsonPath("$.jiraProjectKey").doesNotExist())
                .andExpect(jsonPath("$.createdByCognitoSub").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void getMissingProjectReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/projects/{projectId}", UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void putAllowsAdminOwnerLecturerAndOwningTeamLeader() throws Exception {
        Fixture fixture = fixture();

        putAs(fixture, ApplicationRole.ADMIN, UUID.randomUUID(), "Admin name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Admin name"));
        putAs(fixture, ApplicationRole.LECTURER, fixture.instructor().getId(), "Lecturer name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lecturer name"));
        putAs(fixture, ApplicationRole.STUDENT, fixture.leader().getId(), "Leader name")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Leader name"));
    }

    @Test
    void putRejectsMemberAndActorsOutsideManagerScope() throws Exception {
        Fixture fixture = fixture();
        Lecturer otherLecturer = saveLecturer("OTHER-LECTURER");
        Student otherStudent = saveStudent("OTHER-STUDENT");
        Project otherProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course())
                .name("Other project")
                .build());
        Team otherTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course())
                .project(otherProject)
                .name("Other team")
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(otherTeam)
                .student(otherStudent)
                .roleInTeam(RoleInTeam.LEADER)
                .build());

        putAs(fixture, ApplicationRole.STUDENT, fixture.member().getId(), "Rejected member")
                .andExpect(status().isForbidden());
        putAs(fixture, ApplicationRole.LECTURER, otherLecturer.getId(), "Rejected lecturer")
                .andExpect(status().isForbidden());
        putAs(fixture, ApplicationRole.STUDENT, otherStudent.getId(), "Rejected student")
                .andExpect(status().isForbidden());
    }

    @Test
    void putRequiresAuthenticationAndValidCsrf() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anonymous\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/projects/{projectId}", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No csrf\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/{projectId}", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Invalid csrf\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void putMissingProjectAndBlankNameReturnExpectedErrors() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(put("/api/projects/{projectId}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing\"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/projects/{projectId}", fixture.project().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void putTrimsNamePreservesOwnershipAndIntegrationStateAndGetReturnsNewMetadata() throws Exception {
        Fixture fixture = fixture();
        JiraBoard board = saveBoard(fixture.project());

        putAs(fixture, ApplicationRole.STUDENT, fixture.leader().getId(), "  Trimmed project  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trimmed project"));

        Project reloadedProject = projectRepository.findById(fixture.project().getId()).orElseThrow();
        Team reloadedTeam = teamRepository.findByProjectId(fixture.project().getId()).orElseThrow();
        JiraBoard reloadedBoard = jiraBoardRepository.findById(board.getId()).orElseThrow();
        assertEquals("Trimmed project", reloadedProject.getName());
        assertEquals(fixture.team().getId(), reloadedTeam.getId());
        assertEquals(fixture.course().getId(), reloadedProject.getCourse().getId());
        assertEquals(IntegrationStatus.ACTIVE, reloadedBoard.getConnectionStatus());

        mockMvc.perform(get("/api/projects/{projectId}", fixture.project().getId())
                        .with(authentication(authenticationFor(
                                ApplicationRole.STUDENT,
                                fixture.member().getId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Trimmed project"));
    }

    @Test
    void updateRequestSupportsDescriptionWithoutActorContract() {
        List<String> componentNames = List.of(ProjectUpdateRequest.class.getRecordComponents())
                .stream()
                .map(component -> component.getName())
                .toList();

        assertEquals(List.of("name", "description"), componentNames);
        assertTrue(componentNames.contains("description"));
        assertTrue(componentNames.stream().noneMatch(name -> name.endsWith("Id")));
    }

    private org.springframework.test.web.servlet.ResultActions getAs(
            Fixture fixture,
            ApplicationRole role,
            UUID profileId
    ) throws Exception {
        return mockMvc.perform(get("/api/projects/{projectId}", fixture.project().getId())
                .with(authentication(authenticationFor(role, profileId))));
    }

    private org.springframework.test.web.servlet.ResultActions putAs(
            Fixture fixture,
            ApplicationRole role,
            UUID profileId,
            String name
    ) throws Exception {
        return mockMvc.perform(put("/api/projects/{projectId}", fixture.project().getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}")
                .with(authentication(authenticationFor(role, profileId)))
                .with(csrf()));
    }

    private Fixture fixture() {
        Lecturer instructor = saveLecturer("OWNER-LECTURER");
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor)
                .courseCode(unique("COURSE"))
                .name("Project metadata course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Project metadata")
                .createdByCognitoSub(SECRET_COGNITO)
                .repositoryUrl("https://github.example.test/secret/repo")
                .jiraProjectKey("PRIVATE")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name("Owning team")
                .build());
        Student leader = saveStudent("LEADER");
        Student member = saveStudent("MEMBER");
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(leader)
                .roleInTeam(RoleInTeam.LEADER)
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(member)
                .roleInTeam(RoleInTeam.MEMBER)
                .build());
        return new Fixture(course, project, team, instructor, leader, member);
    }

    private JiraBoard saveBoard(Project project) {
        return jiraBoardRepository.saveAndFlush(JiraBoard.builder()
                .project(project)
                .name("Private board")
                .jiraBoardId(unique("BOARD"))
                .cloudId(unique("CLOUD"))
                .jiraProjectId(unique("JIRA-PROJECT"))
                .projectKey(unique("KEY"))
                .encryptedAccessToken(SECRET_TOKEN)
                .encryptedRefreshToken("encrypted-refresh-token-must-not-leak")
                .webhookSecretHash(SECRET_WEBHOOK)
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build());
    }

    private Lecturer saveLecturer(String prefix) {
        return lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix + " Lecturer")
                .build());
    }

    private Student saveStudent(String prefix) {
        return studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(unique(prefix + "-SUB"))
                .studentCode(unique(prefix + "-CODE"))
                .email(unique(prefix.toLowerCase()) + "@example.test")
                .fullName(prefix + " Student")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-project-detail",
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

    private record Fixture(
            Course course,
            Project project,
            Team team,
            Lecturer instructor,
            Student leader,
            Student member
    ) {
    }
}
