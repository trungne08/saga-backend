package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.entity.Course;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
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
import java.io.ByteArrayInputStream;
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
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CourseImportSecurityIntegrationTest {

    private static final String IMPORT_PATH = "/api/v1/courses/{courseId}/import-students";
    private static final String ADMIN_TEMPLATE_IMPORT_PATH = "/api/v1/courses/{courseId}/admin-import-students-template";
    private static final String GROUPING_TEMPLATE_PATH = "/api/v1/courses/{courseId}/students-grouping-template";
    private static final String ADMIN_TEMPLATE_DOWNLOAD_PATH = "/api/v1/courses/{courseId}/admin-students-template";
    private static final String MANUAL_ADD_PATH = "/api/v1/courses/{courseId}/students/manual";
    private static final String STUDENT_UPDATE_PATH = "/api/v1/courses/{courseId}/students/{studentId}";

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
        Cookie csrfCookie = csrfCookie(authenticationFor(
                ApplicationRole.ADMIN,
                UUID.randomUUID()
        ));

        mockMvc.perform(multipart(IMPORT_PATH, UUID.randomUUID())
                        .file(workbook(row("SE000001", "anonymous@example.test", "1", "x")))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
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
    void requestWithInvalidCsrfIsForbiddenBeforeController() throws Exception {
        Course course = createCourse(createLecturer());
        Authentication admin = authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID());
        Cookie csrfCookie = csrfCookie(admin);

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(row("SE000002A", "invalid-csrf@example.test", "1", "x")))
                        .with(authentication(admin))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "invalid-csrf-token"))
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
        Authentication student = authenticationFor(ApplicationRole.STUDENT, UUID.randomUUID());
        Cookie csrfCookie = csrfCookie(student);

        mockMvc.perform(multipart(IMPORT_PATH, course.getId())
                        .file(workbook(row("SE000008", "header@example.test", "1", "x")))
                        .with(authentication(student))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .header("X-Application-Role", "ADMIN")
                        .header("X-Local-Profile-Id", course.getInstructor().getId().toString()))
                .andExpect(status().isForbidden());

        assertEquals(0, studentRepository.count());
    }

    @Test
    void masterDataCreateEndpointsRemainAdminOnly() throws Exception {
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

        for (ApplicationRole role : List.of(ApplicationRole.STUDENT, ApplicationRole.LECTURER)) {
            Authentication authentication = authenticationFor(role, UUID.randomUUID());
            Cookie csrfCookie = csrfCookie(authentication);
            for (MasterDataRequest request : requests) {
                mockMvc.perform(post(request.path())
                                .with(authentication(authentication))
                                .cookie(csrfCookie)
                                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request.body()))
                        .andExpect(status().isForbidden());
            }
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

    @Test
    void adminTemplateImportThenLecturerDownloadAndGroupImportWorks() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);

        mockMvc.perform(adminTemplateImportRequest(
                        authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()),
                        course.getId(),
                        adminRow("SE010001", "template-flow@example.test")
                ))
                .andExpect(status().isOk());

        assertEquals(1, studentRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(1, invitationRepository.count());

        MvcResult templateResult = mockMvc.perform(get(GROUPING_TEMPLATE_PATH, course.getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, owner.getId()))))
                .andExpect(status().isOk())
                .andReturn();

        MockMultipartFile groupedFile = groupedTemplate(
                templateResult.getResponse().getContentAsByteArray(),
                "3",
                "x"
        );

        mockMvc.perform(importRequest(
                        authenticationFor(ApplicationRole.LECTURER, owner.getId()),
                        course.getId(),
                        groupedFile
                ))
                .andExpect(status().isOk());

        assertEquals(1, teamMemberRepository.count());
        assertEquals("Group 3", teamRepository.findAll().get(0).getName());
        assertEquals(RoleInTeam.LEADER, teamMemberRepository.findAll().get(0).getRoleInTeam());
    }

    @Test
    void adminTemplateImportRejectsLecturerTemplateWithGroupLeaderColumns() throws Exception {
        Course course = createCourse(createLecturer());

        mockMvc.perform(adminTemplateImportRequest(
                        authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()),
                        course.getId(),
                        workbook(row("SE010002", "grouping-not-allowed@example.test", "1", "x"))
                ))
                .andExpect(status().isBadRequest());

        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());
    }

    @Test
    void lecturerCannotCallAdminTemplateImportEndpoint() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);

        mockMvc.perform(adminTemplateImportRequest(
                        authenticationFor(ApplicationRole.LECTURER, owner.getId()),
                        course.getId(),
                        adminRow("SE010003", "lecturer-forbidden@example.test")
                ))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDownloadAdminTemplateAndLecturerCannot() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);

        MvcResult adminResult = mockMvc.perform(get(ADMIN_TEMPLATE_DOWNLOAD_PATH, course.getId())
                        .with(authentication(authenticationFor(ApplicationRole.ADMIN, UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(adminResult.getResponse().getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            assertEquals("Class", header.getCell(0).getStringCellValue());
            assertEquals("StudentCode", header.getCell(1).getStringCellValue());
            assertEquals("Email", header.getCell(2).getStringCellValue());
            assertEquals("MemberCode", header.getCell(3).getStringCellValue());
            assertEquals("FullName", header.getCell(4).getStringCellValue());
            assertEquals(1, sheet.getPhysicalNumberOfRows());
        }

        mockMvc.perform(get(ADMIN_TEMPLATE_DOWNLOAD_PATH, course.getId())
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER, owner.getId()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void manualAddWithoutGroupAppearsInWithoutTeamRoster() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);
        Authentication lecturer = authenticationFor(ApplicationRole.LECTURER, owner.getId());
        Cookie csrfCookie = csrfCookie(lecturer);

        mockMvc.perform(post(MANUAL_ADD_PATH, course.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentCode":"SE020001","email":"manual-no-group@example.test","fullName":"Manual Student"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolledInCourse").value(true))
                .andExpect(jsonPath("$.teamId").isEmpty());

        mockMvc.perform(get("/api/v1/courses/{courseId}/students", course.getId())
                        .with(authentication(lecturer))
                        .param("hasTeam", "without"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithoutTeam.content[0].studentCode").value("SE020001"));
    }

    @Test
    void updateStudentSupportsGroupingAndUngrouping() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);
        Authentication lecturer = authenticationFor(ApplicationRole.LECTURER, owner.getId());
        Cookie csrfCookie = csrfCookie(lecturer);

        mockMvc.perform(post(MANUAL_ADD_PATH, course.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentCode":"SE020002","email":"drag-drop@example.test","fullName":"Drag Drop Student"}
                                """))
                .andExpect(status().isCreated());

        var student = studentRepository.findByStudentCodeIgnoreCase("SE020002").orElseThrow();

        mockMvc.perform(patch(STUDENT_UPDATE_PATH, course.getId(), student.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"group":"7","leader":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("Group 7"))
                .andExpect(jsonPath("$.roleInTeam").value("LEADER"));

        mockMvc.perform(patch(STUDENT_UPDATE_PATH, course.getId(), student.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"group":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").isEmpty());
    }

    @Test
    void manualGroupedProvisioningDoesNotUseMembershipToActivateLegacyPendingStudent() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);
        Student linkedPending = studentRepository.saveAndFlush(Student.builder()
                .cognitoSub("manual-linked-subject")
                .studentCode("SE020004")
                .email("manual-linked@example.test")
                .fullName("Manual Linked Student")
                .accountStatus(AccountStatus.PENDING)
                .build());
        Authentication lecturer = authenticationFor(ApplicationRole.LECTURER, owner.getId());
        Cookie csrfCookie = csrfCookie(lecturer);

        mockMvc.perform(post(MANUAL_ADD_PATH, course.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentCode":"SE020004","email":"manual-linked@example.test","fullName":"Manual Linked Student","group":"10","leader":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(linkedPending.getId().toString()))
                .andExpect(jsonPath("$.roleInTeam").value("LEADER"));

        assertEquals(AccountStatus.PENDING, studentRepository.findById(linkedPending.getId())
                .orElseThrow().getAccountStatus());
        assertEquals(1, teamMemberRepository.findByStudentId(linkedPending.getId()).size());
        assertEquals(1, invitationRepository.count());
    }

    @Test
    void deleteStudentRemovesEnrollmentFromCourse() throws Exception {
        Lecturer owner = createLecturer();
        Course course = createCourse(owner);
        Authentication lecturer = authenticationFor(ApplicationRole.LECTURER, owner.getId());
        Cookie csrfCookie = csrfCookie(lecturer);

        mockMvc.perform(post(MANUAL_ADD_PATH, course.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentCode":"SE020003","email":"remove-course@example.test","fullName":"Remove Student","group":"9","leader":false}
                                """))
                .andExpect(status().isCreated());

        var student = studentRepository.findByStudentCodeIgnoreCase("SE020003").orElseThrow();

        mockMvc.perform(delete(STUDENT_UPDATE_PATH, course.getId(), student.getId())
                        .with(authentication(lecturer))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledInCourse").value(false));

        mockMvc.perform(get("/api/v1/courses/{courseId}/students", course.getId())
                        .with(authentication(lecturer))
                        .param("hasTeam", "without"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentsWithoutTeam.content").isEmpty());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder importRequest(
            Authentication authentication,
            UUID courseId,
            StudentRow... rows
    ) throws Exception {
        return importRequest(authentication, courseId, workbook(rows));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder importRequest(
            Authentication authentication,
            UUID courseId,
            MockMultipartFile workbook
    ) throws Exception {
        Cookie csrfCookie = csrfCookie(authentication);
        return multipart(IMPORT_PATH, courseId)
                .file(workbook)
                .with(authentication(authentication))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder adminTemplateImportRequest(
            Authentication authentication,
            UUID courseId,
            AdminTemplateRow... rows
    ) throws Exception {
        return adminTemplateImportRequest(authentication, courseId, adminTemplateWorkbook(rows));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder adminTemplateImportRequest(
            Authentication authentication,
            UUID courseId,
            MockMultipartFile workbook
    ) throws Exception {
        Cookie csrfCookie = csrfCookie(authentication);
        return multipart(ADMIN_TEMPLATE_IMPORT_PATH, courseId)
                .file(workbook)
                .with(authentication(authentication))
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue());
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        return csrfCookie;
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
            String[] columns = {"Class", "StudentCode", "Email", "MemberCode", "FullName", "Group", "Leader"};
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

    private MockMultipartFile groupedTemplate(byte[] templateBytes, String group, String leader) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            var row = sheet.getRow(1);
            row.getCell(5).setCellValue(group);
            row.getCell(6).setCellValue(leader);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "students-grouped.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private MockMultipartFile adminTemplateWorkbook(AdminTemplateRow... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Danh_Sach_SV");
            var header = sheet.createRow(0);
            String[] columns = {"Class", "StudentCode", "Email", "MemberCode", "FullName"};
            for (int index = 0; index < columns.length; index++) {
                header.createCell(index).setCellValue(columns[index]);
            }
            for (int index = 0; index < rows.length; index++) {
                AdminTemplateRow student = rows[index];
                var row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue("SE");
                row.createCell(1).setCellValue(student.studentCode());
                row.createCell(2).setCellValue(student.email());
                row.createCell(3).setCellValue(student.studentCode().toLowerCase());
                row.createCell(4).setCellValue("Imported Student");
            }
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "students-admin-template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray()
            );
        }
    }

    private StudentRow row(String studentCode, String email, String group, String leader) {
        return new StudentRow(studentCode, email, group, leader);
    }

    private AdminTemplateRow adminRow(String studentCode, String email) {
        return new AdminTemplateRow(studentCode, email);
    }

    private record StudentRow(String studentCode, String email, String group, String leader) {
    }

    private record AdminTemplateRow(String studentCode, String email) {
    }

    private record MasterDataRequest(String path, String body) {
    }
}
