package com.saga.be.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.saga.be.repository.ProjectRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class MyCourseTeamMembersIntegrationTest {

    private static final String PATH = "/api/me/courses/{courseId}/team/members";

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
    private ProjectRepository projectRepository;
    @Autowired
    private StudentCourseInvitationRepository invitationRepository;

    @AfterEach
    void cleanUp() {
        invitationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        projectRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void anonymousAdminAndLecturerCannotUseTheStudentSelfScopedEndpoint() throws Exception {
        Course course = course("auth");

        mockMvc.perform(get(PATH, course.getId()))
                .andExpect(status().isUnauthorized());
        request(ApplicationRole.ADMIN, UUID.randomUUID(), course)
                .andExpect(status().isForbidden());
        request(ApplicationRole.LECTURER, UUID.randomUUID(), course)
                .andExpect(status().isForbidden());
    }

    @Test
    void studentReceivesResolvedTeamProjectRoleMembersAndNoSensitiveFields() throws Exception {
        Course course = course("project");
        Team team = team(course, "Resolved Team");
        Student currentStudent = student("current");
        membership(team, currentStudent, RoleInTeam.LEADER);
        membership(team, student("other"), RoleInTeam.MEMBER);
        project(team, course, "Resolved Project");

        request(ApplicationRole.STUDENT, currentStudent.getId(), course)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andExpect(jsonPath("$.teamId").value(team.getId().toString()))
                .andExpect(jsonPath("$.teamName").value("Resolved Team"))
                .andExpect(jsonPath("$.roleInTeam").value("LEADER"))
                .andExpect(jsonPath("$.project.id").exists())
                .andExpect(jsonPath("$.project.name").value("Resolved Project"))
                .andExpect(jsonPath("$.members.content.length()").value(2))
                .andExpect(jsonPath("$.members.content[?(@.studentId == '" + currentStudent.getId() + "')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.members.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.members.content[0].cognitoSub").doesNotExist())
                .andExpect(jsonPath("$.members.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.credential").doesNotExist());
    }

    @Test
    void studentReceivesNullProjectWhenResolvedTeamHasNoProject() throws Exception {
        Course course = course("no-project");
        Team team = team(course, "No Project Team");
        Student currentStudent = student("no-project-current");
        membership(team, currentStudent, RoleInTeam.MEMBER);

        request(ApplicationRole.STUDENT, currentStudent.getId(), course)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.roleInTeam").value("MEMBER"));
    }

    @Test
    void studentWithoutMembershipAndMissingCourseReceiveNotFound() throws Exception {
        Course targetCourse = course("target");
        Course otherCourse = course("other");
        Student currentStudent = student("outside");
        membership(team(otherCourse, "Other Course Team"), currentStudent, RoleInTeam.MEMBER);

        request(ApplicationRole.STUDENT, currentStudent.getId(), targetCourse)
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH, UUID.randomUUID())
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, currentStudent.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void legacyMultipleTeamsInOneCourseReturnsConflictWithoutSelectingOne() throws Exception {
        Course course = course("legacy");
        Student currentStudent = student("legacy-current");
        membership(team(course, "Legacy A"), currentStudent, RoleInTeam.MEMBER);
        membership(team(course, "Legacy B"), currentStudent, RoleInTeam.LEADER);

        request(ApplicationRole.STUDENT, currentStudent.getId(), course)
                .andExpect(status().isConflict());
    }

    @Test
    void studentWithTeamsInDifferentCoursesResolvesOnlyTheRequestedCourse() throws Exception {
        Course firstCourse = course("first");
        Course secondCourse = course("second");
        Student currentStudent = student("multi-course");
        Team firstTeam = team(firstCourse, "First Team");
        Team secondTeam = team(secondCourse, "Second Team");
        membership(firstTeam, currentStudent, RoleInTeam.LEADER);
        membership(secondTeam, currentStudent, RoleInTeam.MEMBER);

        request(ApplicationRole.STUDENT, currentStudent.getId(), secondCourse)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(secondCourse.getId().toString()))
                .andExpect(jsonPath("$.teamId").value(secondTeam.getId().toString()))
                .andExpect(jsonPath("$.roleInTeam").value("MEMBER"));
    }

    @Test
    void selfScopedMembersUseRosterPaginationAndValidationWithoutCsrf() throws Exception {
        Course course = course("pagination");
        Team team = team(course, "Paged Team");
        Student currentStudent = student("page-current");
        membership(team, currentStudent, RoleInTeam.MEMBER);
        membership(team, student("page-one"), RoleInTeam.MEMBER);
        membership(team, student("page-two"), RoleInTeam.MEMBER);

        mockMvc.perform(get(PATH, course.getId())
                        .param("page", "1")
                        .param("size", "1")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, currentStudent.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.content.length()").value(1))
                .andExpect(jsonPath("$.members.number").value(1))
                .andExpect(jsonPath("$.members.size").value(1))
                .andExpect(jsonPath("$.members.totalElements").value(3));

        mockMvc.perform(get(PATH, course.getId())
                        .param("size", "101")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, currentStudent.getId()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH, course.getId())
                        .param("page", "-1")
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, currentStudent.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openApiDocumentsSelfScopedSessionEndpointAndConflictResponses() throws Exception {
        String operation = "$.paths['/api/me/courses/{courseId}/team/members'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"))
                .andExpect(jsonPath(operation).exists())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'page')]").isNotEmpty())
                .andExpect(jsonPath(operation + ".parameters[?(@.name == 'size')]").isNotEmpty())
                .andExpect(jsonPath(operation + ".responses['200']").exists())
                .andExpect(jsonPath(operation + ".responses['401']").exists())
                .andExpect(jsonPath(operation + ".responses['403']").exists())
                .andExpect(jsonPath(operation + ".responses['404']").exists())
                .andExpect(jsonPath(operation + ".responses['409']").exists());
    }

    private org.springframework.test.web.servlet.ResultActions request(
            ApplicationRole role,
            UUID profileId,
            Course course
    ) throws Exception {
        return mockMvc.perform(get(PATH, course.getId())
                .with(authentication(authenticationFor(role, profileId))));
    }

    private Course course(String label) {
        Lecturer instructor = lecturer(label);
        return courseRepository.save(Course.builder()
                .courseCode("COURSE-" + label + "-" + UUID.randomUUID())
                .name("Course " + label)
                .instructor(instructor)
                .build());
    }

    private Lecturer lecturer(String label) {
        String suffix = UUID.randomUUID().toString();
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + label + "-" + suffix)
                .email("lecturer-" + label + "-" + suffix + "@example.test")
                .fullName("Lecturer " + label)
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

    private Team team(Course course, String name) {
        return teamRepository.save(Team.builder().course(course).name(name).build());
    }

    private void membership(Team team, Student student, RoleInTeam role) {
        teamMemberRepository.save(TeamMember.builder()
                .team(team)
                .student(student)
                .roleInTeam(role)
                .build());
    }

    private void project(Team team, Course course, String name) {
        Project project = projectRepository.save(Project.builder().course(course).name(name).build());
        team.setProject(project);
        teamRepository.save(team);
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
}
