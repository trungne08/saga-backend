package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class AdminAccountStatusIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminRepository adminRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;

    @Test
    void lecturerDefaultsActiveAndAdminReadFiltersBothSupportedProfileTypes() throws Exception {
        Lecturer lecturer = lecturer("lecturer-default", AccountStatus.ACTIVE);
        Student student = student("student-filter", AccountStatus.SUSPENDED);

        assertEquals(AccountStatus.ACTIVE, lecturer.getAccountStatus());
        mockMvc.perform(get("/api/admin/users").param("accountStatus", "ACTIVE")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].localProfileId").value(lecturer.getId().toString()))
                .andExpect(jsonPath("$.content[0].accountStatus").value("ACTIVE"));
        mockMvc.perform(get("/api/admin/users").param("role", "STUDENT").param("accountStatus", "SUSPENDED")
                        .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].localProfileId").value(student.getId().toString()));
    }

    @Test
    void adminUpdatesStudentStatusesAndRejectsPending() throws Exception {
        Student student = student("student-status", AccountStatus.ACTIVE);
        patchAsAdmin(student.getId(), "SUSPENDED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
        assertEquals(AccountStatus.SUSPENDED, studentRepository.findById(student.getId()).orElseThrow().getAccountStatus());
        patchAsAdmin(student.getId(), "ACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        patchAsAdmin(student.getId(), "INACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
        patchAsAdmin(student.getId(), "INACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
        patchAsAdmin(student.getId(), "PENDING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_STATUS_PENDING_NOT_ALLOWED"));
    }

    @Test
    void adminUpdatesLecturerStatusesWithoutChangingCourseOwnership() throws Exception {
        Lecturer lecturer = lecturer("lecturer-status", AccountStatus.ACTIVE);
        Course course = courseRepository.saveAndFlush(Course.builder().courseCode("STATUS-COURSE")
                .name("Status Course").instructor(lecturer).build());

        patchAsAdmin(lecturer.getId(), "SUSPENDED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
        patchAsAdmin(lecturer.getId(), "ACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        patchAsAdmin(lecturer.getId(), "INACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
        assertEquals(lecturer.getId(), courseRepository.findById(course.getId()).orElseThrow().getInstructor().getId());
    }

    @Test
    void studentStatusChangeDoesNotMutateMembershipAndAdminAndUnknownTargetsFailSafely() throws Exception {
        Lecturer lecturer = lecturer("membership-lecturer", AccountStatus.ACTIVE);
        Course course = courseRepository.saveAndFlush(Course.builder().courseCode("MEMBERSHIP-COURSE")
                .name("Membership Course").instructor(lecturer).build());
        Student student = student("membership-student", AccountStatus.ACTIVE);
        Team team = teamRepository.saveAndFlush(Team.builder().course(course).name("Status Team").build());
        TeamMember membership = teamMemberRepository.saveAndFlush(TeamMember.builder().team(team).student(student)
                .roleInTeam(RoleInTeam.MEMBER).build());

        patchAsAdmin(student.getId(), "SUSPENDED").andExpect(status().isOk());
        assertNotNull(teamMemberRepository.findById(membership.getId()).orElseThrow());

        Admin admin = adminRepository.saveAndFlush(Admin.builder().cognitoSub("target-admin").email("target-admin@test")
                .fullName("Target Admin").build());
        patchAsAdmin(admin.getId(), "ACTIVE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ACCOUNT_STATUS_TARGET_UNSUPPORTED"));
        patchAsAdmin(UUID.randomUUID(), "ACTIVE").andExpect(status().isNotFound());
    }

    @Test
    void patchRequiresAdminAndCsrf() throws Exception {
        Student target = student("security-target", AccountStatus.ACTIVE);
        Cookie anonymousCsrf = new Cookie("XSRF-TOKEN", "anonymous-csrf-token");
        mockMvc.perform(patch("/api/admin/users/{id}/status", target.getId()).cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN", anonymousCsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(statusJson("SUSPENDED")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/users/{id}/status", target.getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, UUID.randomUUID(), AccountStatus.ACTIVE)))
                        .contentType(MediaType.APPLICATION_JSON).content(statusJson("SUSPENDED")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/{id}/status", target.getId())
                        .with(authentication(authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE)))
                        .contentType(MediaType.APPLICATION_JSON).content(statusJson("SUSPENDED")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/{id}/status", target.getId()).with(authentication(adminAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON).content(statusJson("SUSPENDED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountsInvalidateTheCurrentSessionAndCannotBootstrapAgain() throws Exception {
        Student student = student("session-student", AccountStatus.ACTIVE);
        MockHttpSession studentSession = session(authenticationFor(ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE));
        mockMvc.perform(get("/api/v1/subjects").session(studentSession)).andExpect(status().isOk());
        patchAsAdmin(student.getId(), "INACTIVE").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subjects").session(studentSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(studentSession.isInvalid());

        MockHttpSession studentMeSession = session(authenticationFor(
                ApplicationRole.STUDENT, student.getId(), AccountStatus.ACTIVE
        ));
        mockMvc.perform(get("/api/auth/me").session(studentMeSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(studentMeSession.isInvalid());

        mockMvc.perform(get("/api/v1/subjects")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"));
        patchAsAdmin(student.getId(), "ACTIVE").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subjects")).andExpect(status().isUnauthorized());

        Lecturer lecturer = lecturer("session-lecturer", AccountStatus.ACTIVE);
        MockHttpSession lecturerSession = session(authenticationFor(ApplicationRole.LECTURER, lecturer.getId(), AccountStatus.ACTIVE));
        mockMvc.perform(get("/api/v1/subjects").session(lecturerSession)).andExpect(status().isOk());
        patchAsAdmin(lecturer.getId(), "SUSPENDED").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subjects").session(lecturerSession)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(lecturerSession.isInvalid());
    }

    private org.springframework.test.web.servlet.ResultActions patchAsAdmin(UUID targetId, String statusValue) throws Exception {
        Authentication admin = adminAuthentication();
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(patch("/api/admin/users/{id}/status", targetId).with(authentication(admin))
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(statusJson(statusValue)));
    }

    private org.springframework.test.web.servlet.ResultActions logoutWithCsrf(MockHttpSession session) throws Exception {
        org.springframework.test.web.servlet.MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        Cookie csrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        return mockMvc.perform(post("/api/auth/logout").session(session).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/auth/csrf").with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        return csrf;
    }

    private MockHttpSession session(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private Student student(String suffix, AccountStatus status) {
        return studentRepository.saveAndFlush(Student.builder().cognitoSub(suffix + "-sub").studentCode("ST-" + suffix)
                .email(suffix + "@test").fullName(suffix).accountStatus(status).build());
    }

    private Lecturer lecturer(String suffix, AccountStatus status) {
        return lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub(suffix + "-sub")
                .email(suffix + "@test").fullName(suffix).accountStatus(status).build());
    }

    private Authentication adminAuthentication() {
        return authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID(), null);
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId, AccountStatus status) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-sub", role.name().toLowerCase()
                + "@test", role.name(), role, localProfileId, status);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String statusJson(String status) {
        return "{\"status\":\"" + status + "\"}";
    }
}
