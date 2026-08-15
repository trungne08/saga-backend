package com.saga.be.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.config.IntegrationAvailability;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitHubInstallation;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.GitHubInstallationStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.sync.ManualReconciliationExecutor;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.GitHubInstallationRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SyncJobLogRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class ManualProjectSyncIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private JiraBoardRepository jiraBoardRepository;
    @Autowired private GitHubInstallationRepository installationRepository;
    @Autowired private GitRepoRepository gitRepoRepository;
    @Autowired private SyncJobLogRepository syncJobLogRepository;

    @MockitoBean private ManualReconciliationExecutor executor;
    @MockitoBean private IntegrationAvailability availability;
    @MockitoBean private AuthenticationAuditService auditService;
    @MockitoBean private JiraProviderClient jiraProviderClient;
    @MockitoBean private GitHubProviderClient gitHubProviderClient;

    @AfterEach
    void cleanUp() {
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
        syncJobLogRepository.deleteAll();
        gitRepoRepository.deleteAll();
        jiraBoardRepository.deleteAll();
        installationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        projectRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void adminLecturerOwnerAndTeamLeaderCanRequestJiraSync() throws Exception {
        Fixture fixture = fixture();
        jira(fixture.project());

        requestSync(fixture.project(), "JIRA", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets[0].targetSystem").value("JIRA"))
                .andExpect(jsonPath("$.targets[0].coalesced").value(false));
        requestSync(fixture.project(), "JIRA", ApplicationRole.LECTURER, fixture.instructor().getId())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets[0].jobId").exists())
                .andExpect(jsonPath("$.targets[0].coalesced").value(true));
        requestSync(fixture.project(), "JIRA", ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets[0].coalesced").value(true));
    }

    @Test
    void memberAndActorsOutsideProjectManagerScopeAreForbidden() throws Exception {
        Fixture fixture = fixture();
        jira(fixture.project());
        Lecturer otherLecturer = lecturer("OTHER");
        Student otherStudent = student("OTHER");
        Project otherProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course()).name("Other").build());
        Team otherTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course()).project(otherProject).name("Other team").build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(otherTeam)
                .student(otherStudent).roleInTeam(RoleInTeam.LEADER).build());

        requestSync(fixture.project(), "JIRA", ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isForbidden());
        requestSync(fixture.project(), "JIRA", ApplicationRole.LECTURER, otherLecturer.getId())
                .andExpect(status().isForbidden());
        requestSync(fixture.project(), "JIRA", ApplicationRole.STUDENT, otherStudent.getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationAndCsrfAreRequiredBeforeAnyManualDispatch() throws Exception {
        Fixture fixture = fixture();
        jira(fixture.project());

        mockMvc.perform(post("/api/projects/{projectId}/sync", fixture.project().getId())
                        .param("provider", "JIRA").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/projects/{projectId}/sync", fixture.project().getId())
                        .param("provider", "JIRA")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{projectId}/sync", fixture.project().getId())
                        .param("provider", "JIRA")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesProjectProviderAndIntegrationWithoutExposingSecrets() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(post("/api/projects/{projectId}/sync", UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/projects/{projectId}/sync", fixture.project().getId())
                        .param("provider", "INVALID")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        requestSync(fixture.project(), "ALL", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isConflict());

        JiraBoard board = jira(fixture.project());
        board.setConnectionStatus(IntegrationStatus.DISCONNECTED);
        jiraBoardRepository.saveAndFlush(board);
        requestSync(fixture.project(), "JIRA", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isConflict());
    }

    @Test
    void jiraOnlyGitHubOnlyAndAllUsePerTargetJobsWithoutPayloadLeakage() throws Exception {
        Fixture jiraFixture = fixture();
        jira(jiraFixture.project());
        requestSync(jiraFixture.project(), "ALL", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets.length()").value(1))
                .andExpect(jsonPath("$.targets[0].targetSystem").value("JIRA"));

        Fixture githubFixture = fixture();
        gitHub(githubFixture.project(), "one");
        requestSync(githubFixture.project(), "GITHUB", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets.length()").value(1))
                .andExpect(jsonPath("$.targets[0].targetSystem").value("GITHUB"));

        Fixture allFixture = fixture();
        jira(allFixture.project());
        gitHub(allFixture.project(), "one");
        gitHub(allFixture.project(), "two");
        String response = requestSync(allFixture.project(), "ALL", ApplicationRole.ADMIN, UUID.randomUUID())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.targets.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("encrypted-access-token"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("webhook-secret"));
    }

    private org.springframework.test.web.servlet.ResultActions requestSync(
            Project project, String provider, ApplicationRole role, UUID profileId
    ) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/sync", project.getId())
                .param("provider", provider)
                .with(authentication(authenticationFor(role, profileId))).with(csrf()));
    }

    private Fixture fixture() {
        Lecturer instructor = lecturer("OWNER");
        Course course = courseRepository.saveAndFlush(Course.builder().instructor(instructor)
                .courseCode(unique("COURSE")).name("Course").build());
        Project project = projectRepository.saveAndFlush(Project.builder().course(course)
                .name("Project").build());
        Team team = teamRepository.saveAndFlush(Team.builder().course(course)
                .project(project).name("Team").build());
        Student leader = student("LEADER");
        Student member = student("MEMBER");
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(leader)
                .roleInTeam(RoleInTeam.LEADER).build());
        teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(member)
                .roleInTeam(RoleInTeam.MEMBER).build());
        return new Fixture(course, project, instructor, leader, member);
    }

    private JiraBoard jira(Project project) {
        return jiraBoardRepository.saveAndFlush(JiraBoard.builder().project(project)
                .name("Board").cloudId(unique("cloud")).jiraProjectId(unique("project"))
                .jiraBoardId(unique("board")).projectKey(unique("key"))
                .encryptedAccessToken("encrypted-access-token").webhookSecretHash("webhook-secret")
                .connectionStatus(IntegrationStatus.ACTIVE).build());
    }

    private GitRepo gitHub(Project project, String suffix) {
        GitHubInstallation installation = installationRepository.saveAndFlush(GitHubInstallation.builder()
                .installationId(Math.abs(UUID.randomUUID().getLeastSignificantBits()))
                .installedByCognitoSub(unique("installer"))
                .installationStatus(GitHubInstallationStatus.ACTIVE).build());
        return gitRepoRepository.saveAndFlush(GitRepo.builder().project(project)
                .installation(installation).name("repo-" + suffix).ownerLogin("owner")
                .fullName("owner/repo-" + suffix).repositoryId(Math.abs(UUID.randomUUID().getLeastSignificantBits()))
                .connectionStatus(IntegrationStatus.ACTIVE).build());
    }

    private Lecturer lecturer(String prefix) {
        return lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub(unique(prefix + "-sub"))
                .email(unique(prefix) + "@test").fullName(prefix).build());
    }

    private Student student(String prefix) {
        return studentRepository.saveAndFlush(Student.builder().cognitoSub(unique(prefix + "-sub"))
                .studentCode(unique(prefix + "-code")).email(unique(prefix) + "@test")
                .fullName(prefix).accountStatus(AccountStatus.ACTIVE).build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID profileId) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase(), role.name() + "@test",
                role.name(), role, profileId, AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String unique(String value) { return value + "-" + UUID.randomUUID(); }

    private record Fixture(Course course, Project project, Lecturer instructor, Student leader, Student member) { }
}
