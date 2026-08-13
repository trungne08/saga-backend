package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.NotificationRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudentAutoActivationRollbackIntegrationTest {

    @Autowired
    private ExcelImportService excelImportService;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private TeamMemberRepository teamMemberRepository;
    @Autowired
    private StudentCourseInvitationRepository invitationRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoBean
    private StudentInvitationOutboxService invitationOutboxService;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        invitationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    void failedCourseProvisioningDoesNotMutateLegacyPendingAccountLifecycle() throws Exception {
        Student linkedPending = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub("rollback-linked-subject")
                .email("rollbackse170512@example.test")
                .studentCode("SE170512")
                .fullName("Rollback Student")
                .accountStatus(AccountStatus.PENDING)
                .build());
        Course course = courseRepository.saveAndFlush(Course.builder()
                .courseCode("ROLLBACK-" + UUID.randomUUID())
                .name("Rollback Course")
                .build());
        when(invitationOutboxService.enqueueForCourse(any(Student.class), any(Course.class)))
                .thenThrow(new IllegalStateException("late provisioning failure"));

        assertThrows(IllegalStateException.class, () -> excelImportService.importStudentsToCourse(
                admin(),
                course.getId(),
                workbook(linkedPending)
        ));

        Student reloaded = studentRepository.findById(linkedPending.getId()).orElseThrow();
        assertEquals(AccountStatus.PENDING, reloaded.getAccountStatus());
        assertEquals(0, teamRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
        assertEquals(0, notificationRepository.count());
    }

    private SagaPrincipal admin() {
        return new SagaPrincipal(
                "admin-subject",
                "admin@example.test",
                "Admin",
                ApplicationRole.ADMIN,
                UUID.randomUUID(),
                null
        );
    }

    private MockMultipartFile workbook(Student student) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Students");
            var header = sheet.createRow(0);
            List<String> headers = List.of(
                    "Class", "StudentCode", "Email", "MemberCode", "FullName", "Group", "Leader"
            );
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Class A");
            row.createCell(1).setCellValue(student.getStudentCode());
            row.createCell(2).setCellValue(student.getEmail());
            row.createCell(3).setCellValue("Member A");
            row.createCell(4).setCellValue(student.getFullName());
            row.createCell(5).setCellValue("1");
            row.createCell(6).setCellValue("x");
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "students.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }
}
