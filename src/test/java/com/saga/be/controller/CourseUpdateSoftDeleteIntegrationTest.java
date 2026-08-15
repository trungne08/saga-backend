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
import com.saga.be.entity.Project;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.Subject;
import com.saga.be.entity.TaskWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.TaskWeightConfigRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.ClassRepository;
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
class CourseUpdateSoftDeleteIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private SemesterRepository semesterRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private StudentRepository studentRepository;
    @Autowired private StudentCourseInvitationRepository invitationRepository;
    @Autowired private TaskWeightConfigRepository taskWeightConfigRepository;

    @Test
    void createAcceptsActiveReferencesAndRejectsEachTombstoneOrMissingLecturer() throws Exception {
        References active = references();
        performAdminPost(courseJson("ACTIVE", "Active course", active)).andExpect(status().isCreated());

        Subject deletedSubject = active.subject(); deletedSubject.setDeletedAt(LocalDateTime.now()); subjectRepository.saveAndFlush(deletedSubject);
        performAdminPost(courseJson("DELETED-SUBJECT", "No", active)).andExpect(status().isNotFound());
        assertTrue(courseRepository.findByCourseCode("DELETED-SUBJECT").isEmpty());

        References classReferences = references();
        com.saga.be.entity.Class deletedClass = classReferences.clazz(); deletedClass.setDeletedAt(LocalDateTime.now()); classRepository.saveAndFlush(deletedClass);
        performAdminPost(courseJson("DELETED-CLASS", "No", classReferences)).andExpect(status().isNotFound());

        References semesterReferences = references();
        Semester deletedSemester = semesterReferences.semester(); deletedSemester.setDeletedAt(LocalDateTime.now()); semesterRepository.saveAndFlush(deletedSemester);
        performAdminPost(courseJson("DELETED-SEMESTER", "No", semesterReferences)).andExpect(status().isNotFound());

        References lecturerReferences = references();
        performAdminPost(courseJson("MISSING-LECTURER", "No", lecturerReferences, UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void adminUpdatesWithoutMutatingTeamOrProjectAndSecurityFailuresRemainCorrect() throws Exception {
        References references = references();
        Course course = course("OLD", references);
        Project project = projectRepository.saveAndFlush(Project.builder().course(course).name("Project").build());
        Team team = teamRepository.saveAndFlush(Team.builder().course(course).project(project).name("Team").build());
        String body = courseJson("UPDATED", "Updated course", references);
        Cookie csrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));

        mockMvc.perform(put("/api/v1/courses/{id}", course.getId()).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        putAs(course.getId(), body, ApplicationRole.LECTURER).andExpect(status().isForbidden());
        putAs(course.getId(), body, ApplicationRole.STUDENT).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/courses/{id}", course.getId()).with(authentication(authenticationFor(ApplicationRole.ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        putAdmin(UUID.randomUUID(), body).andExpect(status().isNotFound());
        putAdmin(course.getId(), body).andExpect(status().isOk()).andExpect(jsonPath("$.courseCode").value("UPDATED"));
        assertEquals(course.getId(), teamRepository.findById(team.getId()).orElseThrow().getCourse().getId());
        assertEquals(course.getId(), projectRepository.findById(project.getId()).orElseThrow().getCourse().getId());
    }

    @Test
    void updateRejectsDuplicateAndEachTombstonedReference() throws Exception {
        References references = references();
        Course course = course("ONE", references);
        Course duplicate = course("TWO", references());
        putAdmin(course.getId(), courseJson(duplicate.getCourseCode(), "Duplicate", references)).andExpect(status().isConflict());

        Subject deletedSubject = references.subject(); deletedSubject.setDeletedAt(LocalDateTime.now()); subjectRepository.saveAndFlush(deletedSubject);
        putAdmin(course.getId(), courseJson("NEW-S", "No", references)).andExpect(status().isNotFound());

        References classReferences = references();
        com.saga.be.entity.Class deletedClass = classReferences.clazz(); deletedClass.setDeletedAt(LocalDateTime.now()); classRepository.saveAndFlush(deletedClass);
        putAdmin(course.getId(), courseJson("NEW-C", "No", classReferences)).andExpect(status().isNotFound());

        References semesterReferences = references();
        Semester deletedSemester = semesterReferences.semester(); deletedSemester.setDeletedAt(LocalDateTime.now()); semesterRepository.saveAndFlush(deletedSemester);
        putAdmin(course.getId(), courseJson("NEW-SEM", "No", semesterReferences)).andExpect(status().isNotFound());
    }

    @Test
    void unusedCourseSoftDeletesAndActiveReadsHideItWithDeterministicRepeatAndCodePolicy() throws Exception {
        Course course = course("DELETE", references());
        deleteAdmin(course.getId()).andExpect(status().isNoContent());
        assertNotNull(courseRepository.findById(course.getId()).orElseThrow().getDeletedAt());
        mockMvc.perform(get("/api/v1/courses/{id}", course.getId()).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/courses").with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", not(hasItem(course.getId().toString()))));
        deleteAdmin(course.getId()).andExpect(status().isNotFound());
        performAdminPost(courseJson("DELETE", "Reuse denied", references())).andExpect(status().isConflict());
    }

    @Test
    void deleteRequiresAdminCsrfAndGuardsEveryDirectDependencyWithoutCascade() throws Exception {
        Course teamCourse = course("TEAM", references());
        Team team = teamRepository.saveAndFlush(Team.builder().course(teamCourse).name("Team").build());
        deleteAdmin(teamCourse.getId()).andExpect(status().isConflict());
        assertTrue(teamRepository.findById(team.getId()).isPresent());

        Course projectCourse = course("PROJECT", references());
        Project project = projectRepository.saveAndFlush(Project.builder().course(projectCourse).name("Project").build());
        deleteAdmin(projectCourse.getId()).andExpect(status().isConflict());
        assertTrue(projectRepository.findById(project.getId()).isPresent());

        Course invitationCourse = course("INVITATION", references());
        Student student = studentRepository.saveAndFlush(Student.builder().cognitoSub(unique("SUB")).studentCode(unique("ST"))
                .email(unique("student") + "@test").fullName("Student").accountStatus(AccountStatus.ACTIVE).build());
        StudentCourseInvitation invitation = invitationRepository.saveAndFlush(StudentCourseInvitation.builder().student(student)
                .course(invitationCourse).invitationType(StudentInvitationType.FIRST_LOGIN_REQUIRED).invitationStatus(StudentInvitationStatus.PENDING).build());
        deleteAdmin(invitationCourse.getId()).andExpect(status().isConflict());
        assertTrue(invitationRepository.findById(invitation.getId()).isPresent());

        Course configCourse = course("CONFIG", references());
        TaskWeightConfig config = taskWeightConfigRepository.saveAndFlush(TaskWeightConfig.builder().course(configCourse)
                .taskType("TASK").weight(1F).build());
        deleteAdmin(configCourse.getId()).andExpect(status().isConflict());
        assertTrue(taskWeightConfigRepository.findById(config.getId()).isPresent());

        Course secure = course("SECURE", references());
        deleteAs(secure.getId(), ApplicationRole.LECTURER).andExpect(status().isForbidden());
        deleteAs(secure.getId(), ApplicationRole.STUDENT).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/courses/{id}", secure.getId()).with(authentication(authenticationFor(ApplicationRole.ADMIN))))
                .andExpect(status().isForbidden());
    }

    private References references() {
        Subject subject = subjectRepository.saveAndFlush(Subject.builder().subjectCode(unique("SUBJECT")).name("Subject").build());
        com.saga.be.entity.Class clazz = classRepository.saveAndFlush(com.saga.be.entity.Class.builder().classCode(unique("CLASS")).name("Class").build());
        Semester semester = semesterRepository.saveAndFlush(Semester.builder().code(unique("SEM")).name("Semester")
                .startDate(LocalDateTime.of(2026, 1, 1, 0, 0)).endDate(LocalDateTime.of(2026, 5, 1, 0, 0)).build());
        Lecturer lecturer = lecturerRepository.saveAndFlush(Lecturer.builder().cognitoSub(unique("LECTURER-SUB"))
                .email(unique("lecturer") + "@test").fullName("Lecturer").build());
        return new References(subject, clazz, semester, lecturer);
    }

    private Course course(String code, References references) {
        return courseRepository.saveAndFlush(Course.builder().courseCode(code).name("Course " + code).subject(references.subject())
                .clazz(references.clazz()).semester(references.semester()).instructor(references.lecturer()).build());
    }

    private org.springframework.test.web.servlet.ResultActions performAdminPost(String body) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN); Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(post("/api/v1/courses").with(authentication(admin)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private org.springframework.test.web.servlet.ResultActions putAdmin(UUID id, String body) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN); Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(put("/api/v1/courses/{id}", id).with(authentication(admin)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private org.springframework.test.web.servlet.ResultActions putAs(UUID id, String body, ApplicationRole role) throws Exception {
        Authentication user = authenticationFor(role); Cookie csrf = csrfCookie(user);
        return mockMvc.perform(put("/api/v1/courses/{id}", id).with(authentication(user)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private org.springframework.test.web.servlet.ResultActions deleteAdmin(UUID id) throws Exception {
        Authentication admin = authenticationFor(ApplicationRole.ADMIN); Cookie csrf = csrfCookie(admin);
        return mockMvc.perform(delete("/api/v1/courses/{id}", id).with(authentication(admin)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()));
    }
    private org.springframework.test.web.servlet.ResultActions deleteAs(UUID id, ApplicationRole role) throws Exception {
        Authentication user = authenticationFor(role); Cookie csrf = csrfCookie(user);
        return mockMvc.perform(delete("/api/v1/courses/{id}", id).with(authentication(user)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()));
    }
    private Cookie csrfCookie(Authentication auth) throws Exception { MvcResult result = mockMvc.perform(get("/api/auth/csrf").with(authentication(auth))).andExpect(status().isOk()).andReturn(); Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN"); if (cookie == null) throw new AssertionError("Missing XSRF-TOKEN"); return cookie; }
    private Authentication authenticationFor(ApplicationRole role) { SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-course", role.name().toLowerCase() + "@test", role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE); return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))); }
    private String courseJson(String code, String name, References references) { return courseJson(code, name, references, references.lecturer().getId()); }
    private String courseJson(String code, String name, References references, UUID lecturerId) { return "{\"courseCode\":\"%s\",\"name\":\"%s\",\"subjectId\":\"%s\",\"classId\":\"%s\",\"semesterId\":\"%s\",\"instructorId\":\"%s\"}".formatted(code, name, references.subject().getId(), references.clazz().getId(), references.semester().getId(), lecturerId); }
    private String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private record References(Subject subject, com.saga.be.entity.Class clazz, Semester semester, Lecturer lecturer) { }
}
