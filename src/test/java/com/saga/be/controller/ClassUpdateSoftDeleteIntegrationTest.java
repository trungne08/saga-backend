package com.saga.be.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class ClassUpdateSoftDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Test
    void adminUpdatesClassAndMayKeepItsOwnCode() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("OLD"), "Old name");

        performAdminPut(
                clazz.getId(),
                classJson("  " + clazz.getClassCode() + "  ", "  Updated name  ")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clazz.getId().toString()))
                .andExpect(jsonPath("$.classCode").value(clazz.getClassCode()))
                .andExpect(jsonPath("$.name").value("Updated name"))
                .andExpect(jsonPath("$.deletedAt").doesNotExist());

        com.saga.be.entity.Class persisted = classRepository.findById(clazz.getId()).orElseThrow();
        assertEquals(clazz.getClassCode(), persisted.getClassCode());
        assertEquals("Updated name", persisted.getName());
    }

    @Test
    void updateRejectsDuplicateCodeWithConflict() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("ONE"), "One");
        com.saga.be.entity.Class other = saveClass(uniqueCode("TWO"), "Two");

        performAdminPut(clazz.getId(), classJson(other.getClassCode(), "Changed"))
                .andExpect(status().isConflict());

        assertEquals(
                clazz.getClassCode(),
                classRepository.findById(clazz.getId()).orElseThrow().getClassCode()
        );
    }

    @Test
    void updateReturnsNotFoundForMissingOrDeletedClass() throws Exception {
        performAdminPut(UUID.randomUUID(), classJson(uniqueCode("MISS"), "Missing"))
                .andExpect(status().isNotFound());

        com.saga.be.entity.Class deleted = saveClass(uniqueCode("DEL"), "Deleted");
        deleted.setDeletedAt(LocalDateTime.now());
        classRepository.saveAndFlush(deleted);

        performAdminPut(deleted.getId(), classJson(deleted.getClassCode(), "Changed"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateValidatesRequestAndEnforcesRoleAndCsrf() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("SEC"), "Secured");
        String validBody = classJson(clazz.getClassCode(), "Changed");
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(put("/api/v1/classes/{id}", clazz.getId())
                        .cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN", anonymousCsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isUnauthorized());

        performPutAs(clazz.getId(), validBody, ApplicationRole.LECTURER)
                .andExpect(status().isForbidden());
        performPutAs(clazz.getId(), validBody, ApplicationRole.STUDENT)
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/classes/{id}", clazz.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        performAdminPut(clazz.getId(), classJson(" ", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminSoftDeletesWithoutRemovingRowAndReadsFilterItOut() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("SOFT"), "Searchable soft delete");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);

        performAdminDelete(clazz.getId())
                .andExpect(status().isNoContent());

        com.saga.be.entity.Class storedRow = classRepository.findById(clazz.getId()).orElseThrow();
        assertNotNull(storedRow.getDeletedAt());

        mockMvc.perform(get("/api/v1/classes/{id}", clazz.getId())
                        .with(authentication(admin)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/classes")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(clazz.getId().toString()))));

        mockMvc.perform(get("/api/v1/classes")
                        .param("keyword", "Searchable")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", not(hasItem(clazz.getId().toString()))));

        performAdminDelete(clazz.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRequiresAdminAndCsrf() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("DELETE-SEC"), "Delete secured");
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(delete("/api/v1/classes/{id}", clazz.getId())
                        .cookie(anonymousCsrf)
                        .header("X-XSRF-TOKEN", anonymousCsrf.getValue()))
                .andExpect(status().isUnauthorized());

        performDeleteAs(clazz.getId(), ApplicationRole.LECTURER)
                .andExpect(status().isForbidden());
        performDeleteAs(clazz.getId(), ApplicationRole.STUDENT)
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/classes/{id}", clazz.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());

        assertTrue(classRepository.findByIdAndDeletedAtIsNull(clazz.getId()).isPresent());
    }

    @Test
    void deleteRejectsClassUsedByCourseWithoutDeletingDependency() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("COURSE"), "Course class");
        Course course = courseRepository.saveAndFlush(Course.builder()
                .clazz(clazz)
                .courseCode(uniqueCode("COURSE-CODE"))
                .name("Dependent course")
                .build());
        long courseCount = courseRepository.count();

        performAdminDelete(clazz.getId())
                .andExpect(status().isConflict());

        assertTrue(classRepository.findByIdAndDeletedAtIsNull(clazz.getId()).isPresent());
        assertTrue(courseRepository.findById(course.getId()).isPresent());
        assertEquals(courseCount, courseRepository.count());
    }

    @Test
    void createDoesNotReuseCodeOfSoftDeletedClass() throws Exception {
        com.saga.be.entity.Class deleted = saveClass(uniqueCode("REUSE"), "Deleted code");
        deleted.setDeletedAt(LocalDateTime.now());
        classRepository.saveAndFlush(deleted);
        Authentication admin = authenticationFor(ApplicationRole.ADMIN);
        Cookie csrfCookie = csrfCookie(admin);

        mockMvc.perform(post("/api/v1/classes")
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classJson(deleted.getClassCode(), "Replacement")))
                .andExpect(status().isConflict());

        assertEquals(1, classRepository.findAll().stream()
                .filter(clazz -> deleted.getClassCode().equals(clazz.getClassCode()))
                .count());
    }

    @Test
    void courseCreateKeepsExistingBehaviorForActiveClass() throws Exception {
        com.saga.be.entity.Class clazz = saveClass(uniqueCode("ACTIVE-COURSE"), "Active course class");
        CourseReferences references = saveCourseReferences();
        String courseCode = uniqueCode("ACTIVE-CLASS-COURSE");

        performAdminCourseCreate(courseJson(courseCode, clazz.getId(), references))
                .andExpect(status().isCreated());

        Course persisted = courseRepository.findByCourseCode(courseCode).orElseThrow();
        assertEquals(clazz.getId(), persisted.getClazz().getId());
        assertNull(persisted.getClazz().getDeletedAt());
    }

    @Test
    void courseCreateReturnsNotFoundForMissingOrSoftDeletedClass() throws Exception {
        CourseReferences references = saveCourseReferences();
        String missingCourseCode = uniqueCode("MISSING-CLASS-COURSE");

        performAdminCourseCreate(courseJson(missingCourseCode, UUID.randomUUID(), references))
                .andExpect(status().isNotFound());
        assertTrue(courseRepository.findByCourseCode(missingCourseCode).isEmpty());

        com.saga.be.entity.Class deleted = saveClass(uniqueCode("DELETED-COURSE"), "Deleted course class");
        deleted.setDeletedAt(LocalDateTime.now());
        classRepository.saveAndFlush(deleted);
        String deletedCourseCode = uniqueCode("DELETED-CLASS-COURSE");

        performAdminCourseCreate(courseJson(deletedCourseCode, deleted.getId(), references))
                .andExpect(status().isNotFound());

        assertTrue(courseRepository.findByCourseCode(deletedCourseCode).isEmpty());
        assertNotNull(classRepository.findById(deleted.getId()).orElseThrow().getDeletedAt());
    }

    private com.saga.be.entity.Class saveClass(String code, String name) {
        return classRepository.saveAndFlush(com.saga.be.entity.Class.builder()
                .classCode(code)
                .name(name)
                .build());
    }

    private CourseReferences saveCourseReferences() {
        Subject subject = subjectRepository.saveAndFlush(Subject.builder()
                .subjectCode(uniqueCode("SUBJECT"))
                .name("Course subject")
                .build());
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
        return new CourseReferences(subject.getId(), semester.getId(), lecturer.getId());
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
        return mockMvc.perform(put("/api/v1/classes/{id}", id)
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
        return mockMvc.perform(put("/api/v1/classes/{id}", id)
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
        return mockMvc.perform(delete("/api/v1/classes/{id}", id)
                .with(authentication(admin))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue()));
    }

    private org.springframework.test.web.servlet.ResultActions performDeleteAs(UUID id, ApplicationRole role)
            throws Exception {
        Authentication authentication = authenticationFor(role);
        Cookie csrfCookie = csrfCookie(authentication);
        return mockMvc.perform(delete("/api/v1/classes/{id}", id)
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
                role.name().toLowerCase() + "-class",
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

    private String classJson(String code, String name) {
        return """
                {
                  "classCode": "%s",
                  "name": "%s"
                }
                """.formatted(code, name);
    }

    private String courseJson(String courseCode, UUID classId, CourseReferences references) {
        return """
                {
                  "courseCode": "%s",
                  "name": "Course class cross-reference test",
                  "subjectId": "%s",
                  "classId": "%s",
                  "semesterId": "%s",
                  "instructorId": "%s"
                }
                """.formatted(
                courseCode,
                references.subjectId(),
                classId,
                references.semesterId(),
                references.lecturerId()
        );
    }

    private String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private record CourseReferences(UUID subjectId, UUID semesterId, UUID lecturerId) {
    }
}
