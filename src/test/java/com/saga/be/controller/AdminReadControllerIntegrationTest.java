package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Course;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.SystemAuditLog;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SystemAuditLogRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
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
class AdminReadControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminRepository adminRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private JiraBoardRepository jiraBoardRepository;
    @Autowired private GitRepoRepository gitRepoRepository;
    @MockitoBean private SystemAuditLogRepository systemAuditLogRepository;
    @MockitoBean private JiraProviderClient jiraProviderClient;
    @MockitoBean private GitHubProviderClient gitHubProviderClient;

    @BeforeEach
    void defaultAuditPage() {
        when(systemAuditLogRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @AfterEach
    void cleanupAndVerifyProviderIsolation() {
        gitRepoRepository.deleteAll();
        jiraBoardRepository.deleteAll();
        teamRepository.deleteAll();
        projectRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
        lecturerRepository.deleteAll();
        adminRepository.deleteAll();
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
    }

    @Test
    void allAdminReadsRequireAdminSession() throws Exception {
        List<String> paths = List.of("/api/admin/users", "/api/admin/audit-logs", "/api/admin/system-stats",
                "/api/admin/teams", "/api/admin/projects");
        for (String path : paths) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            mockMvc.perform(get(path).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void usersAreDatabasePagedFilteredAndSanitized() throws Exception {
        Admin admin = adminRepository.saveAndFlush(Admin.builder().cognitoSub("secret-admin-sub")
                .email("admin@example.test").fullName("Admin Alpha").build());
        lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub("secret-lecturer-sub")
                .email("lecturer@example.test").fullName("Lecturer Bravo").build());
        Student student = studentRepository.saveAndFlush(Student.builder().cognitoSub("secret-student-sub")
                .studentCode("SE-01").email("student@example.test").fullName("Student Alpha")
                .accountStatus(AccountStatus.ACTIVE).build());

        String response = mockMvc.perform(get("/api/admin/users").param("keyword", "alpha")
                        .param("page", "0").param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].localProfileId").value(admin.getId().toString()))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("secret-admin-sub"));
        assertFalse(response.contains("secret-lecturer-sub"));
        assertFalse(response.contains("secret-student-sub"));

        mockMvc.perform(get("/api/admin/users").param("role", "STUDENT").param("accountStatus", "ACTIVE")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].localProfileId").value(student.getId().toString()))
                .andExpect(jsonPath("$.content[0].studentCode").value("SE-01"));
        mockMvc.perform(get("/api/admin/users").param("size", "101")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditLogsAreNewestFirstAndDoNotExposeRawOrSensitiveFields() throws Exception {
        SystemAuditLog newer = audit("new", LocalDateTime.of(2026, 8, 9, 10, 0));
        SystemAuditLog older = audit("old", LocalDateTime.of(2026, 8, 8, 10, 0));
        when(systemAuditLogRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(newer, older)));

        String response = mockMvc.perform(get("/api/admin/audit-logs")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value("new"))
                .andExpect(jsonPath("$.content[0].action").value("LOGIN"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("raw-old-payload"));
        assertFalse(response.contains("raw-new-payload"));
        assertFalse(response.contains("secret-actor"));
        assertFalse(response.contains("192.0.2.10"));
        verify(systemAuditLogRepository).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void statsTeamsAndProjectsUseOnlyLocalSafeSnapshots() throws Exception {
        adminRepository.saveAndFlush(Admin.builder().cognitoSub("stats-admin-sub").email("stats-admin@test")
                .fullName("Stats Admin").build());
        Lecturer lecturer = lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub("stats-lecturer-sub")
                .email("stats-lecturer@test").fullName("Stats Lecturer").build());
        Student student = studentRepository.saveAndFlush(Student.builder().cognitoSub("stats-student-sub")
                .studentCode("STATS-01").email("stats-student@test").fullName("Stats Student")
                .accountStatus(AccountStatus.ACTIVE).build());
        Course course = courseRepository.saveAndFlush(Course.builder().instructor(lecturer).courseCode("STATS")
                .name("Stats Course").build());
        Project project = projectRepository.saveAndFlush(Project.builder().course(course).name("Stats Project")
                .description("Safe description").repositoryUrl("https://secret.example/repo")
                .createdByCognitoSub("secret-project-owner").build());
        Team team = teamRepository.saveAndFlush(Team.builder().course(course).project(project).name("Stats Team").build());
        jiraBoardRepository.saveAndFlush(JiraBoard.builder().project(project).connectionStatus(IntegrationStatus.ACTIVE)
                .encryptedAccessToken("never-return").encryptedRefreshToken("never-return").build());
        gitRepoRepository.saveAndFlush(GitRepo.builder().project(project).name("repo").fullName("private/repo")
                .url("https://secret.example/repo").connectionStatus(IntegrationStatus.ACTIVE).build());

        mockMvc.perform(get("/api/admin/system-stats").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProfiles").value(3))
                .andExpect(jsonPath("$.totalCourses").value(1)).andExpect(jsonPath("$.totalTeams").value(1))
                .andExpect(jsonPath("$.totalProjects").value(1)).andExpect(jsonPath("$.activeJiraBoards").value(1))
                .andExpect(jsonPath("$.activeGitRepositories").value(1)).andExpect(jsonPath("$.generatedAt").exists());

        mockMvc.perform(get("/api/admin/teams").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(team.getId().toString()))
                .andExpect(jsonPath("$.content[0].course.courseCode").value("STATS"))
                .andExpect(jsonPath("$.content[0].project.id").value(project.getId().toString()));
        String response = mockMvc.perform(get("/api/admin/projects").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(project.getId().toString()))
                .andExpect(jsonPath("$.content[0].jira.connectionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].gitHub.repositoryCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("never-return"));
        assertFalse(response.contains("secret-project-owner"));
        assertFalse(response.contains("https://secret.example/repo"));
        assertFalse(response.contains(student.getCognitoSub()));
    }

    private SystemAuditLog audit(String id, LocalDateTime timestamp) {
        SystemAuditLog log = new SystemAuditLog();
        log.setId(id);
        log.setAction("LOGIN");
        log.setTargetEntity("SESSION");
        log.setTimestamp(timestamp);
        log.setActorId("secret-actor");
        log.setIpAddress("192.0.2.10");
        log.setOldValues("raw-old-payload");
        log.setNewValues("raw-new-payload");
        return log;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-sub", role.name().toLowerCase()
                + "@example.test", role.name() + " User", role, UUID.randomUUID(), null);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
