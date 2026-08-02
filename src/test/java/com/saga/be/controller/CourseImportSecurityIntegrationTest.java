package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.ExcelImportService;
import com.saga.be.service.StudentInvitationClaimService;
import com.saga.be.service.StudentInvitationProcessor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseImportSecurityIntegrationTest {

    private static final String IMPORT_PATH = "/api/v1/courses/{courseId}/import-students";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExcelImportService importService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LecturerRepository lecturerRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentCourseInvitationRepository invitationRepository;

    @Autowired
    private StudentInvitationClaimService invitationClaimService;

    @Autowired
    private StudentInvitationProperties invitationProperties;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

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
    void anonymousRequestWithValidCsrfIsUnauthorized() throws Exception {
        mockMvc.perform(multipart(IMPORT_PATH, UUID.randomUUID())
                        .file(workbook(row("SE000001", "anonymous@example.test", "1", "x")))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutCsrfIsForbiddenBeforeController() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(row("SE000002", "csrf@example.test", "1", "x")))
                        .with(authentication(authenticationFor(
                                ApplicationRole.ADMIN,
                                UUID.randomUUID()
                        ))))
                .andExpect(status().isForbidden());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void adminMayImportIntoAnyCourse() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()),
                        course.getId(),
                        row("SE000003", "admin@example.test", "1", "x")
                ))
                .andExpect(status().isOk());

        assertEquals(1, studentRepository.count());
        assertEquals(1, teamRepository.count());
        assertEquals(1, teamMemberRepository.count());
    }

    @Test
    void assignedLecturerMayImportIntoOwnCourse() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);

        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.LECTURER, owner.getId()),
                        course.getId(),
                        row("SE000004", "owner@example.test", "1", "x")
                ))
                .andExpect(status().isOk());

        assertEquals(1, studentRepository.count());
    }

    @Test
    void unrelatedLecturerIsForbidden() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.LECTURER, UUID.randomUUID()),
                        course.getId(),
                        row("SE000005", "non-owner@example.test", "1", "x")
                ))
                .andExpect(status().isForbidden());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void studentIsForbiddenEvenWithValidCsrf() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID()),
                        course.getId(),
                        row("SE000006", "student@example.test", "1", "x")
                ))
                .andExpect(status().isForbidden());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void missingCourseReturnsNotFoundAfterRoleGuard() throws Exception {
        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()),
                        UUID.randomUUID(),
                        row("SE000007", "missing-course@example.test", "1", "x")
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientSuppliedHeadersCannotChangeSessionRoleOrProfileId() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(row("SE000008", "header@example.test", "1", "x")))
                        .with(authentication(authenticationFor(
                                ApplicationRole.STUDENT,
                                UUID.randomUUID()
                        )))
                        .with(csrf())
                        .header("X-Application-Role", "ADMIN")
                        .header("X-Local-Profile-Id", course.getInstructor().getId().toString()))
                .andExpect(status().isForbidden());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void masterDataCreateEndpointsRemainAdminOnly() throws Exception {
        Authentication student = authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID());

        List<MasterDataRequest> requests = List.of(
                new MasterDataRequest("/api/v1/subjects", """
                        {"subjectCode":"SWE-401","name":"Software Engineering"}
                        """),
                new MasterDataRequest("/api/v1/classes", """
                        {"classCode":"SE-401","name":"SE 401"}
                        """),
                new MasterDataRequest("/api/v1/semesters", """
                        {"code":"SEM-401","name":"Semester 401","startDate":"2026-01-01T00:00:00","endDate":"2026-06-01T00:00:00"}
                        """),
                new MasterDataRequest("/api/v1/courses", ("""
                        {"courseCode":"COURSE-401","name":"Course 401","subjectId":"%s","classId":"%s","semesterId":"%s","instructorId":"%s"}
                        """).formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        );

        for (MasterDataRequest request : requests) {
            mockMvc.perform(post(request.path())
                            .with(authentication(student))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request.body()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void invalidImportRollsBackAllRows() throws Exception {
        Course course = createCourse(createLecturer());
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> importService.importStudentsToCourse(
                (SagaPrincipal) admin.getPrincipal(),
                course.getId(),
                workbook(
                        row("SE000009", "duplicate@example.test", "1", "x"),
                        row("SE000010", "duplicate@example.test", "1", "")
                )
        ));

        assertEquals(0, studentRepository.count());
        assertEquals(0, teamRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void repeatedImportDoesNotDuplicateStudentTeamOrMembership() throws Exception {
        Course course = createCourse(createLecturer());
        SagaPrincipal admin = (SagaPrincipal) authenticationFor(
                ApplicationRole.ADMIN,
                UUID.randomUUID()
        ).getPrincipal();
        MockMultipartFile file = workbook(row("SE000011", "repeat@example.test", "1", "x"));

        importService.importStudentsToCourse(admin, course.getId(), file);
        importService.importStudentsToCourse(admin, course.getId(), file);

        assertEquals(1, studentRepository.count());
        assertEquals(1, teamRepository.count());
        assertEquals(1, teamMemberRepository.count());
        assertEquals(1, invitationRepository.count());
    }

    @Test
    void providerFailureDoesNotRollBackCompletedImport() throws Exception {
        Course course = createCourse(createLecturer());
        SagaPrincipal admin = (SagaPrincipal) authenticationFor(
                ApplicationRole.ADMIN,
                UUID.randomUUID()
        ).getPrincipal();
        importService.importStudentsToCourse(
                admin,
                course.getId(),
                workbook(row("SE000013", "failure@example.test", "1", "x"))
        );

        new StudentInvitationProcessor(
                invitationRepository,
                invitationClaimService,
                message -> {
                    throw new IllegalStateException("test provider failure");
                },
                invitationProperties
        ).process(invitationRepository.findAll().get(0).getId());

        assertEquals(1, studentRepository.count());
        assertEquals(1, teamRepository.count());
        assertEquals(1, teamMemberRepository.count());
        assertEquals(
                com.saga.be.entity.enums.StudentInvitationStatus.FAILED,
                invitationRepository.findAll().get(0).getInvitationStatus()
        );
    }

    @Test
    void roleChangeDoesNotCreateSecondMembershipInSameTeam() throws Exception {
        Course course = createCourse(createLecturer());
        SagaPrincipal admin = (SagaPrincipal) authenticationFor(
                ApplicationRole.ADMIN,
                UUID.randomUUID()
        ).getPrincipal();

        importService.importStudentsToCourse(
                admin,
                course.getId(),
                workbook(row("SE000012", "role@example.test", "1", "x"))
        );
        importService.importStudentsToCourse(
                admin,
                course.getId(),
                workbook(row("SE000012", "role@example.test", "1", ""))
        );

        assertEquals(1, teamMemberRepository.count());
        assertEquals(
                RoleInTeam.LEADER,
                teamMemberRepository.findAll().get(0).getRoleInTeam()
        );
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder importRequest(
            Authentication authentication,
            UUID courseId,
            StudentRow... rows
    ) throws IOException {
        return multipart(IMPORT_PATH, courseId)
                .file(workbook(rows))
                .with(authentication(authentication))
                .with(csrf());
    }

    private Lecturer createLecturer() {
        String suffix = UUID.randomUUID().toString();
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + suffix)
                .email("lecturer-" + suffix + "@example.test")
                .fullName("Course Lecturer")
                .build());
    }

    private Course createCourse(Lecturer instructor) {
        String suffix = UUID.randomUUID().toString();
        return courseRepository.save(Course.builder()
                .courseCode("COURSE-" + suffix)
                .name("Import authorization course")
                .instructor(instructor)
                .build());
    }

    private Authentication authenticationFor(ApplicationRole role, UUID localProfileId) {
        SagaPrincipal principal = new SagaPrincipal(
                role.name().toLowerCase() + "-subject",
                role.name().toLowerCase() + "@example.test",
                role.name() + " User",
                role,
                localProfileId,
                AccountStatus.ACTIVE
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private MockMultipartFile workbook(StudentRow... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            var header = sheet.createRow(0);
            String[] columns = {"Class", "RollNumber", "Email", "MemberCode", "FullName", "Group", "Leader"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            for (int index = 0; index < rows.length; index++) {
                StudentRow student = rows[index];
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue("SE");
                row.createCell(1).setCellValue(student.studentCode());
                row.createCell(2).setCellValue(student.email());
                row.createCell(3).setCellValue(student.studentCode().toLowerCase());
                row.createCell(4).setCellValue("Imported Student");
                row.createCell(5).setCellValue(student.group());
                row.createCell(6).setCellValue(student.leader());
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private StudentRow row(String studentCode, String email, String group, String leader) {
        return new StudentRow(studentCode, email, group, leader);
    }

    private record StudentRow(String studentCode, String email, String group, String leader) {
    }

    private record MasterDataRequest(String path, String body) {
    }
}
