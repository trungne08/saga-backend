package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SelfProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LecturerRepository lecturerRepository;

    @Test
    void activeStudentCanUpdateOnlyOwnFullNameAndReadCanonicalStudentCode() throws Exception {
        Student student = student("student-update", AccountStatus.ACTIVE);

        mockMvc.perform(patch("/api/auth/me")
                        .with(authentication(authenticationFor(student)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"  Student Edited  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Student Edited"))
                .andExpect(jsonPath("$.studentCode").value(student.getStudentCode()))
                .andExpect(jsonPath("$.email").value(student.getEmail()))
                .andExpect(jsonPath("$.applicationRole").value("STUDENT"));

        Student saved = studentRepository.findById(student.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Student Edited", saved.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals(AccountStatus.ACTIVE, saved.getAccountStatus());
        org.junit.jupiter.api.Assertions.assertEquals(student.getCognitoSub(), saved.getCognitoSub());
        org.junit.jupiter.api.Assertions.assertEquals(student.getEmail(), saved.getEmail());

        mockMvc.perform(get("/api/auth/me").with(authentication(authenticationFor(student))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Student Edited"))
                .andExpect(jsonPath("$.studentCode").value(student.getStudentCode()));
    }

    @Test
    void activeLecturerCanUpdateOwnProfileAndStudentCodeIsNull() throws Exception {
        Lecturer lecturer = lecturer("lecturer-update", AccountStatus.ACTIVE);

        mockMvc.perform(patch("/api/auth/me")
                        .with(authentication(authenticationFor(lecturer)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Lecturer Edited\",\"avatarUrl\":\"https://cdn.example.test/lecturer.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Lecturer Edited"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.test/lecturer.png"))
                .andExpect(jsonPath("$.studentCode").doesNotExist());

        Lecturer saved = lecturerRepository.findById(lecturer.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Lecturer Edited", saved.getFullName());
        org.junit.jupiter.api.Assertions.assertEquals("https://cdn.example.test/lecturer.png", saved.getAvatarUrl());
        org.junit.jupiter.api.Assertions.assertEquals(AccountStatus.ACTIVE, saved.getAccountStatus());
    }

    @Test
    void sparseAvatarUpdateKeepsFullNameAndSupportsExplicitNullClear() throws Exception {
        Student student = student("avatar-sparse", AccountStatus.ACTIVE);
        student.setFullName("Keep Name");
        student.setAvatarUrl("https://cdn.example.test/original.png");
        studentRepository.saveAndFlush(student);

        mockMvc.perform(patch("/api/auth/me")
                        .with(authentication(authenticationFor(student))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":\"https://cdn.example.test/new.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Keep Name"))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.example.test/new.png"));

        mockMvc.perform(patch("/api/auth/me")
                        .with(authentication(authenticationFor(student))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarUrl\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());
        org.junit.jupiter.api.Assertions.assertNull(studentRepository.findById(student.getId()).orElseThrow().getAvatarUrl());
    }

    @Test
    void rejectsInvalidProfileFieldsAndForbiddenIdentityFields() throws Exception {
        Student student = student("invalid-profile", AccountStatus.ACTIVE);
        Authentication authentication = authenticationFor(student);
        for (String avatar : List.of("javascript:alert(1)", "/relative.png", "https:///missing-host.png")) {
            mockMvc.perform(patch("/api/auth/me").with(authentication(authentication)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"avatarUrl\":\"" + avatar + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(patch("/api/auth/me").with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/auth/me").with(authentication(authentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@test\",\"studentCode\":\"OTHER\",\"applicationRole\":\"ADMIN\",\"accountStatus\":\"SUSPENDED\",\"cognitoSub\":\"other\",\"localProfileId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest());

        Student unchanged = studentRepository.findById(student.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(student.getEmail(), unchanged.getEmail());
        org.junit.jupiter.api.Assertions.assertEquals(student.getStudentCode(), unchanged.getStudentCode());
        org.junit.jupiter.api.Assertions.assertEquals(AccountStatus.ACTIVE, unchanged.getAccountStatus());
    }

    @Test
    void patchRequiresActiveSessionAndCsrfAndDoesNotAllowAdmin() throws Exception {
        Student student = student("csrf-student", AccountStatus.ACTIVE);
        mockMvc.perform(patch("/api/auth/me").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/auth/me").with(authentication(authenticationFor(student)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/auth/me").with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID(), null)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledAccountIsRejectedBeforeSelfPatchAndAuthMeAndSessionIsInvalidated() throws Exception {
        Student student = student("disabled-profile", AccountStatus.INACTIVE);
        MockHttpSession session = session(authenticationFor(student));
        mockMvc.perform(patch("/api/auth/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Blocked\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(session.isInvalid());

        MockHttpSession meSession = session(authenticationFor(student));
        mockMvc.perform(get("/api/auth/me").session(meSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
        assertTrue(meSession.isInvalid());
    }

    private Student student(String suffix, AccountStatus status) {
        return studentRepository.saveAndFlush(Student.builder()
                .cognitoSub(suffix + "-sub").studentCode("SE" + Math.abs(suffix.hashCode() % 1000000))
                .email(suffix + "@example.test").fullName("Student " + suffix)
                .accountStatus(status).build());
    }

    private Lecturer lecturer(String suffix, AccountStatus status) {
        return lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub(suffix + "-sub")
                .email(suffix + "@example.test").fullName("Lecturer " + suffix).accountStatus(status).build());
    }

    private Authentication authenticationFor(Student student) {
        return authenticationFor(ApplicationRole.STUDENT, student.getId(), student.getAccountStatus());
    }

    private Authentication authenticationFor(Lecturer lecturer) {
        return authenticationFor(ApplicationRole.LECTURER, lecturer.getId(), lecturer.getAccountStatus());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID id, AccountStatus status) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-subject", role.name().toLowerCase()
                + "@example.test", role.name(), role, id, status);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private MockHttpSession session(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
