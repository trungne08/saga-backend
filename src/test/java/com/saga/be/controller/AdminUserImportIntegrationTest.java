package com.saga.be.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticatedProfileService;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class AdminUserImportIntegrationTest {

    private static final String PATH = "/api/admin/users/import";

    @Autowired private MockMvc mockMvc;
    @Autowired private StudentRepository studentRepository;
    @Autowired private LecturerRepository lecturerRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private StudentCourseInvitationRepository invitationRepository;
    @Autowired private AuthenticatedProfileService profileService;

    @Test
    void adminImportsStudentsWithoutCourseTeamOrInvitationAndFirstLoginBindsExactIdentity() throws Exception {
        performImport(ApplicationRole.ADMIN, "STUDENT", workbook(
                new String[] {"studentCode", "email", "fullName"},
                new String[] {" se170506 ", "ALICESE170506@example.test ", "Alice Student"}
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.reusedCount").value(0));

        Student imported = studentRepository.findByStudentCodeIgnoreCase("SE170506").orElseThrow();
        assertEquals("alicese170506@example.test", imported.getEmail());
        assertEquals(AccountStatus.PENDING, imported.getAccountStatus());
        assertEquals(0, courseRepository.count());
        assertEquals(0, teamRepository.count());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, invitationRepository.count());

        profileService.synchronize(new AuthenticatedIdentity(
                "student-first-login", "alicese170506@example.test", "Alice Cognito", ApplicationRole.STUDENT
        ));
        Student bound = studentRepository.findById(imported.getId()).orElseThrow();
        assertEquals("student-first-login", bound.getCognitoSub());
        assertEquals(AccountStatus.ACTIVE, bound.getAccountStatus());
    }

    @Test
    void adminImportsLecturersAsActiveAndFirstLoginBindsByEmailWithoutMutatingExistingDetails() throws Exception {
        performImport(ApplicationRole.ADMIN, "LECTURER", workbook(
                new String[] {"email", "fullName"},
                new String[] {"lecturer@example.test", "Imported Lecturer"}
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LECTURER"))
                .andExpect(jsonPath("$.createdCount").value(1));

        Lecturer imported = lecturerRepository.findByEmailIgnoreCase("lecturer@example.test").orElseThrow();
        assertEquals(AccountStatus.ACTIVE, imported.getAccountStatus());
        assertEquals(0, courseRepository.count());
        profileService.synchronize(new AuthenticatedIdentity(
                "lecturer-first-login", "lecturer@example.test", "Cognito Lecturer", ApplicationRole.LECTURER
        ));
        assertEquals("lecturer-first-login", lecturerRepository.findById(imported.getId()).orElseThrow().getCognitoSub());

        Lecturer existing = lecturerRepository.saveAndFlush(Lecturer.builder()
                .cognitoSub("existing-sub").email("existing@example.test").fullName("Keep Name")
                .accountStatus(AccountStatus.SUSPENDED).build());
        performImport(ApplicationRole.ADMIN, "LECTURER", workbook(
                new String[] {"email", "fullName"}, new String[] {"existing@example.test", "Replace Name"}
        )).andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0))
                .andExpect(jsonPath("$.reusedCount").value(1));
        Lecturer reused = lecturerRepository.findById(existing.getId()).orElseThrow();
        assertEquals("Keep Name", reused.getFullName());
        assertEquals(AccountStatus.SUSPENDED, reused.getAccountStatus());
        assertEquals("existing-sub", reused.getCognitoSub());
    }

    @Test
    void invalidHeadersRequiredCellsFormulasAndDuplicateRowsFailBeforeAnyWrite() throws Exception {
        performImport(ApplicationRole.ADMIN, "STUDENT", workbook(
                new String[] {"email", "studentCode", "fullName"}, new String[] {"SE170507", "bad@test", "Bad"}
        )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REQUEST"));
        performImport(ApplicationRole.ADMIN, "LECTURER", workbook(
                new String[] {"email", "fullName"}, new String[] {"", "Missing Email"}
        )).andExpect(status().isBadRequest());
        performImport(ApplicationRole.ADMIN, "STUDENT", formulaWorkbook())
                .andExpect(status().isBadRequest());
        performImport(ApplicationRole.ADMIN, "STUDENT", workbook(
                new String[] {"studentCode", "email", "fullName"},
                new String[] {"SE170508", "duplicate@test", "One"},
                new String[] {"SE170508", "duplicate@test", "Two"}
        )).andExpect(status().isBadRequest());
        performImport(ApplicationRole.ADMIN, "STUDENT", workbook(
                new String[] {"studentCode", "email", "fullName"},
                new String[] {"SE170511", "partial-duplicate@test", "One"},
                new String[] {"SE170512", "partial-duplicate@test", "Two"}
        )).andExpect(status().isBadRequest());
        assertEquals(0, studentRepository.count());
        assertEquals(0, lecturerRepository.count());
    }

    @Test
    void partialStudentIdentityAndCrossRoleEmailConflictsAreRejectedWithoutPartialImport() throws Exception {
        studentRepository.saveAndFlush(Student.builder().studentCode("SE170509").email("existing@test")
                .fullName("Existing").accountStatus(AccountStatus.PENDING).build());
        performImport(ApplicationRole.ADMIN, "STUDENT", workbook(
                new String[] {"studentCode", "email", "fullName"}, new String[] {"SE170509", "other@test", "Conflict"}
        )).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("BUSINESS_CONFLICT"));

        adminRepository.saveAndFlush(Admin.builder().cognitoSub("admin-sub").email("admin@test").fullName("Admin").build());
        performImport(ApplicationRole.ADMIN, "LECTURER", workbook(
                new String[] {"email", "fullName"}, new String[] {"admin@test", "Conflict"}
        )).andExpect(status().isConflict());
        assertEquals(1, studentRepository.count());
        assertEquals(0, lecturerRepository.count());
    }

    @Test
    void endpointRequiresAdminAndCsrfAndRejectsUnsupportedRole() throws Exception {
        MockMultipartFile file = workbook(new String[] {"email", "fullName"}, new String[] {"safe@test", "Safe"});
        Cookie anonymousCsrf = csrfCookie(authenticationFor(ApplicationRole.ADMIN));
        mockMvc.perform(multipart(PATH).file(file).param("role", "LECTURER")
                        .cookie(anonymousCsrf).header("X-XSRF-TOKEN", anonymousCsrf.getValue()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(PATH).file(file).param("role", "LECTURER")
                        .with(authentication(authenticationFor(ApplicationRole.LECTURER))))
                .andExpect(status().isForbidden());
        performImport(ApplicationRole.ADMIN, "ADMIN", file).andExpect(status().isBadRequest());
        assertEquals(0, lecturerRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions performImport(
            ApplicationRole callerRole, String importRole, MockMultipartFile file
    ) throws Exception {
        Authentication authentication = authenticationFor(callerRole);
        Cookie csrf = csrfCookie(authentication);
        return mockMvc.perform(multipart(PATH).file(file).param("role", importRole)
                .with(authentication(authentication)).cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()));
    }

    private Cookie csrfCookie(Authentication authentication) throws Exception {
        Cookie csrf = mockMvc.perform(get("/api/auth/csrf").with(authentication(authentication)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(csrf);
        return csrf;
    }

    private Authentication authenticationFor(ApplicationRole role) {
        SagaPrincipal principal = new SagaPrincipal(role.name().toLowerCase() + "-sub", role.name().toLowerCase()
                + "@test", role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private MockMultipartFile workbook(String[] headers, String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Users");
            writeRow(sheet.createRow(0), headers);
            for (int index = 0; index < rows.length; index++) {
                writeRow(sheet.createRow(index + 1), rows[index]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile formulaWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Users");
            writeRow(sheet.createRow(0), new String[] {"studentCode", "email", "fullName"});
            var row = sheet.createRow(1);
            row.createCell(0).setCellFormula("\"SE170510\"");
            row.createCell(1).setCellValue("formula@test");
            row.createCell(2).setCellValue("Formula");
            workbook.write(output);
            return new MockMultipartFile("file", "users.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private void writeRow(org.apache.poi.ss.usermodel.Row row, String[] values) {
        for (int index = 0; index < values.length; index++) {
            row.createCell(index, CellType.STRING).setCellValue(values[index]);
        }
    }
}
