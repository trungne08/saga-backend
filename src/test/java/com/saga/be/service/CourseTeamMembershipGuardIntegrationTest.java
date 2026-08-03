package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;
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
        Cookie csrf = csrfCookie(authentication);

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(student, "B", ""))
                        .with(authentication(authentication))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());

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

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
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
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            var header = sheet.createRow(0);
            String[] columns = {"Class", "RollNumber", "Email", "MemberCode", "FullName", "Group", "Leader"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("SE");
            row.createCell(1).setCellValue(student.getStudentCode());
            row.createCell(2).setCellValue(student.getEmail());
            row.createCell(3).setCellValue(student.getStudentCode().toLowerCase());
            row.createCell(4).setCellValue(student.getFullName());
            row.createCell(5).setCellValue(group);
            row.createCell(6).setCellValue(leader);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
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
