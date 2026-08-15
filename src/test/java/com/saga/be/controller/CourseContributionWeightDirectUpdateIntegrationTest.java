package com.saga.be.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.ContributionConfigMode;
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
import java.math.BigDecimal;
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

/**
 * Contribution weight authority has exactly one active mode per Course — COURSE (every Team
 * shares the Course weights) or TEAM (every current Team must have its own exact Project+Team
 * override; no partial/mixed activation, no silent fallback to Course). Criteria are
 * Code/Test/Document/Research (DESIGN retired). {@code ProjectGroupWeightConfig} historical rows
 * are retained even when inactive.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class CourseContributionWeightDirectUpdateIntegrationTest {

    private static final String WEIGHTS_PATH = "/api/v1/courses/{courseId}/contribution-slice-weights";
    private static final String MODE_PATH = "/api/v1/courses/{courseId}/contribution-config-mode";
    private static final String TEAM_WEIGHTS_PATH = "/api/v1/courses/{courseId}/contribution-team-weights";
    private static final String GROUP_WEIGHTS_PATH = "/api/projects/{projectId}/group-weights";
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
                "30", "10", "20", "40", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(30.0))
                .andExpect(jsonPath("$.testWeight").value(10.0))
                .andExpect(jsonPath("$.documentWeight").value(20.0))
                .andExpect(jsonPath("$.researchWeight").value(40.0));

        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeWeight").value(30.0))
                .andExpect(jsonPath("$.testWeight").value(10.0))
                .andExpect(jsonPath("$.documentWeight").value(20.0))
                .andExpect(jsonPath("$.researchWeight").value(40.0));
        mockMvc.perform(get(WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk());
    }

    @Test
    void lecturerOtherCourseStudentAdminAndAnonymousCannotDirectlyMutate() throws Exception {
        Fixture fixture = fixture();

        update(fixture, ApplicationRole.LECTURER, fixture.otherLecturer().getId(), fixture.course().getId(),
                "30", "10", "20", "40", true)
                .andExpect(status().isForbidden());
        update(fixture, ApplicationRole.STUDENT, fixture.leader().getId(), fixture.course().getId(),
                "30", "10", "20", "40", true)
                .andExpect(status().isForbidden());
        update(fixture, ApplicationRole.ADMIN, UUID.randomUUID(), fixture.course().getId(),
                "30", "10", "20", "40", true)
                .andExpect(status().isForbidden());
        mockMvc.perform(put(WEIGHTS_PATH, fixture.course().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("30", "10", "20", "40"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "30", "10", "20", "40", false)
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyWeightRequestAndAdminDecisionRoutesNoLongerExist() throws Exception {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/courses/{courseId}/contribution-slice-weight-requests", fixture.course().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codeWeight":50,"documentWeight":30,"designWeight":20,"reason":"x","lecturerId":"%s"}
                                """.formatted(fixture.owner().getId()))
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId())))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/courses/contribution-slice-weight-requests")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/courses/contribution-slice-weight-requests/{requestId}/decision", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVED","note":"ok","adminId":"%s"}
                                """.formatted(UUID.randomUUID()))
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID())))
                        .with(csrf()))
                .andExpect(status().isNotFound());
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
                "-10", "10", "40", "60", true)
                .andExpect(status().isBadRequest());
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "10", "40", "20", true)
                .andExpect(status().isBadRequest());
    }

    @Test
    void allTeamsInTheSameCourseResolveTheIdenticalUpdatedWeights() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "20", "20", "20", true)
                .andExpect(status().isOk());

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        fixture.teamA().setCourse(reloaded);
        fixture.teamB().setCourse(reloaded);
        fixture.teamC().setCourse(reloaded);

        for (Team team : List.of(fixture.teamA(), fixture.teamB(), fixture.teamC())) {
            var weights = sliceWeightResolver.resolve(team);
            assertThat(weights.code()).isEqualByComparingTo("40");
            assertThat(weights.test()).isEqualByComparingTo("20");
            assertThat(weights.document()).isEqualByComparingTo("20");
            assertThat(weights.research()).isEqualByComparingTo("20");
        }

        mockMvc.perform(get(EVALUATION_PATH, fixture.teamA().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(fixture.teamA().getId().toString()));
        mockMvc.perform(get(EVALUATION_PATH, fixture.teamC().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(fixture.teamC().getId().toString()));
    }

    @Test
    void anotherCourseKeepsIndependentWeightsUnaffectedByThisCoursesUpdate() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "20", "20", "20", true)
                .andExpect(status().isOk());

        Lecturer otherOwner = lecturer("other-course-owner");
        Course otherCourse = courseRepository.save(Course.builder()
                .courseCode("WEIGHT-OTHER-" + UUID.randomUUID())
                .name("Other Course")
                .instructor(otherOwner)
                .codeContributionWeight(10.0)
                .testContributionWeight(40.0)
                .documentContributionWeight(30.0)
                .researchContributionWeight(20.0)
                .build());
        TeamGraph otherCourseTeam = teamGraph(otherCourse, "cross-course");

        var thisCourseWeights = sliceWeightResolver.resolve(fixture.teamA());
        var otherCourseWeights = sliceWeightResolver.resolve(otherCourseTeam.team());

        assertThat(thisCourseWeights.code()).isEqualByComparingTo("40");
        assertThat(otherCourseWeights.code()).isEqualByComparingTo("10");
        assertThat(otherCourseWeights.test()).isEqualByComparingTo("40");
    }

    @Test
    void historicalProjectGroupWeightConfigRowIsIgnoredWhileCourseModeIsActive() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "20", "20", "20", true)
                .andExpect(status().isOk());
        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        fixture.teamA().setCourse(reloaded);

        groupWeightConfigRepository.save(ProjectGroupWeightConfig.builder()
                .project(fixture.projectA())
                .team(fixture.teamA())
                .codeWeight(new BigDecimal("0.9"))
                .testWeight(new BigDecimal("0.0"))
                .documentWeight(new BigDecimal("0.05"))
                .researchWeight(new BigDecimal("0.05"))
                .designWeight(BigDecimal.ZERO)
                .build());

        var weights = sliceWeightResolver.resolve(fixture.teamA());

        assertThat(weights.code()).isEqualByComparingTo("40");
        assertThat(weights.test()).isEqualByComparingTo("20");
        assertThat(weights.document()).isEqualByComparingTo("20");
        assertThat(weights.research()).isEqualByComparingTo("20");
        assertThat(groupWeightConfigRepository.findByProjectId(fixture.projectA().getId())).isPresent();

        mockMvc.perform(get(EVALUATION_PATH, fixture.teamA().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void teamModeActivationRejectedWhenATeamIsMissingAnOverride() throws Exception {
        Fixture fixture = fixture();
        putGroupWeights(fixture.owner().getId(), fixture.projectA().getId(), fixture.teamA().getId(),
                "0.5", "0.2", "0.2", "0.1")
                .andExpect(status().isOk());
        // teamB and teamC intentionally left unconfigured.

        switchMode(fixture.owner().getId(), fixture.course().getId(), "TEAM")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TEAM_MODE_CONFIGURATION_INCOMPLETE"));

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        assertThat(reloaded.getContributionConfigMode()).isEqualTo(ContributionConfigMode.COURSE);
    }

    @Test
    void teamModeActivationSucceedsWhenEveryCurrentTeamHasAValidOverride() throws Exception {
        Fixture fixture = fixture();
        putGroupWeights(fixture.owner().getId(), fixture.projectA().getId(), fixture.teamA().getId(),
                "0.5", "0.2", "0.2", "0.1")
                .andExpect(status().isOk());
        putGroupWeights(fixture.owner().getId(), fixture.projectB().getId(), fixture.teamB().getId(),
                "0.2", "0.6", "0.15", "0.05")
                .andExpect(status().isOk());
        putGroupWeights(fixture.owner().getId(), fixture.projectC().getId(), fixture.teamC().getId(),
                "0.25", "0.25", "0.25", "0.25")
                .andExpect(status().isOk());

        switchMode(fixture.owner().getId(), fixture.course().getId(), "TEAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("TEAM"));

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        assertThat(reloaded.getContributionConfigMode()).isEqualTo(ContributionConfigMode.TEAM);
    }

    @Test
    void teamModeUsesExactTeamOverrideAndDoesNotFallBackToCourse() throws Exception {
        Fixture fixture = fixture();
        activateTeamModeForAllThreeTeams(fixture);

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        fixture.teamA().setCourse(reloaded);
        fixture.teamB().setCourse(reloaded);

        var teamAWeights = sliceWeightResolver.resolve(fixture.teamA());
        assertThat(teamAWeights.code()).isEqualByComparingTo("50");
        assertThat(teamAWeights.test()).isEqualByComparingTo("20");

        var teamBWeights = sliceWeightResolver.resolve(fixture.teamB());
        assertThat(teamBWeights.code()).isEqualByComparingTo("20");
        assertThat(teamBWeights.test()).isEqualByComparingTo("60");

        mockMvc.perform(get(EVALUATION_PATH, fixture.teamA().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk());
    }

    @Test
    void teamModeFailsClosedForATeamCreatedAfterActivationInsteadOfFallingBackToCourse() throws Exception {
        Fixture fixture = fixture();
        activateTeamModeForAllThreeTeams(fixture);

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        TeamGraph newTeam = teamGraph(reloaded, "late-joiner");
        membership(newTeam.team(), student("late-leader"), RoleInTeam.LEADER);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.saga.be.exception.IntegrationException.class,
                () -> sliceWeightResolver.resolve(newTeam.team())
        );
    }

    @Test
    void switchingBackToCourseModeRestoresCourseWeightsForAllTeamsAndRetainsHistoricalOverrides() throws Exception {
        Fixture fixture = fixture();
        activateTeamModeForAllThreeTeams(fixture);

        switchMode(fixture.owner().getId(), fixture.course().getId(), "COURSE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COURSE"));

        Course reloaded = courseRepository.findById(fixture.course().getId()).orElseThrow();
        fixture.teamA().setCourse(reloaded);
        fixture.teamB().setCourse(reloaded);

        var teamAWeights = sliceWeightResolver.resolve(fixture.teamA());
        var teamBWeights = sliceWeightResolver.resolve(fixture.teamB());
        assertThat(teamAWeights).isEqualTo(teamBWeights);

        assertThat(groupWeightConfigRepository.findByProjectId(fixture.projectA().getId())).isPresent();
    }

    @Test
    void modeSwitchRequiresLecturerOwnerAndCsrf() throws Exception {
        Fixture fixture = fixture();

        switchMode(fixture.otherLecturer().getId(), fixture.course().getId(), "COURSE")
                .andExpect(status().isForbidden());
        mockMvc.perform(put(MODE_PATH, fixture.course().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"COURSE\"}")
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamMenuReflectsCourseModeSourceForEveryTeam() throws Exception {
        Fixture fixture = fixture();
        update(fixture, ApplicationRole.LECTURER, fixture.owner().getId(), fixture.course().getId(),
                "40", "20", "20", "20", true)
                .andExpect(status().isOk());

        mockMvc.perform(get(TEAM_WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COURSE"))
                .andExpect(jsonPath("$.teams.length()").value(3))
                .andExpect(jsonPath("$.teams[?(@.teamId=='" + fixture.teamA().getId() + "')].source").value("COURSE"))
                .andExpect(jsonPath("$.teams[?(@.teamId=='" + fixture.teamA().getId() + "')].codeWeight").value(40.0));
    }

    @Test
    void teamMenuShowsIncompleteSourceForTeamsMissingAnOverrideInTeamMode() throws Exception {
        Fixture fixture = fixture();
        putGroupWeights(fixture.owner().getId(), fixture.projectA().getId(), fixture.teamA().getId(),
                "0.5", "0.2", "0.2", "0.1")
                .andExpect(status().isOk());

        mockMvc.perform(get(TEAM_WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COURSE"))
                .andExpect(jsonPath("$.teams[?(@.teamId=='" + fixture.teamA().getId() + "')].source").value("COURSE"));

        activateTeamModeMissingTeamCOverride(fixture);

        mockMvc.perform(get(TEAM_WEIGHTS_PATH, fixture.course().getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, fixture.owner().getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("COURSE"));
    }

    private void activateTeamModeMissingTeamCOverride(Fixture fixture) throws Exception {
        putGroupWeights(fixture.owner().getId(), fixture.projectB().getId(), fixture.teamB().getId(),
                "0.2", "0.6", "0.15", "0.05")
                .andExpect(status().isOk());
        switchMode(fixture.owner().getId(), fixture.course().getId(), "TEAM")
                .andExpect(status().isConflict());
    }

    private void activateTeamModeForAllThreeTeams(Fixture fixture) throws Exception {
        putGroupWeights(fixture.owner().getId(), fixture.projectA().getId(), fixture.teamA().getId(),
                "0.5", "0.2", "0.2", "0.1")
                .andExpect(status().isOk());
        putGroupWeights(fixture.owner().getId(), fixture.projectB().getId(), fixture.teamB().getId(),
                "0.2", "0.6", "0.15", "0.05")
                .andExpect(status().isOk());
        putGroupWeights(fixture.owner().getId(), fixture.projectC().getId(), fixture.teamC().getId(),
                "0.25", "0.25", "0.25", "0.25")
                .andExpect(status().isOk());
        switchMode(fixture.owner().getId(), fixture.course().getId(), "TEAM")
                .andExpect(status().isOk());
    }

    private ResultActions putGroupWeights(
            UUID actorId,
            UUID projectId,
            UUID groupId,
            String code,
            String test,
            String document,
            String research
    ) throws Exception {
        return mockMvc.perform(put(GROUP_WEIGHTS_PATH, projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "groupId": "%s",
                          "codeWeight": %s,
                          "testWeight": %s,
                          "documentWeight": %s,
                          "researchWeight": %s
                        }
                        """.formatted(groupId, code, test, document, research))
                .with(authentication(authenticationFor(ApplicationRole.LECTURER, actorId)))
                .with(csrf()));
    }

    private ResultActions switchMode(UUID actorId, UUID courseId, String mode) throws Exception {
        return mockMvc.perform(put(MODE_PATH, courseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"" + mode + "\"}")
                .with(authentication(authenticationFor(ApplicationRole.LECTURER, actorId)))
                .with(csrf()));
    }

    private ResultActions update(
            Fixture fixture,
            ApplicationRole role,
            UUID actorId,
            UUID courseId,
            String code,
            String test,
            String document,
            String research,
            boolean includeCsrf
    ) throws Exception {
        var request = put(WEIGHTS_PATH, courseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(code, test, document, research))
                .with(authentication(authenticationFor(role, actorId)));
        if (includeCsrf) {
            request = request.with(csrf());
        }
        return mockMvc.perform(request);
    }

    private String body(String code, String test, String document, String research) {
        return """
                {
                  "codeWeight": %s,
                  "testWeight": %s,
                  "documentWeight": %s,
                  "researchWeight": %s
                }
                """.formatted(code, test, document, research);
    }

    private Fixture fixture() {
        Lecturer owner = lecturer("owner");
        Lecturer otherLecturer = lecturer("other");
        Course course = courseRepository.save(Course.builder()
                .courseCode("WEIGHT-" + UUID.randomUUID())
                .name("Weight Course")
                .instructor(owner)
                .codeContributionWeight(25.0)
                .testContributionWeight(25.0)
                .documentContributionWeight(25.0)
                .researchContributionWeight(25.0)
                .contributionConfigMode(ContributionConfigMode.COURSE)
                .build());
        TeamGraph teamA = teamGraph(course, "a");
        TeamGraph teamB = teamGraph(course, "b");
        TeamGraph teamC = teamGraph(course, "c");
        Student leader = student("leader");
        membership(teamA.team(), leader, RoleInTeam.LEADER);
        membership(teamB.team(), student("b-leader"), RoleInTeam.LEADER);
        membership(teamC.team(), student("c-leader"), RoleInTeam.LEADER);
        return new Fixture(owner, otherLecturer, course,
                teamA.project(), teamA.team(), teamB.project(), teamB.team(), teamC.project(), teamC.team(),
                leader);
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
            Project projectA,
            Team teamA,
            Project projectB,
            Team teamB,
            Project projectC,
            Team teamC,
            Student leader
    ) {}
}
