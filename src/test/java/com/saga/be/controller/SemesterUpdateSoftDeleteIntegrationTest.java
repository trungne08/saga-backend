package com.saga.be.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Semester;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@Transactional
class SemesterUpdateSoftDeleteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CourseRepository courseRepository;

    @Test
    void adminUpdatesAllSemesterFieldsWithoutMutatingReferencingCourse() throws Exception {
        Semester semester = semester("OLD", "Old semester");
        Course course = courseRepository.saveAndFlush(Course.builder().semester(semester)
                .courseCode(unique("COURSE")).name("Retained course").build());

        performAdminPut(semester.getId(), semesterJson("UPDATED", "Updated semester",
                "2026-01-01T00:00:00", "2026-05-01T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(semester.getId().toString()))
                .andExpect(jsonPath("$.code").value("UPDATED"))
                .andExpect(jsonPath("$.name").value("Updated semester"));

        Semester persisted = semesterRepository.findById(semester.getId()).orElseThrow();
        assertEquals("UPDATED", persisted.getCode());
        assertEquals(semester.getId(), courseRepository.findById(course.getId()).orElseThrow()
                .getSemester().getId());
    }

    @Test
    void updateValidatesSecurityMissingDuplicateAndDates() throws Exception {
        Semester semester = semester("ONE", "One");
        Semester other = semester("TWO", "Two");
        String valid = semesterJson("ONE", "Changed", "2026-01-01T00:00:00", "2026-05-01T00:00:00");
        Cookie csrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(put("/api/v1/semesters/{id}", semester.getId()).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        performPutAs(semester.getId(), valid, ApplicationRole.LECTURER).andExpect(status().isForbidden());
        performPutAs(semester.getId(), valid, ApplicationRole.STUDENT).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/semesters/{id}", semester.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isForbidden());
        performAdminPut(UUID.randomUUID(), valid).andExpect(status().isNotFound());
        performAdminPut(semester.getId(), semesterJson(other.getCode(), "Changed",
                "2026-01-01T00:00:00", "2026-05-01T00:00:00")).andExpect(status().isConflict());
        performAdminPut(semester.getId(), semesterJson("VALID", "Changed",
                "2026-05-02T00:00:00", "2026-05-01T00:00:00")).andExpect(status().isBadRequest());
    }

    @Test
    void adminSoftDeletesUnusedSemesterAndActiveReadsExcludeTombstone() throws Exception {
        Semester semester = semester("DELETE", "Searchable semester");

        performAdminDelete(semester.getId()).andExpect(status().isNoContent());

        Semester stored = semesterRepository.findById(semester.getId()).orElseThrow();
        assertNotNull(stored.getDeletedAt());
        mockMvc.perform(get("/api/v1/semesters/{id}", semester.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/semesters").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", not(hasItem(semester.getId().toString()))));
        mockMvc.perform(get("/api/v1/semesters").param("keyword", "Searchable")
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", not(hasItem(semester.getId().toString()))));
        performAdminDelete(semester.getId()).andExpect(status().isNotFound());
    }

    @Test
    void deleteFailsClosedForReferencedCourseAndDoesNotCascade() throws Exception {
        Semester semester = semester("REFERENCED", "Referenced semester");
        Course course = courseRepository.saveAndFlush(Course.builder().semester(semester)
                .courseCode(unique("COURSE")).name("Dependent course").build());
        long courseCount = courseRepository.count();

        performAdminDelete(semester.getId()).andExpect(status().isConflict());

        assertTrue(semesterRepository.findByIdAndDeletedAtIsNull(semester.getId()).isPresent());
        assertTrue(courseRepository.findById(course.getId()).isPresent());
        assertEquals(courseCount, courseRepository.count());
    }

    @Test
    void deleteSecurityAndTombstonedCodeUniquenessFollowExistingPolicy() throws Exception {
        Semester semester = semester("REUSE", "Code policy");
        Cookie csrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));
        mockMvc.perform(delete("/api/v1/semesters/{id}", semester.getId()).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        performDeleteAs(semester.getId(), ApplicationRole.LECTURER).andExpect(status().isForbidden());
        performDeleteAs(semester.getId(), ApplicationRole.STUDENT).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/semesters/{id}", semester.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());

        performAdminDelete(semester.getId()).andExpect(status().isNoContent());
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie adminCsrf = csrfCookie(admin);
        mockMvc.perform(post("/api/v1/semesters").with(authentication(admin)).cookie(adminCsrf)
                        .header("X-XSRF-TOKEN", adminCsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content(semesterJson("REUSE", "Replacement", "2026-01-01T00:00:00", "2026-05-01T00:00:00")))
                .andExpect(status().isConflict());
        assertEquals(1, semesterRepository.findAll().stream().filter(item -> "REUSE".equals(item.getCode())).count());
    }

    private Semester semester(String code, String name) {
        return semesterRepository.saveAndFlush(Semester.builder().code(code).name(name)
                .startDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .endDate(LocalDateTime.of(2026, 5, 1, 0, 0)).build());
    }

    private org.springframework.test.web.servlet.ResultActions performAdminPut(UUID id, String body) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(put("/api/v1/semesters/{id}", id).with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performPutAs(UUID id, String body, ApplicationRole role) throws Exception {
        Authentication user = authenticationFor(role);
        Cookie csrf = csrfCookie(user);
        return mockMvc.perform(put("/api/v1/semesters/{id}", id).with(authentication(user)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performAdminDelete(UUID id) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(delete("/api/v1/semesters/{id}", id).with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private org.springframework.test.web.servlet.ResultActions performDeleteAs(UUID id, ApplicationRole role) throws Exception {
        Authentication user = authenticationFor(role);
        Cookie csrf = csrfCookie(user);
        return mockMvc.perform(delete("/api/v1/semesters/{id}", id).with(authentication(user)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (cookie == null) throw new AssertionError("Missing XSRF-TOKEN");
        return cookie;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-semester",
                role.name().toLowerCase() + "@example.test", role.name() + " User", role,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String semesterJson(String code, String name, String start, String end) {
        return """
                {"code":"%s","name":"%s","startDate":"%s","endDate":"%s"}
                """.formatted(code, name, start, end);
    }

    private String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
