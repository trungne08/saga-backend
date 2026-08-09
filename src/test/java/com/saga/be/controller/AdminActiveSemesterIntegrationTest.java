package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Semester;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.ActiveSemesterSettingRepository;
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
class AdminActiveSemesterIntegrationTest {

    private static final String PATH = "/api/admin/settings/active-semester";

    @Autowired private MockMvc mockMvc;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private ActiveSemesterSettingRepository activeSemesterSettingRepository;

    @Test
    void adminSetsReadsRepeatsAndChangesExplicitSemesterWithoutMutatingCourseOrSemester() throws Exception {
        Semester first = semester("FIRST");
        Semester second = semester("SECOND");
        Course course = courseRepository.saveAndFlush(Course.builder().semester(first)
                .courseCode("COURSE-" + UUID.randomUUID()).name("Existing course").build());
        LocalDateTime originalStart = first.getStartDate();
        LocalDateTime originalEnd = first.getEndDate();

        putAsAdmin(first.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(first.getId().toString()))
                .andExpect(jsonPath("$.semesterCode").value("FIRST"));
        putAsAdmin(first.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(first.getId().toString()));
        assertEquals(1, activeSemesterSettingRepository.count());
        assertEquals("FIRST", semesterRepository.findById(first.getId()).orElseThrow().getCode());
        assertEquals(originalStart, semesterRepository.findById(first.getId()).orElseThrow().getStartDate());
        assertEquals(originalEnd, semesterRepository.findById(first.getId()).orElseThrow().getEndDate());
        assertEquals(first.getId(), courseRepository.findById(course.getId()).orElseThrow().getSemester().getId());
        assertEquals(1, courseRepository.count());

        putAsAdmin(second.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(second.getId().toString()));
        mockMvc.perform(get(PATH).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.semesterId").value(second.getId().toString()));
        assertEquals(1, activeSemesterSettingRepository.count());
        assertEquals(first.getId(), courseRepository.findById(course.getId()).orElseThrow().getSemester().getId());
    }

    @Test
    void rejectsMissingAndTombstonedSemesterAndFailsClosedOnActiveSemesterDelete() throws Exception {
        putAsAdmin(UUID.randomUUID()).andExpect(status().isNotFound());
        Semester tombstoned = semester("TOMBSTONED");
        tombstoned.setDeletedAt(LocalDateTime.now());
        semesterRepository.saveAndFlush(tombstoned);
        putAsAdmin(tombstoned.getId()).andExpect(status().isNotFound());

        Semester active = semester("ACTIVE");
        putAsAdmin(active.getId()).andExpect(status().isOk());
        deleteAsAdmin(active.getId()).andExpect(status().isConflict());
        assertNotNull(semesterRepository.findByIdAndDeletedAtIsNull(active.getId()).orElseThrow());
        assertEquals(active.getId(), activeSemesterSettingRepository.findById((byte) 1).orElseThrow()
                .getSemester().getId());
    }

    @Test
    void endpointRequiresAdminSessionAndCsrfForMutation() throws Exception {
        Semester semester = semester("SECURITY");
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).with(authentication(authenticationFor(ApplicationRole.STUDENT))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).cookie(anonymousCsrf).header("X-XSRF-TOKEN", anonymousCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body(semester.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).with(authentication(authenticationFor(ApplicationRole.LECTURER)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(semester.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).with(authentication(authenticationFor(ApplicationRole.STUDENT)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(semester.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(body(semester.getId())))
                .andExpect(status().isForbidden());
    }

    private Semester semester(String code) {
        return semesterRepository.saveAndFlush(Semester.builder().code(code).name(code + " semester")
                .startDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .endDate(LocalDateTime.of(2026, 5, 1, 0, 0)).build());
    }

    private org.springframework.test.web.servlet.ResultActions putAsAdmin(UUID semesterId) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(put(PATH).with(authentication(admin)).cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content(body(semesterId)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteAsAdmin(UUID semesterId) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(delete("/api/v1/semesters/{id}", semesterId).with(authentication(admin)).cookie(csrf)
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
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-active-semester",
                role.name().toLowerCase() + "@example.test", role.name() + " User", role,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private String body(UUID semesterId) {
        return "{\"semesterId\":\"" + semesterId + "\"}";
    }
}
