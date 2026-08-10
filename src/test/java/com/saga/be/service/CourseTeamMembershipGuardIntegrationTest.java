package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
class CourseTeamMembershipGuardIntegrationTest {

    private static final String IMPORT_PATH = "/api/v1/courses/{courseId}/import-students";

    @Autowired
    private ExcelImportService excelImportService;
    @Autowired
    private PlatformTransactionManager transactionManager;
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
    private StudentCourseInvitationRepository invitationRepository;

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
    void createsMembershipOnceAndKeepsRoleForSameStudentAndTeam() throws Exception {
        Course course = course("X");
        Student student = student("same-team");

        excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, "A", "x"));
        excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, "A", ""));

        List<TeamMember> memberships = memberships(student, course);
        assertEquals(1, memberships.size());
        assertEquals(RoleInTeam.LEADER, memberships.get(0).getRoleInTeam());
    }

    @Test
    void rejectsDifferentTeamInSameCourseWithoutChangingExistingMembership() throws Exception {
        Course course = course("X");
        Student student = student("same-course-conflict");
        excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, "A", "x"));

        assertThrows(IdentityConflictException.class,
                () -> excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, "B", "")));

        List<TeamMember> memberships = memberships(student, course);
        assertEquals(1, memberships.size());
        assertEquals("Group A", memberships.get(0).getTeam().getName());
        assertEquals(RoleInTeam.LEADER, memberships.get(0).getRoleInTeam());
    }

    @Test
    void httpImportReturnsConflictForDifferentTeamInSameCourse() throws Exception {
        Course course = course("X");
        Student student = student("http-conflict");
        excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, "A", ""));
        Authentication authentication = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(student, "B", ""))
                        .with(authentication(authentication)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("COURSE_TEAM_MEMBERSHIP_CONFLICT"));

        assertEquals(1, memberships(student, course).size());
    }

    @Test
    void allowsIndependentMembershipAndRoleInDifferentCourses() throws Exception {
        Course courseX = course("X");
        Course courseY = course("Y");
        Student student = student("different-courses");

        excelImportService.importStudentsToCourse(admin(), courseX.getId(), workbook(student, "A", "x"));
        excelImportService.importStudentsToCourse(admin(), courseY.getId(), workbook(student, "B", ""));

        assertEquals(RoleInTeam.LEADER, memberships(student, courseX).get(0).getRoleInTeam());
        assertEquals(RoleInTeam.MEMBER, memberships(student, courseY).get(0).getRoleInTeam());
    }

    @Test
    void httpImportRequiresAuthorizedBrowserSessionAndCsrf() throws Exception {
        Course course = course("AUTH");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());
        Authentication owner = authenticationFor(ApplicationRole.LECTURER, course.getInstructor().getId());
        Authentication otherLecturer = authenticationFor(ApplicationRole.LECTURER, UUID.randomUUID());
        Authentication student = authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook("SE100001", "valid@example.test", "Valid", "A", ""))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook("SE100001", "valid@example.test", "Valid", "A", ""))
                        .with(authentication(student))).andExpect(status().isForbidden());
        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook("SE100001", "valid@example.test", "Valid", "A", ""))
                        .with(authentication(otherLecturer))).andExpect(status().isForbidden());
        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook("SE100001", "valid@example.test", "Valid", "A", ""))
                        .with(authentication(admin))).andExpect(status().isForbidden());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook("SE100001", "valid@example.test", "Valid", "A", ""))
                        .with(authentication(owner)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void parserRejectsBadHeaderFormulaAndMalformedWorkbookWithSafeBadRequest() throws Exception {
        Course course = course("PARSER");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbookWithHeader(
                        new String[] {"Class", "StudentCode", "Email", "MemberCode", "FullName", "Unexpected", "Leader"},
                        new String[] {"SE", "SE100002", "header@example.test", "m", "Header", "A", ""}))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_HEADER"));

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbookWithFormula())
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FORMULA_NOT_ALLOWED"));

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(new MockMultipartFile(
                        "file", "broken.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {1, 2, 3}))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("MALFORMED_WORKBOOK"));
        assertEquals(0, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void duplicateAndInvalidRowsRollbackBeforeAnyStudentMembershipOrInvitationWrite() throws Exception {
        Course course = course("ROLLBACK");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbookRows(List.of(
                        new String[] {"SE100003", "first@example.test", "First", "A", ""},
                        new String[] {"SE100004", "second@example.test", "", "B", ""})))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_ROW"));
        assertEquals(0, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbookRows(List.of(
                        new String[] {"SE100005", "duplicate@example.test", "One", "A", ""},
                        new String[] {"SE100005", "duplicate@example.test", "Two", "A", ""})))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("DUPLICATE_IN_FILE"));
        assertEquals(0, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void identityConflictAndExistingStudentReusePreserveCurrentIdentityAndStatus() throws Exception {
        Course course = course("IDENTITY");
        Student existing = student("identity-existing");
        existing.setAccountStatus(AccountStatus.INACTIVE);
        existing.setFullName("Stored Name");
        studentRepository.saveAndFlush(existing);
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook(
                        existing.getStudentCode(), "different@example.test", "Different", "A", ""))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("IDENTITY_CONFLICT"));

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbook(
                        existing.getStudentCode(), existing.getEmail(), "Different", "A", "x"))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isOk());
        Student reloaded = studentRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Stored Name", reloaded.getFullName());
        assertEquals(AccountStatus.INACTIVE, reloaded.getAccountStatus());
        assertEquals(existing.getCognitoSub(), reloaded.getCognitoSub());
        assertEquals(RoleInTeam.LEADER, memberships(existing, course).get(0).getRoleInTeam());
    }

    @Test
    void rowLimitIsRejectedBeforePersistence() throws Exception {
        Course course = course("LIMIT");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());
        List<String[]> rows = new java.util.ArrayList<>();
        for (int index = 0; index < 1_001; index++) {
            rows.add(new String[] {String.format("SE%06d", index), "limit" + index + "@example.test", "Limit", "A", ""});
        }
        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(workbookRows(rows))
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ROW_LIMIT"));
        assertEquals(0, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void fileSizeLimitIsRejectedBeforeWorkbookParsingOrPersistence() throws Exception {
        Course course = course("FILE-LIMIT");
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "oversized.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[1_048_577]
        );

        mockMvc.perform(multipart(IMPORT_PATH, course.getId()).file(oversized)
                        .with(authentication(admin)).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
        assertEquals(0, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    @Timeout(15)
    void concurrentIndependentTransactionsCreateOnlyOneMembershipInCourse() throws Exception {
        Course course = course("X");
        Student student = student("concurrent");
        team(course, "Group A");
        team(course, "Group B");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch transactionsStarted = new CountDownLatch(2);
        CyclicBarrier beforeWrite = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<AttemptResult> first = executor.submit(competingImport(
                    transactionTemplate, transactionsStarted, beforeWrite, course, student, "A"
            ));
            Future<AttemptResult> second = executor.submit(competingImport(
                    transactionTemplate, transactionsStarted, beforeWrite, course, student, "B"
            ));

            assertTrue(transactionsStarted.await(5, TimeUnit.SECONDS));
            AttemptResult firstResult = first.get(8, TimeUnit.SECONDS);
            AttemptResult secondResult = second.get(8, TimeUnit.SECONDS);
            assertEquals(1, List.of(firstResult, secondResult).stream().filter(AttemptResult::success).count());
            assertEquals(1, List.of(firstResult, secondResult).stream().filter(AttemptResult::conflict).count());

            List<TeamMember> memberships = transactionTemplate.execute(status -> memberships(student, course));
            assertEquals(1, memberships.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Callable<AttemptResult> competingImport(
            TransactionTemplate transactionTemplate,
            CountDownLatch transactionsStarted,
            CyclicBarrier beforeWrite,
            Course course,
            Student student,
            String group
    ) {
        return () -> {
            try {
                return transactionTemplate.execute(status -> {
                    transactionsStarted.countDown();
                    try {
                        beforeWrite.await(5, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                    try {
                        excelImportService.importStudentsToCourse(admin(), course.getId(), workbook(student, group, ""));
                        return AttemptResult.SUCCESS;
                    } catch (IdentityConflictException exception) {
                        throw exception;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            } catch (IdentityConflictException exception) {
                return AttemptResult.CONFLICT;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        };
    }

    private List<TeamMember> memberships(Student student, Course course) {
        return teamMemberRepository.findByStudentIdAndTeamCourseId(student.getId(), course.getId());
    }

    private Course course(String suffix) {
        Lecturer lecturer = lecturer(suffix);
        return courseRepository.save(Course.builder()
                .courseCode("COURSE-" + suffix + "-" + UUID.randomUUID())
                .name("Course " + suffix)
                .instructor(lecturer)
                .build());
    }

    private Lecturer lecturer(String suffix) {
        return lecturerRepository.save(Lecturer.builder()
                .cognitoSub("lecturer-" + suffix + "-" + UUID.randomUUID())
                .email("lecturer-" + suffix + "-" + UUID.randomUUID() + "@example.test")
                .fullName("Lecturer " + suffix)
                .build());
    }

    private Student student(String suffix) {
        String id = UUID.randomUUID().toString();
        return studentRepository.save(Student.builder()
                .cognitoSub("student-" + suffix + "-" + id)
                .studentCode("SE" + id.substring(0, 6).toUpperCase())
                .email("student-" + suffix + "-" + id + "@example.test")
                .fullName("Student " + suffix)
                .accountStatus(AccountStatus.ACTIVE)
                .build());
    }

    private Team team(Course course, String name) {
        return teamRepository.save(Team.builder().course(course).name(name).build());
    }

    private SagaPrincipal admin() {
        return (SagaPrincipal) authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()).getPrincipal();
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

    private MockMultipartFile workbook(Student student, String group, String leader) throws Exception {
        return workbook(student.getStudentCode(), student.getEmail(), student.getFullName(), group, leader);
    }

    private MockMultipartFile workbook(String studentCode, String email, String fullName, String group, String leader)
            throws Exception {
        return workbookRows(List.<String[]>of(new String[] {studentCode, email, fullName, group, leader}));
    }

    private MockMultipartFile workbookRows(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            var header = sheet.createRow(0);
            String[] columns = {"Class", "StudentCode", "Email", "MemberCode", "FullName", "Group", "Leader"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                String[] values = rows.get(rowIndex);
                var row = sheet.createRow(rowIndex + 1);
                row.createCell(0).setCellValue("SE");
                row.createCell(1).setCellValue(values[0]);
                row.createCell(2).setCellValue(values[1]);
                row.createCell(3).setCellValue(values[0].toLowerCase());
                row.createCell(4).setCellValue(values[2]);
                row.createCell(5).setCellValue(values[3]);
                row.createCell(6).setCellValue(values[4]);
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

    private MockMultipartFile workbookWithHeader(String[] headerValues, String[] rowValues) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            var header = sheet.createRow(0);
            for (int index = 0; index < headerValues.length; index++) {
                header.createCell(index).setCellValue(headerValues[index]);
            }
            var row = sheet.createRow(1);
            for (int index = 0; index < rowValues.length; index++) {
                row.createCell(index).setCellValue(rowValues[index]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile workbookWithFormula() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            String[] columns = {"Class", "StudentCode", "Email", "MemberCode", "FullName", "Group", "Leader"};
            var header = sheet.createRow(0);
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("SE");
            row.createCell(1).setCellFormula("\"SE100006\"");
            row.createCell(2).setCellValue("formula@example.test");
            row.createCell(3).setCellValue("se100006");
            row.createCell(4).setCellValue("Formula");
            row.createCell(5).setCellValue("A");
            row.createCell(6).setCellValue("");
            workbook.write(output);
            return new MockMultipartFile("file", "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private enum AttemptResult {
        SUCCESS, CONFLICT;

        boolean success() {
            return this == SUCCESS;
        }

        boolean conflict() {
            return this == CONFLICT;
        }
    }
}
