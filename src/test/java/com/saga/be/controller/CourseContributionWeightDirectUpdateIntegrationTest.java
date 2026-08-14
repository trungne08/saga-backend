package com.saga.be.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.contribution.ContributionSliceWeightResolver;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class CourseContributionWeightDirectUpdateIntegrationTest {

    private static final String WEIGHTS_PATH = "/api/v1/courses/{courseId}/contribution-slice-weights";
    private static final String EVALUATION_PATH = "/api/v1/teams/{teamId}/contribution-evaluation";

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private ProjectGroupWeightConfigRepository groupWeightConfigRepository;
    @Autowired private ContributionSliceWeightResolver sliceWeightResolver;

    @Test
    void lecturerOwnerCanDirectlyUpdateCourseWeightsAndGetReturnsThem() throws Exception {
        Fixture fixture = fixture();

        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "30", "20", "50", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(30.0))
                .andExpect(jsonPath("$.documentWeight").value(20.0))
                .andExpect(jsonPath("$.designWeight").value(50.0));

        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(30.0))
                .andExpect(jsonPath("$.documentWeight").value(20.0))
                .andExpect(jsonPath("$.designWeight").value(50.0));
        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk());
    }

    @Test
    void lecturerOtherCourseStudentAdminAndAnonymousCannotDirectlyMutate() throws Exception {
        Fixture fixture = fixture();

        update(fixture, ApplicationRole.LECTURER, fixture.otherLecturer().getId(), fixture.course().getId(),
                "30", "20", "50", true)
                .andExpect(status().isForbidden());
        update(fixture, ApplicationRole.STUDENT, fixture.leader().getId(), fixture.course().getId(),
                "30", "20", "50", true)
                .andExpect(status().isForbidden());
        update(fixture, ApplicationRole.ADMIN, UUID.randomUUID(), fixture.course().getId(),
                "30", "20", "50", true)
                .andExpect(status().isForbidden());
        mockMvc.perform(put(WEIGHTS_PATH, fixture.course().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("30", "20", "50"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "30", "20", "50", false)
                .andExpect(status().isForbidden());
    }

    @Test
    void lecturerCannotReadAnotherCourseAndStudentCannotGetWeights() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.otherLecturer().getId()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, fixture.leader().getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidNegativeAndInvalidTotalAreRejected() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "-10", "50", "60", true)
                .andExpect(status().isBadRequest());
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "40", "10", true)
                .andExpect(status().isBadRequest());
    }

    @Test
    void courseFallbackUsesUpdatedWeightsWhileExactGroupOverrideWinsAndDoesNotLeak() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "30", "20", "50", true)
                .andExpect(status().isOk());

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        fixture.courseTeam().setCourse(reloaded);
        fixture.overrideTeam().setCourse(reloaded);

        var fallback = sliceWeightResolver.resolve(fixture.courseProject().getId(), fixture.courseTeam());
        assertThat(fallback.code()).isEqualByComparingTo("30");
        assertThat(fallback.document()).isEqualByComparingTo("20");
        assertThat(fallback.design()).isEqualByComparingTo("50");

        mockMvc.perform(put("/api/projects/{projectId}/group-weights", fixture.overrideProject().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId": "%s",
                                  "codeWeight": 0.4,
                                  "documentWeight": 0.3,
                                  "designWeight": 0.3
                                }
                                """.formatted(fixture.overrideTeam().getId()))
                        .with(csrf())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk());

        var override = sliceWeightResolver.resolve(fixture.overrideProject().getId(), fixture.overrideTeam());
        assertThat(override.code()).isEqualByComparingTo("40");
        assertThat(override.document()).isEqualByComparingTo("30");
        assertThat(override.design()).isEqualByComparingTo("30");

        var noCrossTeamLeak = sliceWeightResolver.resolve(fixture.overrideProject().getId(), fixture.courseTeam());
        assertThat(noCrossTeamLeak.code()).isEqualByComparingTo("30");

        mockMvc.perform(get(EVALUATION_PATH, fixture.courseTeam().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(fixture.courseTeam().getId().toString()));
        mockMvc.perform(get(EVALUATION_PATH, fixture.overrideTeam().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk());

        assertThat(groupWeightConfigRepository.findByProjectId(fixture.courseProject().getId())).isEmpty();
    }

    private ResultActions update(
            Fixture fixture,
            ApplicationRole role,
            UUID actorId,
            UUID courseId,
            String code,
            String document,
            String design,
            boolean includeCsrf
    ) throws Exception {
        var request = put(WEIGHTS_PATH, courseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, document, design))
                .with(authentication(authenticationFor(role, actorId)));
        if (includeCsrf) {
            request = request.with(csrf());
        }
        return mockMvc.perform(request);
    }

    private String body(String code, String document, String design) {
        return """
                {
                  "codeWeight": %s,
                  "documentWeight": %s,
                  "designWeight": %s
                }
                """.formatted(code, document, design);
    }

    private Fixture fixture() {
        Lecturer owner = lecturer("owner");
        Lecturer otherLecturer = lecturer("other");
        Course course = courseRepository.save(Course.builder()
                .courseCode("WEIGHT-" + UUID.randomUUID())
                .name("Weight Course")
                .instructor(owner)
                .codeContributionWeight(100.0 / 3.0)
                .documentContributionWeight(100.0 / 3.0)
                .designContributionWeight(100.0 / 3.0)
                .build());
        TeamGraph courseTeam = teamGraph(course, "fallback");
        TeamGraph overrideTeam = teamGraph(course, "override");
        Student leader = student("leader");
        membership(courseTeam.team(), leader, RoleInTeam.LEADER);
        membership(overrideTeam.team(), student("override-leader"), RoleInTeam.LEADER);
        return new Fixture(owner, otherLecturer, course, courseTeam.project(), courseTeam.team(),
                overrideTeam.project(), overrideTeam.team(), leader);
    }

    private TeamGraph teamGraph(Course course, String label) {
        Project project = projectRepository.save(Project.builder()
                .course(course)
                .name("Project " + label + " " + UUID.randomUUID())
                .build());
        Team team = teamRepository.save(Team.builder()
                .course(course)
                .project(project)
                .name("Team " + label + " " + UUID.randomUUID())
                .build());
        return new TeamGraph(project, team);
    }

    private Lecturer lecturer(String label) {
        String suffix = UUID.randomUUID().toString();
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + label + "-" + suffix)
                .email("lecturer-" + label + "-" + suffix + "@example.test")
                .fullName("Lecturer " + label)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Student student(String label) {
        String suffix = UUID.randomUUID().toString();
        return studentRepository.save(Student.builder()
                .cognitoSub("student-" + label + "-" + suffix)
                .email("student-" + label + "-" + suffix + "@example.test")
                .studentCode("SE" + suffix.substring(0, 6).toUpperCase())
                .fullName("Student " + label)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private void membership(Team team, Student student, RoleInTeam role) {
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .student(student)
                .roleInTeam(role)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID profileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject-" + UUID.randomUUID(),
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                profileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private record TeamGraph(Project project, Team team) {}

    private record Fixture(
            Lecturer owner,
            Lecturer otherLecturer,
            Course course,
            Project courseProject,
            Team courseTeam,
            Project overrideProject,
            Team overrideTeam,
            Student leader
    ) {}
}
