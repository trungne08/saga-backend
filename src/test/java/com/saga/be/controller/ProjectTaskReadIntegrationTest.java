package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.entity.value.TaskComponentSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class ProjectTaskReadIntegrationTest {

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

    @Autowired
    private SprintRepository sprintRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private JiraProviderClient jiraProviderClient;

    @AfterEach
    void noJiraProviderCallWasMade() {
        verifyNoInteractions(jiraProviderClient);
    }

    @Test
    void adminReadsLocalSnapshotDtoWithoutSecretsOrMutation() throws Exception {
        Fixture fixture = fixture();
        LocalDateTime lastSyncedAt = LocalDateTime.of(2026, 8, 1, 10, 30);
        JiraBoard board = saveBoard(fixture.project(), lastSyncedAt);
        Sprint sprint = sprintRepository.saveAndFlush(Sprint.builder()
                .board(board)
                .name("Sprint 7")
                .externalSprintId("77")
                .build());
        Student reporter = saveStudent("REPORTER");
        LocalDateTime externalUpdatedAt = LocalDateTime.of(2026, 8, 2, 9, 15);
        Task task = taskRepository.saveAndFlush(Task.builder()
                .project(fixture.project())
                .sprint(sprint)
                .assignee(fixture.member())
                .reporter(reporter)
                .externalId("jira-10001")
                .externalKey("SAGA-101")
                .title("Read local Jira snapshot")
                .type(TaskType.STORY)
                .status(TaskStatus.IN_PROGRESS)
                .priority(Priority.HIGH)
                .storyPoint(8)
                .dueDate(LocalDateTime.of(2026, 8, 10, 0, 0))
                .externalUpdatedAt(externalUpdatedAt)
                .description("Canonical snapshot description")
                .labels(List.of("Backend", "Read API"))
                .components(List.of(new TaskComponentSnapshot("10", "Backend")))
                .build());

        MvcResult result = mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/tasks/{taskId}",
                                fixture.project().getId(),
                                task.getId()
                        )
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId().toString()))
                .andExpect(jsonPath("$.projectId").value(fixture.project().getId().toString()))
                .andExpect(jsonPath("$.externalKey").value("SAGA-101"))
                .andExpect(jsonPath("$.storyPoint").value(8))
                .andExpect(jsonPath("$.description").value("Canonical snapshot description"))
                .andExpect(jsonPath("$.labels[0]").value("Backend"))
                .andExpect(jsonPath("$.components[0].id").value("10"))
                .andExpect(jsonPath("$.components[0].name").value("Backend"))
                .andExpect(jsonPath("$.sprint.id").value(sprint.getId().toString()))
                .andExpect(jsonPath("$.assignee.id").value(fixture.member().getId().toString()))
                .andExpect(jsonPath("$.reporter.id").value(reporter.getId().toString()))
                .andExpect(jsonPath("$.assignee.cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.reporter.cognitoSub").doesNotExist())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertFalse(response.contains(SECRET_TOKEN));
        assertFalse(response.contains(SECRET_WEBHOOK));
        assertFalse(response.contains(SECRET_COGNITO));
        assertEquals(externalUpdatedAt, taskRepository.findById(task.getId()).orElseThrow().getExternalUpdatedAt());
        assertEquals(lastSyncedAt, jiraBoardRepository.findById(board.getId()).orElseThrow().getLastSyncedAt());
    }

    @Test
    void lecturerOwnerAndStudentInExactTeamCanReadWithoutCsrf() throws Exception {
        Fixture fixture = fixture();
        Task task = saveTask(fixture.project(), "SAGA-201", "Authorized task", TaskStatus.TODO);

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .with(authentication(authenticationFor(
                                ApplicationRole.LECTURER,
                                fixture.instructor().getId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(task.getId().toString()));

        mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/tasks/{taskId}",
                                fixture.project().getId(),
                                task.getId()
                        )
                        .with(authentication(authenticationFor(
                                ApplicationRole.STUDENT,
                                fixture.member().getId()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(task.getId().toString()));
    }

    @Test
    void lecturerAndStudentOutsideExactProjectTeamAreForbidden() throws Exception {
        Fixture fixture = fixture();
        Lecturer otherLecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("OTHER-LECTURER-SUB"))
                .email(unique("other-lecturer") + "@example.test")
                .fullName("Other lecturer")
                .build());
        Student otherStudent = saveStudent("OTHER-STUDENT");
        Project otherProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course())
                .name("Other project")
                .build());
        Team otherTeam = teamRepository.saveAndFlush(Team.builder()
                .course(fixture.course())
                .project(otherProject)
                .name("Other team in same course")
                .build());
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(otherTeam)
                .student(otherStudent)
                .build());

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .with(authentication(authenticationFor(
                                ApplicationRole.LECTURER,
                                otherLecturer.getId()
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .with(authentication(authenticationFor(
                                ApplicationRole.STUDENT,
                                otherStudent.getId()
                        ))))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousListAndDetailAreUnauthorized() throws Exception {
        Fixture fixture = fixture();
        Task task = saveTask(fixture.project(), "SAGA-301", "Anonymous task", TaskStatus.TODO);

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/tasks/{taskId}",
                        fixture.project().getId(),
                        task.getId()
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingProjectReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTaskAndTaskFromAnotherProjectReturnNotFound() throws Exception {
        Fixture fixture = fixture();
        Project otherProject = projectRepository.saveAndFlush(Project.builder()
                .course(fixture.course())
                .name("Cross-project owner")
                .build());
        Task otherTask = saveTask(otherProject, "SAGA-401", "Cross-project task", TaskStatus.TODO);
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/tasks/{taskId}",
                                fixture.project().getId(),
                                UUID.randomUUID()
                        )
                        .with(authentication(admin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/tasks/{taskId}",
                                fixture.project().getId(),
                                otherTask.getId()
                        )
                        .with(authentication(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyListUsesDefaultPagination() throws Exception {
        Fixture fixture = fixture();

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listSupportsDeterministicPaginationFiltersAndSort() throws Exception {
        Fixture fixture = fixture();
        JiraBoard board = saveBoard(fixture.project(), null);
        Sprint sprint = sprintRepository.saveAndFlush(Sprint.builder()
                .board(board)
                .name("Filter sprint")
                .externalSprintId("filter-1")
                .build());
        Task alpha = saveTask(fixture.project(), "SAGA-501", "Alpha target", TaskStatus.TODO);
        alpha.setSprint(sprint);
        alpha.setAssignee(fixture.member());
        taskRepository.saveAndFlush(alpha);
        saveTask(fixture.project(), "SAGA-502", "Beta target", TaskStatus.DONE);
        saveTask(fixture.project(), "SAGA-503", "Gamma", TaskStatus.TODO);
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("keyword", "alpha")
                        .param("sprintId", sprint.getId().toString())
                        .param("assigneeId", fixture.member().getId().toString())
                        .param("status", "TODO")
                        .param("sortBy", "title")
                        .param("sortDirection", "desc")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(alpha.getId().toString()));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "externalKey")
                        .param("sortDirection", "desc")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].externalKey").value("SAGA-503"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("page", "-1")
                        .with(authentication(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("size", "101")
                        .with(authentication(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("status", "INVALID")
                        .with(authentication(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("sortBy", "secret")
                        .with(authentication(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/projects/{projectId}/tasks", fixture.project().getId())
                        .param("sortDirection", "sideways")
                        .with(authentication(admin)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullableSprintAssigneeAndStoryPointAreReturnedAsNull() throws Exception {
        Fixture fixture = fixture();
        Task task = saveTask(fixture.project(), "SAGA-601", "Nullable snapshot", TaskStatus.TODO);

        mockMvc.perform(get(
                                "/api/v1/projects/{projectId}/tasks/{taskId}",
                                fixture.project().getId(),
                                task.getId()
                        )
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint").value((Object) null))
                .andExpect(jsonPath("$.assignee").value((Object) null))
                .andExpect(jsonPath("$.storyPoint").value((Object) null))
                .andExpect(jsonPath("$.labels.length()").value(0))
                .andExpect(jsonPath("$.components.length()").value(0));
    }

    private Fixture fixture() {
        Lecturer instructor = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(unique("LECTURER-SUB"))
                .email(unique("lecturer") + "@example.test")
                .fullName("Owning lecturer")
                .build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .instructor(instructor)
                .courseCode(unique("COURSE"))
                .name("Task read course")
                .build());
        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Task read project")
                .createdByCognitoSub(SECRET_COGNITO)
                .build());
        Team team = teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name("Owning team")
                .build());
        Student member = saveStudent("MEMBER");
        teamMemberRepository.saveAndFlush(TeamMember.builder()
                .team(team)
                .student(member)
                .build());
        return new Fixture(course, project, team, instructor, member);
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

    private JiraBoard saveBoard(Project project, LocalDateTime lastSyncedAt) {
        return jiraBoardRepository.saveAndFlush(JiraBoard.builder()
                .project(project)
                .name("Local board")
                .jiraBoardId(unique("BOARD"))
                .cloudId(unique("CLOUD"))
                .jiraProjectId(unique("JIRA-PROJECT"))
                .projectKey(unique("KEY"))
                .encryptedAccessToken(SECRET_TOKEN)
                .encryptedRefreshToken("encrypted-refresh-token-must-not-leak")
                .webhookSecretHash(SECRET_WEBHOOK)
                .connectionStatus(IntegrationStatus.ACTIVE)
                .lastSyncedAt(lastSyncedAt)
                .build());
    }

    private Task saveTask(Project project, String externalKey, String title, TaskStatus status) {
        return taskRepository.saveAndFlush(Task.builder()
                .project(project)
                .externalId(unique("JIRA-ID"))
                .externalKey(externalKey)
                .title(title)
                .type(TaskType.TASK)
                .status(status)
                .priority(Priority.MEDIUM)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-task-read",
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
            Student member
    ) {
    }
}
