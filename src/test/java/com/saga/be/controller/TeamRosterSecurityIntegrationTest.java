package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TeamRosterSecurityIntegrationTest {

    private static final String ROSTER_PATH = "/api/v1/courses/{courseId}/teams/{teamId}/members";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private LecturerRepository lecturerRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private StudentCourseInvitationRepository invitationRepository;

    @AfterEach
    void cleanUp() {
        invitationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void adminMayViewAnyTeam() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Admin roster");
        membership(team, student("leader"), RoleInTeam.LEADER);
        membership(team, student("member"), RoleInTeam.MEMBER);

        request(ApplicationRole.ADMIN, UUID.randomUUID(), team)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void assignedLecturerMayViewOwnCourseTeam() throws Exception {
        Lecturer owner = lecturer("owner");
        Team team = team(createCourse(owner), "Lecturer roster");
        membership(team, student("member"), RoleInTeam.MEMBER);

        request(ApplicationRole.LECTURER, owner.getId(), team)
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedLecturerIsForbidden() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Other lecturer roster");

        request(ApplicationRole.LECTURER, lecturer("other").getId(), team)
                .andExpect(status().isForbidden());
    }

    @Test
    void leaderAndMemberOfTheExactTeamMayViewRoster() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Student roster");
        Student leader = student("leader");
        Student member = student("member");
        membership(team, leader, RoleInTeam.LEADER);
        membership(team, member, RoleInTeam.MEMBER);

        request(ApplicationRole.STUDENT, leader.getId(), team)
                .andExpect(status().isOk());
        request(ApplicationRole.STUDENT, member.getId(), team)
                .andExpect(status().isOk());
    }

    @Test
    void studentInAnotherTeamOfTheSameCourseIsForbidden() throws Exception {
        Course course = createCourse(lecturer("owner"));
        Team requestedTeam = team(course, "Requested roster");
        Team otherTeam = team(course, "Other roster");
        Student otherStudent = student("other");
        membership(otherTeam, otherStudent, RoleInTeam.MEMBER);

        request(ApplicationRole.STUDENT, otherStudent.getId(), requestedTeam)
                .andExpect(status().isForbidden());
    }

    @Test
    void studentOutsideTheCourseIsForbidden() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Outside student roster");

        request(ApplicationRole.STUDENT, student("outside").getId(), team)
                .andExpect(status().isForbidden());
    }

    @Test
    void teamFromAnotherCourseIsNotFound() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Mismatched course roster");
        Course anotherCourse = createCourse(lecturer("another-owner"));

        mockMvc.perform(get(ROSTER_PATH, anotherCourse.getId(), team.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTeamIsNotFound() throws Exception {
        Course course = createCourse(lecturer("owner"));

        mockMvc.perform(get(ROSTER_PATH, course.getId(), UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rosterSupportsPagination() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Paged roster");
        membership(team, student("first"), RoleInTeam.MEMBER);
        membership(team, student("second"), RoleInTeam.MEMBER);
        membership(team, student("third"), RoleInTeam.MEMBER);

        mockMvc.perform(get(ROSTER_PATH, team.getCourse().getId(), team.getId())
                        .param("page", "1")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void responseContainsTheMembershipRole() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Role roster");
        membership(team, student("leader"), RoleInTeam.LEADER);
        membership(team, student("member"), RoleInTeam.MEMBER);

        request(ApplicationRole.ADMIN, UUID.randomUUID(), team)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.roleInTeam == 'LEADER')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.roleInTeam == 'MEMBER')]").isNotEmpty());
    }

    @Test
    void responseDoesNotExposeStudentIdentityOrAccountFields() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Private roster");
        membership(team, student("private"), RoleInTeam.MEMBER);

        request(ApplicationRole.ADMIN, UUID.randomUUID(), team)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.content[0].cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.content[0].version").doesNotExist());
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        Team team = team(createCourse(lecturer("owner")), "Anonymous roster");

        mockMvc.perform(get(ROSTER_PATH, team.getCourse().getId(), team.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsRosterPaginationSecurityAndResponses() throws Exception {
        String operation = "$.paths['/api/v1/courses/{courseId}/teams/{teamId}/members'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name")
                        .value("JSESSIONID"))
                .andExpect(jsonPath(operation).exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'page')]").isNotEmpty())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'size')]").isNotEmpty())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions request(
            ApplicationRole role,
            UUID profileId,
            Team team
    ) throws Exception {
        return mockMvc.perform(get(ROSTER_PATH, team.getCourse().getId(), team.getId())
                .with(authentication(authenticationFor(role, profileId))));
    }

    private Lecturer lecturer(String label) {
        String suffix = UUID.randomUUID().toString();
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + label + "-" + suffix)
                .email("lecturer-" + label + "-" + suffix + "@example.test")
                .fullName("Lecturer " + label)
                .build());
    }

    private Course createCourse(Lecturer instructor) {
        String suffix = UUID.randomUUID().toString();
        return courseRepository.save(Course.builder()
                .courseCode("COURSE-" + suffix)
                .name("Course " + suffix)
                .instructor(instructor)
                .build());
    }

    private Team team(Course course, String name) {
        return teamRepository.save(Team.builder().course(course).name(name).build());
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
                null
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }
}
