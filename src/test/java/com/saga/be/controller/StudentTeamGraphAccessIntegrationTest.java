package com.saga.be.controller;

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
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.BoardType;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class StudentTeamGraphAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private JiraBoardRepository jiraBoardRepository;
    @Autowired private SprintRepository sprintRepository;

    @Test
    void adminAndOwningLecturerMayReadEveryTeamGraphWhileOtherLecturerIsForbidden() throws Exception {
        Fixture fixture = fixture();

        for (String path : graphPaths(fixture, fixture.member().getId(), fixture.sprint().getId())) {
            perform(path, ApplicationRole.ADMIN, UUID.randomUUID()).andExpect(status().isOk());
            perform(path, ApplicationRole.LECTURER, fixture.owner().getId()).andExpect(status().isOk());
            perform(path, ApplicationRole.LECTURER, fixture.otherLecturer().getId())
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void exactTeamLeaderAndMemberMayReadEveryTeamGraphButMentorMayNot() throws Exception {
        Fixture fixture = fixture();

        for (String path : graphPaths(fixture, fixture.member().getId(), fixture.sprint().getId())) {
            perform(path, ApplicationRole.STUDENT, fixture.leader().getId()).andExpect(status().isOk());
            perform(path, ApplicationRole.STUDENT, fixture.member().getId()).andExpect(status().isOk());
            perform(path, ApplicationRole.STUDENT, fixture.mentor().getId()).andExpect(status().isForbidden());
        }
    }

    @Test
    void sameCourseOtherTeamAndDifferentCourseStudentsAreForbiddenForEveryGraph() throws Exception {
        Fixture fixture = fixture();

        for (String path : graphPaths(fixture, fixture.member().getId(), fixture.sprint().getId())) {
            perform(path, ApplicationRole.STUDENT, fixture.otherTeamMember().getId())
                    .andExpect(status().isForbidden());
            perform(path, ApplicationRole.STUDENT, fixture.otherCourseMember().getId())
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousIsUnauthorizedForEveryGraph() throws Exception {
        Fixture fixture = fixture();

        for (String path : graphPaths(fixture, fixture.member().getId(), fixture.sprint().getId())) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void blockedStudentAccountStatusesCannotBypassTheExistingFilter() throws Exception {
        Fixture fixture = fixture();

        for (AccountStatus accountStatus : List.of(
                AccountStatus.PENDING, AccountStatus.INACTIVE, AccountStatus.SUSPENDED)) {
            fixture.member().setAccountStatus(accountStatus);
            studentRepository.saveAndFlush(fixture.member());

            for (String path : graphPaths(fixture, fixture.member().getId(), fixture.sprint().getId())) {
                perform(path, ApplicationRole.STUDENT, fixture.member().getId())
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.error").value("ACCOUNT_STATUS_ACCESS_DENIED"));
            }
        }
    }

    @Test
    void courseAndTeamIdMixAndMatchIsNotFoundForEveryGraph() throws Exception {
        Fixture fixture = fixture();

        for (String path : graphPaths(
                fixture.otherCourse().getId(),
                fixture.team().getId(),
                fixture.member().getId(),
                fixture.sprint().getId()
        )) {
            perform(path, ApplicationRole.ADMIN, UUID.randomUUID()).andExpect(status().isNotFound());
        }
    }

    @Test
    void interactionAllowsSameTeamOtherMemberButFailsClosedForCrossTeamTarget() throws Exception {
        Fixture fixture = fixture();

        perform(interactionPath(fixture, fixture.member().getId()), ApplicationRole.STUDENT, fixture.leader().getId())
                .andExpect(status().isOk());
        perform(interactionPath(fixture, fixture.leader().getId()), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
        perform(interactionPath(fixture, fixture.otherTeamMember().getId()),
                ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isNotFound());
        perform(interactionPath(
                fixture.course().getId(),
                fixture.otherTeam().getId(),
                fixture.otherTeamMember().getId()
        ), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void heatmapAllowsWholeTeamSelfAndSameTeamMemberButRejectsOutsideStudentFilter() throws Exception {
        Fixture fixture = fixture();
        String base = heatmapPath(fixture.course().getId(), fixture.team().getId());

        perform(base, ApplicationRole.STUDENT, fixture.member().getId()).andExpect(status().isOk());
        perform(base + "&studentId=" + fixture.member().getId(), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
        perform(base + "&studentId=" + fixture.leader().getId(), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
        perform(base + "&studentId=" + fixture.otherTeamMember().getId(),
                ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void burndownAllowsOwnTeamSprintAndRejectsOtherTeamOrMissingSprint() throws Exception {
        Fixture fixture = fixture();

        perform(burndownPath(fixture.course().getId(), fixture.team().getId(), fixture.sprint().getId()),
                ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isOk());
        perform(burndownPath(fixture.course().getId(), fixture.team().getId(), fixture.otherSprint().getId()),
                ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isNotFound());
        perform(burndownPath(fixture.course().getId(), fixture.team().getId(), UUID.randomUUID()),
                ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void studentAccessIsNotBroadenedToOtherLecturerAnalyticsRoutes() throws Exception {
        Fixture fixture = fixture();

        perform("/api/v1/courses/%s/teams/%s/detail".formatted(
                fixture.course().getId(), fixture.team().getId()), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isForbidden());
        perform("/api/v1/courses/%s/teams/%s/sprints/velocity".formatted(
                fixture.course().getId(), fixture.team().getId()), ApplicationRole.STUDENT, fixture.member().getId())
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentsOnlyTheFourCurrentGraphRoutesForStudentTeamRead() throws Exception {
        for (String path : List.of(
                "/api/v1/courses/{courseId}/teams/{teamId}/overview",
                "/api/v1/courses/{courseId}/teams/{teamId}/heatmap",
                "/api/v1/courses/{courseId}/teams/{teamId}/students/{studentId}/interactions",
                "/api/v1/courses/{courseId}/teams/{teamId}/sprints/{sprintId}/burndown"
        )) {
            String operation = "$.paths['" + path + "'].get";
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(operation).exists())
                    .andExpect(jsonPath(operation + ".description")
                            .value(org.hamcrest.Matchers.allOf(
                                    org.hamcrest.Matchers.containsString("STUDENT"),
                                    org.hamcrest.Matchers.containsString("LEADER"),
                                    org.hamcrest.Matchers.containsString("MEMBER")
                            )));
        }
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/analytics/{courseId}/teams/{teamId}/overview']")
                        .doesNotExist());
    }

    private ResultActions perform(String path, ApplicationRole role, UUID profileId) throws Exception {
        return mockMvc.perform(get(path).with(authentication(authenticationFor(role, profileId))));
    }

    private List<String> graphPaths(Fixture fixture, UUID targetStudentId, UUID sprintId) {
        return graphPaths(fixture.course().getId(), fixture.team().getId(), targetStudentId, sprintId);
    }

    private List<String> graphPaths(UUID courseId, UUID teamId, UUID targetStudentId, UUID sprintId) {
        return List.of(
                "/api/v1/courses/%s/teams/%s/overview?startDate=2026-08-01&endDate=2026-08-02"
                        .formatted(courseId, teamId),
                heatmapPath(courseId, teamId),
                interactionPath(courseId, teamId, targetStudentId),
                burndownPath(courseId, teamId, sprintId)
        );
    }

    private String heatmapPath(UUID courseId, UUID teamId) {
        return "/api/v1/courses/%s/teams/%s/heatmap?startDate=2026-08-01&endDate=2026-08-02"
                .formatted(courseId, teamId);
    }

    private String interactionPath(Fixture fixture, UUID studentId) {
        return interactionPath(fixture.course().getId(), fixture.team().getId(), studentId);
    }

    private String interactionPath(UUID courseId, UUID teamId, UUID studentId) {
        return "/api/v1/courses/%s/teams/%s/students/%s/interactions"
                .formatted(courseId, teamId, studentId);
    }

    private String burndownPath(UUID courseId, UUID teamId, UUID sprintId) {
        return "/api/v1/courses/%s/teams/%s/sprints/%s/burndown"
                .formatted(courseId, teamId, sprintId);
    }

    private Fixture fixture() {
        Lecturer owner = lecturer("owner");
        Lecturer otherLecturer = lecturer("other");
        Course course = course(owner, "primary");
        TeamGraph primary = teamGraph(course, "primary");
        TeamGraph other = teamGraph(course, "same-course-other-team");
        Course otherCourse = course(lecturer("other-course-owner"), "other-course");
        TeamGraph differentCourse = teamGraph(otherCourse, "different-course-team");

        Student leader = student("leader");
        Student member = student("member");
        Student mentor = student("mentor");
        Student otherTeamMember = student("other-team-member");
        Student otherCourseMember = student("other-course-member");
        membership(primary.team(), leader, RoleInTeam.LEADER);
        membership(primary.team(), member, RoleInTeam.MEMBER);
        membership(primary.team(), mentor, RoleInTeam.MENTOR);
        membership(other.team(), otherTeamMember, RoleInTeam.MEMBER);
        membership(differentCourse.team(), otherCourseMember, RoleInTeam.MEMBER);

        return new Fixture(
                owner,
                otherLecturer,
                course,
                primary.team(),
                primary.sprint(),
                other.team(),
                other.sprint(),
                otherCourse,
                leader,
                member,
                mentor,
                otherTeamMember,
                otherCourseMember
        );
    }

    private TeamGraph teamGraph(Course course, String label) {
        String suffix = UUID.randomUUID().toString();
        Project project = projectRepository.save(Project.builder()
                .course(course)
                .name("Project " + label + " " + suffix)
                .build());
        Team team = teamRepository.save(Team.builder()
                .course(course)
                .project(project)
                .name("Team " + label + " " + suffix)
                .build());
        JiraBoard board = jiraBoardRepository.save(JiraBoard.builder()
                .project(project)
                .name("Board " + label)
                .type(BoardType.SCRUM)
                .jiraBoardId("board-" + suffix)
                .cloudId("cloud-" + suffix)
                .jiraProjectId("jira-project-" + suffix)
                .projectKey("KEY" + suffix.substring(0, 4).toUpperCase())
                .connectionStatus(IntegrationStatus.ACTIVE)
                .build());
        Sprint sprint = sprintRepository.save(Sprint.builder()
                .board(board)
                .name("Sprint " + label)
                .externalSprintId("sprint-" + suffix)
                .startDate(LocalDateTime.of(2026, 8, 1, 0, 0))
                .endDate(LocalDateTime.of(2026, 8, 2, 23, 59))
                .state("active")
                .build());
        return new TeamGraph(team, sprint);
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

    private Course course(Lecturer instructor, String label) {
        String suffix = UUID.randomUUID().toString();
        return courseRepository.save(Course.builder()
                .courseCode("GRAPH-" + label + "-" + suffix)
                .name("Graph Course " + label)
                .instructor(instructor)
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

    private record TeamGraph(Team team, Sprint sprint) {}

    private record Fixture(
            Lecturer owner,
            Lecturer otherLecturer,
            Course course,
            Team team,
            Sprint sprint,
            Team otherTeam,
            Sprint otherSprint,
            Course otherCourse,
            Student leader,
            Student member,
            Student mentor,
            Student otherTeamMember,
            Student otherCourseMember
    ) {}
}
