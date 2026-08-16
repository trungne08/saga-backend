package com.saga.be.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class AdminDashboardReportIntegrationTest {

    private static final String ANOMALIES_PATH = "/api/admin/reports/anomalies";
    private static final String GRAPH_PROCESSING_PATH = "/api/admin/reports/graph-processing";

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @MockitoBean private JiraProviderClient jiraProviderClient;
    @MockitoBean private GitHubProviderClient gitHubProviderClient;

    @AfterEach
    void verifyProviderIsolation() {
        verifyNoInteractions(jiraProviderClient, gitHubProviderClient);
    }

    @Test
    void anomaliesRequiresAdminSession() throws Exception {
        mockMvc.perform(get(ANOMALIES_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ANOMALIES_PATH).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ANOMALIES_PATH).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ANOMALIES_PATH).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void anomaliesCountsOnlyAuthoritativeOverdueTaskAndLeavesUnsupportedSignalsNull() throws Exception {
        Fixture fixture = seedProjectWithMember();
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);

        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(fixture.member())
                .title("overdue-open")
                .status(TaskStatus.IN_PROGRESS)
                .dueDate(nowUtc.minusDays(1))
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(fixture.member())
                .title("overdue-done")
                .status(TaskStatus.DONE)
                .dueDate(nowUtc.minusDays(2))
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(fixture.member())
                .title("future-open")
                .status(TaskStatus.TODO)
                .dueDate(nowUtc.plusDays(2))
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(fixture.member())
                .title("null-due")
                .status(TaskStatus.IN_PROGRESS)
                .dueDate(null)
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(null)
                .title("unassigned-overdue")
                .status(TaskStatus.IN_PROGRESS)
                .dueDate(nowUtc.minusDays(3))
                .build());
        Student outsider = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub("outsider-sub")
                .studentCode("OUT-01")
                .email("outsider@example.test")
                .fullName("Outsider")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(outsider)
                .title("outsider-overdue")
                .status(TaskStatus.IN_PROGRESS)
                .dueDate(nowUtc.minusDays(1))
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .assignee(fixture.member())
                .title("overdue-null-status")
                .status(null)
                .dueDate(nowUtc.minusHours(2))
                .build());

        mockMvc.perform(get(ANOMALIES_PATH).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.signals", hasSize(4)))
                .andExpect(jsonPath("$.signals[0].type").value("OVERDUE_TASK"))
                .andExpect(jsonPath("$.signals[0].supportStatus").value("SUPPORTED"))
                .andExpect(jsonPath("$.signals[0].count").value(2))
                .andExpect(jsonPath("$.signals[1].type").value("MSR"))
                .andExpect(jsonPath("$.signals[1].supportStatus").value("TBD"))
                .andExpect(jsonPath("$.signals[1].count").value(nullValue()))
                .andExpect(jsonPath("$.signals[2].type").value("DEADLINE_PROCESS"))
                .andExpect(jsonPath("$.signals[2].supportStatus").value("TBD"))
                .andExpect(jsonPath("$.signals[2].count").value(nullValue()))
                .andExpect(jsonPath("$.signals[3].type").value("SNA_ISOLATION"))
                .andExpect(jsonPath("$.signals[3].supportStatus").value("TBD"))
                .andExpect(jsonPath("$.signals[3].count").value(nullValue()));
    }

    @Test
    void graphProcessingRequiresAdminAndReturnsPersistedHistoryContract() throws Exception {
        mockMvc.perform(get(GRAPH_PROCESSING_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(GRAPH_PROCESSING_PATH).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(GRAPH_PROCESSING_PATH).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(GRAPH_PROCESSING_PATH).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.historySupported").value(true))
                .andExpect(jsonPath("$.coverageStart").value(nullValue()))
                .andExpect(jsonPath("$.points", hasSize(0)));
    }

    private Fixture seedProjectWithMember() {
        Course course = courseRepository.saveAndFlush(Course.builder()
                .courseCode("ADM-REP")
                .name("Admin Report Course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Admin Report Project")
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name("Admin Report Team")
                .build());
        Student member = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub("member-sub")
                .studentCode("SE-01")
                .email("member@example.test")
                .fullName("Member")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(member)
                .roleInTeam(RoleInTeam.MEMBER)
                .build());
        return new Fixture(project, member);
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-sub",
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                UUID.randomUUID(),
                null
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private record Fixture(Project project, Student member) {
    }
}
