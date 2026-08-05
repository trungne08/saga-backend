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
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Subject;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.SubjectRepository;
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
class SubjectUpdateSoftDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Test
    void adminUpdatesSubjectAndMayKeepItsOwnCode() throws Exception {
        Subject subject = saveSubject(uniqueCode("OLD"), "Old name");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);

        mockMvc.perform(put("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson("  " + subject.getSubjectCode() + "  ", "  Updated name  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(subject.getId().toString()))
                .andExpect(jsonPath("$.subjectCode").value(subject.getSubjectCode()))
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.deletedAt").doesNotExist());

        Subject persisted = subjectRepository.findById(subject.getId()).orElseThrow();
        assertEquals("Updated name", persisted.getName());
        assertEquals(subject.getSubjectCode(), persisted.getSubjectCode());
    }

    @Test
    void updateRejectsDuplicateCodeWithConflict() throws Exception {
        Subject subject = saveSubject(uniqueCode("ONE"), "One");
        Subject other = saveSubject(uniqueCode("TWO"), "Two");

        performAdminPut(subject.getId(), subjectJson(other.getSubjectCode(), "Changed"))
                .andExpect(status().isConflict());

        assertEquals(subject.getSubjectCode(), subjectRepository.findById(subject.getId()).orElseThrow().getSubjectCode());
    }

    @Test
    void updateReturnsNotFoundForMissingOrDeletedSubject() throws Exception {
        performAdminPut(UUID.randomUUID(), subjectJson(uniqueCode("MISS"), "Missing"))
                .andExpect(status().isNotFound());

        Subject deleted = saveSubject(uniqueCode("DEL"), "Deleted");
        deleted.setDeletedAt(LocalDateTime.now());
        subjectRepository.saveAndFlush(deleted);

        performAdminPut(deleted.getId(), subjectJson(deleted.getSubjectCode(), "Changed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateValidatesRequestAndEnforcesRoleAndCsrf() throws Exception {
        Subject subject = saveSubject(uniqueCode("SEC"), "Secured");
        String validBody = subjectJson(subject.getSubjectCode(), "Changed");
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(put("/api/v1/subjects/{id}", subject.getId())
                        .cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN", anonymousCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        performPutAs(subject.getId(), validBody, ApplicationRole.LECTURER)
                .andExpect(status().isForbidden());
        performPutAs(subject.getId(), validBody, ApplicationRole.STUDENT)
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        performAdminPut(subject.getId(), subjectJson(" ", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminSoftDeletesWithoutRemovingRowAndReadsFilterItOut() throws Exception {
        Subject subject = saveSubject(uniqueCode("SOFT"), "Searchable soft delete");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);

        mockMvc.perform(delete("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());

        Subject storedRow = subjectRepository.findById(subject.getId()).orElseThrow();
        assertNotNull(storedRow.getDeletedAt());

        mockMvc.perform(get("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(admin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/subjects")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(subject.getId().toString()))));

        mockMvc.perform(get("/api/v1/subjects")
                        .param("keyword", "Searchable")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(subject.getId().toString()))));

        mockMvc.perform(delete("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRequiresAdminAndCsrf() throws Exception {
        Subject subject = saveSubject(uniqueCode("DELETE-SEC"), "Delete secured");
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(delete("/api/v1/subjects/{id}", subject.getId())
                        .cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN", anonymousCsrf.getValue()))
                .andExpect(status().isUnauthorized());

        performDeleteAs(subject.getId(), ApplicationRole.LECTURER)
                .andExpect(status().isForbidden());
        performDeleteAs(subject.getId(), ApplicationRole.STUDENT)
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/subjects/{id}", subject.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());

        assertTrue(subjectRepository.findByIdAndDeletedAtIsNull(subject.getId()).isPresent());
    }

    @Test
    void deleteRejectsSubjectUsedByCourseWithoutDeletingDependency() throws Exception {
        Subject subject = saveSubject(uniqueCode("COURSE"), "Course subject");
        Course course = Course.builder()
                .subject(subject)
                .courseCode(uniqueCode("COURSE-CODE"))
                .name("Dependent course")
                .build();
        course = courseRepository.saveAndFlush(course);
        long courseCount = courseRepository.count();

        performAdminDelete(subject.getId())
                .andExpect(status().isConflict());

        assertTrue(subjectRepository.findByIdAndDeletedAtIsNull(subject.getId()).isPresent());
        assertTrue(courseRepository.findById(course.getId()).isPresent());
        assertEquals(courseCount, courseRepository.count());
    }

    @Test
    void createDoesNotReuseCodeOfSoftDeletedSubject() throws Exception {
        Subject deleted = saveSubject(uniqueCode("REUSE"), "Deleted code");
        deleted.setDeletedAt(LocalDateTime.now());
        subjectRepository.saveAndFlush(deleted);
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);

        mockMvc.perform(post("/api/v1/subjects")
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subjectJson(deleted.getSubjectCode(), "Replacement")))
                .andExpect(status().isConflict());

        assertEquals(1, subjectRepository.findAll().stream()
                .filter(subject -> deleted.getSubjectCode().equals(subject.getSubjectCode()))
                .count());
    }

    @Test
    void courseCreateKeepsExistingBehaviorForActiveSubject() throws Exception {
        Subject subject = saveSubject(uniqueCode("ACTIVE-COURSE"), "Active course subject");
        CourseReferences references = saveCourseReferences();
        String courseCode = uniqueCode("ACTIVE-COURSE-CODE");

        performAdminCourseCreate(courseJson(courseCode, subject.getId(), references))
                .andExpect(status().isCreated());

        Course persisted = courseRepository.findByCourseCode(courseCode).orElseThrow();
        assertEquals(subject.getId(), persisted.getSubject().getId());
        assertTrue(persisted.getSubject().getDeletedAt() == null);
    }

    @Test
    void courseCreateReturnsNotFoundForMissingSubject() throws Exception {
        CourseReferences references = saveCourseReferences();
        String courseCode = uniqueCode("MISSING-SUBJECT-COURSE");

        performAdminCourseCreate(courseJson(courseCode, UUID.randomUUID(), references))
                .andExpect(status().isNotFound());

        assertTrue(courseRepository.findByCourseCode(courseCode).isEmpty());
    }

    @Test
    void courseCreateReturnsNotFoundForSoftDeletedSubject() throws Exception {
        Subject deleted = saveSubject(uniqueCode("DELETED-COURSE"), "Deleted course subject");
        deleted.setDeletedAt(LocalDateTime.now());
        subjectRepository.saveAndFlush(deleted);
        CourseReferences references = saveCourseReferences();
        String courseCode = uniqueCode("DELETED-SUBJECT-COURSE");

        performAdminCourseCreate(courseJson(courseCode, deleted.getId(), references))
                .andExpect(status().isNotFound());

        assertTrue(courseRepository.findByCourseCode(courseCode).isEmpty());
        assertNotNull(subjectRepository.findById(deleted.getId()).orElseThrow().getDeletedAt());
    }

    private Subject saveSubject(String code, String name) {
        return subjectRepository.saveAndFlush(Subject.builder()
                .subjectCode(code)
                .name(name)
                .build());
    }

    private CourseReferences saveCourseReferences() {
        com.saga.be.entity.Class clazz = classRepository.saveAndFlush(
                com.saga.be.entity.Class.builder()
                        .classCode(uniqueCode("CLASS"))
                        .name("Course class")
                        .build()
        );
        Semester semester = semesterRepository.saveAndFlush(Semester.builder()
                .code(uniqueCode("SEMESTER"))
                .name("Course semester")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build());
        Lecturer lecturer = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub(uniqueCode("LECTURER-SUB"))
                .email(uniqueCode("lecturer") + "@example.test")
                .fullName("Course lecturer")
                .build());
        return new CourseReferences(clazz.getId(), semester.getId(), lecturer.getId());
    }

    private org.springframework.test.web.servlet.ResultActions performAdminCourseCreate(String body)
            throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);
        return mockMvc.perform(post("/api/v1/courses")
                .with(authentication(admin))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performAdminPut(UUID id, String body)
            throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);
        return mockMvc.perform(put("/api/v1/subjects/{id}", id)
                .with(authentication(admin))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performPutAs(
            UUID id,
            String body,
            ApplicationRole role
    ) throws Exception {
        Authentication authentication = authenticationFor(role);
        Cookie csrfCookie = csrfCookie(authentication);
        return mockMvc.perform(put("/api/v1/subjects/{id}", id)
                .with(authentication(authentication))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performAdminDelete(UUID id)
            throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);
        return mockMvc.perform(delete("/api/v1/subjects/{id}", id)
                .with(authentication(admin))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()));
    }

    private org.springframework.test.web.servlet.ResultActions performDeleteAs(UUID id, ApplicationRole role)
            throws Exception {
        Authentication authentication = authenticationFor(role);
        Cookie csrfCookie = csrfCookie(authentication);
        return mockMvc.perform(delete("/api/v1/subjects/{id}", id)
                .with(authentication(authentication))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()));
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (csrfCookie == null) {
            throw new AssertionError("GET /api/auth/csrf did not create XSRF-TOKEN");
        }
        return csrfCookie;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject",
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                UUID.randomUUID(),
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private String subjectJson(String code, String name) {
        return """
                {
                  "subjectCode": "%s",
                  "name": "%s"
                }
                """.formatted(code, name);
    }

    private String courseJson(String courseCode, UUID subjectId, CourseReferences references) {
        return """
                {
                  "courseCode": "%s",
                  "name": "Course cross-reference test",
                  "subjectId": "%s",
                  "classId": "%s",
                  "semesterId": "%s",
                  "instructorId": "%s"
                }
                """.formatted(
                courseCode,
                subjectId,
                references.classId(),
                references.semesterId(),
                references.lecturerId()
        );
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record CourseReferences(UUID classId, UUID semesterId, UUID lecturerId) {
    }
}
