package com.saga.be.controller;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.saga.be.integration.provider.GitHubProviderClient;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.contribution.ContributionCalculationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseStudentBasicInfoIntegrationTest {

    private static final String PATH =
            "/api/v1/courses/{courseId}/students/{studentId}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CourseRepository courses;
    @Autowired
    private LecturerRepository lecturers;
    @Autowired
    private StudentRepository students;
    @Autowired
    private TeamRepository teams;
    @Autowired
    private TeamMemberRepository memberships;

    @MockitoBean
    private ContributionCalculationService contributions;
    @MockitoBean
    private JiraProviderClient jiraProvider;
    @MockitoBean
    private GitHubProviderClient gitHubProvider;

    @AfterEach
    void cleanUp() {
        memberships.deleteAll();
        teams.deleteAll();
        students.deleteAll();
        courses.deleteAll();
        lecturers.deleteAll();
    }

    @Test
    void adminSessionReadsCompleteBasicInfoWithoutCsrfOrExternalServices()
            throws Exception {
        Course course = course(lecturer("owner"));
        Student student = student("SE000001", AccountStatus.SUSPENDED);
        Team team = team(course, "Team Mentor");
        membership(team, student, RoleInTeam.MENTOR);
        clearInvocations(contributions, jiraProvider, gitHubProvider);

        mockMvc.perform(get(PATH, course.getId(), student.getId())
                        .session(session(ApplicationRole.ADMIN, UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andExpect(jsonPath("$.studentId").value(student.getId().toString()))
                .andExpect(jsonPath("$.studentCode").value("SE000001"))
                .andExpect(jsonPath("$.fullName").value("Student SE000001"))
                .andExpect(jsonPath("$.email").value("se000001@example.test"))
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.team.teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$.team.teamName").value("Team Mentor"))
                .andExpect(jsonPath("$.team.roleInTeam").value("MENTOR"))
                .andExpect(jsonPath("$.cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.credential").doesNotExist());

        verifyNoInteractions(contributions, jiraProvider, gitHubProvider);
    }

    @Test
    void assignedLecturerMayReadOwnCourseStudent() throws Exception {
        Lecturer owner = lecturer("assigned");
        Course course = course(owner);
        Student student = student("SE000002", AccountStatus.ACTIVE);
        membership(team(course, "Team Owner"), student, RoleInTeam.LEADER);

        request(ApplicationRole.LECTURER, owner.getId(), course, student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.roleInTeam").value("LEADER"));
    }

    @Test
    void unrelatedLecturerAndStudentAreForbidden() throws Exception {
        Course course = course(lecturer("owner"));
        Student student = student("SE000003", AccountStatus.ACTIVE);
        membership(team(course, "Team Access"), student, RoleInTeam.MEMBER);

        request(ApplicationRole.LECTURER, lecturer("other").getId(), course, student)
                .andExpect(status().isForbidden());
        request(ApplicationRole.STUDENT, student.getId(), course, student)
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousAndBearerOnlyRequestsAreUnauthorized() throws Exception {
        Course course = course(lecturer("owner"));
        Student student = student("SE000004", AccountStatus.ACTIVE);
        membership(team(course, "Team Anonymous"), student, RoleInTeam.MEMBER);

        mockMvc.perform(get(PATH, course.getId(), student.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH, course.getId(), student.getId())
                        .header("Authorization", "Bearer not-a-session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCourseOrStudentReturnsNotFound() throws Exception {
        Course course = course(lecturer("owner"));

        request(
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        ).andExpect(status().isNotFound());
        request(
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                course.getId(),
                UUID.randomUUID()
        ).andExpect(status().isNotFound());
    }

    @Test
    void studentWithMembershipOnlyInAnotherCourseReturnsNotFound()
            throws Exception {
        Course requestedCourse = course(lecturer("requested"));
        Course otherCourse = course(lecturer("other"));
        Student student = student("SE000005", AccountStatus.PENDING);
        membership(team(otherCourse, "Other Team"), student, RoleInTeam.MEMBER);

        request(ApplicationRole.ADMIN, UUID.randomUUID(), requestedCourse, student)
                .andExpect(status().isNotFound());
    }

    @Test
    void multipleLegacyMembershipsInOneCourseReturnConflictWithoutMutation()
            throws Exception {
        Course course = course(lecturer("owner"));
        Student student = student("SE000006", AccountStatus.ACTIVE);
        membership(team(course, "Legacy One"), student, RoleInTeam.MEMBER);
        membership(team(course, "Legacy Two"), student, RoleInTeam.MENTOR);
        long before = memberships.count();

        request(ApplicationRole.ADMIN, UUID.randomUUID(), course, student)
                .andExpect(status().isConflict());

        org.junit.jupiter.api.Assertions.assertEquals(before, memberships.count());
    }

    private org.springframework.test.web.servlet.ResultActions request(
            ApplicationRole role,
            UUID profileId,
            Course course,
            Student student
    ) throws Exception {
        return request(role, profileId, course.getId(), student.getId());
    }

    private org.springframework.test.web.servlet.ResultActions request(
            ApplicationRole role,
            UUID profileId,
            UUID courseId,
            UUID studentId
    ) throws Exception {
        return mockMvc.perform(get(PATH, courseId, studentId)
                .with(authentication(authenticationFor(role, profileId))));
    }

    private MockHttpSession session(ApplicationRole role, UUID profileId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationFor(role, profileId));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
        return session;
    }

    private Authentication authenticationFor(
            ApplicationRole role,
            UUID profileId
    ) {
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

    private Lecturer lecturer(String suffix) {
        return lecturers.save(Lecturer.builder()
                .cognitoSub("lecturer-" + suffix + "-" + UUID.randomUUID())
                .fullName("Lecturer " + suffix)
                .email(suffix + "-" + UUID.randomUUID() + "@example.test")
                .build());
    }

    private Course course(Lecturer lecturer) {
        return courses.save(Course.builder()
                .courseCode("COURSE-" + UUID.randomUUID())
                .name("Course student detail")
                .instructor(lecturer)
                .build());
    }

    private Student student(String studentCode, AccountStatus status) {
        return students.save(Student.builder()
                .cognitoSub("student-" + studentCode + "-" + UUID.randomUUID())
                .studentCode(studentCode)
                .fullName("Student " + studentCode)
                .email(studentCode.toLowerCase() + "@example.test")
                .accountStatus(status)
                .build());
    }

    private Team team(Course course, String name) {
        return teams.save(Team.builder().course(course).name(name).build());
    }

    private TeamMember membership(
            Team team,
            Student student,
            RoleInTeam role
    ) {
        return memberships.save(TeamMember.builder()
                .team(team)
                .student(student)
                .roleInTeam(role)
                .build());
    }
}
