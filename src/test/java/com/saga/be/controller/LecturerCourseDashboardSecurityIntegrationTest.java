package com.saga.be.controller;

import static org.hamcrest.Matchers.nullValue;
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
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class LecturerCourseDashboardSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired LecturerRepository lecturerRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired JiraBoardRepository jiraBoardRepository;
    @Autowired SprintRepository sprintRepository;
    @Autowired TaskRepository taskRepository;

    private Lecturer owner;
    private Course course;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        owner = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub("dashboard-owner-" + suffix)
                .email("dashboard-owner-" + suffix + "@example.test")
                .fullName("Dashboard Owner")
                .accountStatus(AccountStatus.ACTIVE)
                .build());
        course = courseRepository.saveAndFlush(Course.builder()
                .instructor(owner)
                .courseCode("DASH-" + suffix)
                .name("Dashboard Course")
                .build());
    }

    @Test
    void adminAndOwningLecturerCanReadEveryDashboardRoute() throws Exception {
        for (String path : paths(course.getId())) {
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                    .andExpect(status().isOk());
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.LECTURER, owner.getId()))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void otherLecturerStudentAndAnonymousCannotReadDashboardRoutes() throws Exception {
        for (String path : paths(course.getId())) {
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.LECTURER, UUID.randomUUID()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.STUDENT, UUID.randomUUID()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingCourseIsNotFoundForEveryDashboardRoute() throws Exception {
        for (String path : paths(UUID.randomUUID())) {
            mockMvc.perform(get(path).with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void unknownPopulationAndHistoricalSlicesAreSerializedAsNull() throws Exception {
        mockMvc.perform(get("/api/v1/courses/{courseId}/dashboard/contribution-summary", course.getId())
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(nullValue()))
                .andExpect(jsonPath("$.totalStudentsWithTeam").value(0))
                .andExpect(jsonPath("$.totalStudentsWithoutTeam").value(nullValue()));

        Project project = projectRepository.saveAndFlush(Project.builder()
                .course(course)
                .name("Snapshot Project")
                .build());
        teamRepository.saveAndFlush(Team.builder()
                .course(course)
                .project(project)
                .name("Snapshot Team")
                .build());
        JiraBoard board = jiraBoardRepository.saveAndFlush(JiraBoard.builder()
                .project(project)
                .name("Snapshot Board")
                .connectionStatus(IntegrationStatus.DISCONNECTED)
                .build());
        Sprint sprint = sprintRepository.saveAndFlush(Sprint.builder()
                .board(board)
                .name("Snapshot Sprint")
                .state("closed")
                .startDate(LocalDateTime.of(2026, 8, 1, 0, 0))
                .build());
        taskRepository.saveAndFlush(Task.builder()
                .project(project)
                .sprint(sprint)
                .status(TaskStatus.DONE)
                .storyPoint(5)
                .build());

        mockMvc.perform(get("/api/v1/courses/{courseId}/dashboard/trends", course.getId())
                        .with(authentication(auth(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprints[0].currentPlannedStoryPoints").value(5))
                .andExpect(jsonPath("$.sprints[0].currentCompletedStoryPoints").value(5))
                .andExpect(jsonPath("$.sprints[0].totalSlicesGenerated").value(nullValue()))
                .andExpect(jsonPath("$.sprints[0].completedStoryPoints").doesNotExist());
    }

    private Authentication auth(ApplicationRole role, UUID id) {
        SagaPrincipal principal = new SagaPrincipal(
                "subject",
                "actor@example.test",
                "Actor",
                role,
                id,
                AccountStatus.ACTIVE
        );
        return new UsernamePasswordAuthenticationToken(
                principal,
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String[] paths(UUID courseId) {
        return new String[] {
                "/api/v1/courses/%s/dashboard/teams-progress".formatted(courseId),
                "/api/v1/courses/%s/dashboard/contribution-summary".formatted(courseId),
                "/api/v1/courses/%s/dashboard/trends".formatted(courseId),
                "/api/v1/courses/%s/dashboard/at-risk-summary".formatted(courseId)
        };
    }
}
