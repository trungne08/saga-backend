package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saga.be.OAuth2TestConfiguration;
import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.auth.AuthenticatedProfile;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportedStudentProvisioningIntegrationTest {

    private static final String EMAIL = "alicese170506@example.test";

    @Autowired
    private AuthenticatedProfileService profileService;
    @Autowired
    private ExcelImportService excelImportService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private LecturerRepository lecturerRepository;
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

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        invitationRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        studentRepository.deleteAll();
        courseRepository.deleteAll();
        lecturerRepository.deleteAll();
    }

    @Test
    void loginFirstCreatesActiveStudentThenImportReusesItAndMakesTheCourseVisible() throws Exception {
        String subject = "register-first-subject";
        AuthenticatedProfile registered = profileService.synchronize(identity(subject));
        Student beforeImport = studentRepository.findById(registered.localProfileId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, beforeImport.getAccountStatus());
        assertEquals(subject, beforeImport.getCognitoSub());
        assertEquals(0, teamMemberRepository.count());
        mockMvc.perform(get("/api/v1/subjects").session(session(registered)))
                .andExpect(status().isOk());

        Course course = course("Register First");
        excelImportService.importStudentsToCourse(
                admin(),
                course.getId(),
                workbook(EMAIL, "SE170506", "1", "x")
        );

        Student provisioned = studentRepository.findById(registered.localProfileId()).orElseThrow();
        TeamMember membership = teamMemberRepository.findByStudentId(provisioned.getId()).get(0);
        assertEquals(registered.localProfileId(), provisioned.getId());
        assertEquals(AccountStatus.ACTIVE, provisioned.getAccountStatus());
        assertEquals(1, studentRepository.count());
        assertEquals(1, teamMemberRepository.count());
        assertEquals(RoleInTeam.LEADER, membership.getRoleInTeam());
        assertEquals(1, invitationRepository.count());

        AuthenticatedProfile repeatedLogin = profileService.synchronize(identity(subject));
        assertEquals(AccountStatus.ACTIVE, repeatedLogin.accountStatus());
        assertEquals(registered.localProfileId(), repeatedLogin.localProfileId());
        assertEquals(1, studentRepository.count());
        assertEquals(1, teamMemberRepository.count());

        mockMvc.perform(get("/api/me/courses/{courseId}/team/members", course.getId())
                        .session(session(repeatedLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andExpect(jsonPath("$.teamId").value(membership.getTeam().getId().toString()))
                .andExpect(jsonPath("$.roleInTeam").value("LEADER"));
    }

    @Test
    void importFirstCreatesPendingMembershipAndInvitationThenExactLoginActivatesSameStudent()
            throws Exception {
        Course course = course("Import First");
        excelImportService.importStudentsToCourse(
                admin(),
                course.getId(),
                workbook(EMAIL, "SE170506", "2", "")
        );

        Student imported = studentRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        UUID importedId = imported.getId();
        assertEquals(AccountStatus.PENDING, imported.getAccountStatus());
        assertNull(imported.getCognitoSub());
        assertEquals(1, teamMemberRepository.findByStudentId(importedId).size());
        assertEquals(RoleInTeam.MEMBER, teamMemberRepository.findByStudentId(importedId)
                .get(0).getRoleInTeam());
        assertEquals(1, invitationRepository.count());

        AuthenticatedProfile authenticated = profileService.synchronize(identity("import-first-subject"));

        Student activated = studentRepository.findById(importedId).orElseThrow();
        assertEquals(importedId, authenticated.localProfileId());
        assertEquals(importedId, activated.getId());
        assertEquals("import-first-subject", activated.getCognitoSub());
        assertEquals(AccountStatus.ACTIVE, activated.getAccountStatus());
        assertEquals(1, studentRepository.count());
        assertEquals(1, teamMemberRepository.findByStudentId(importedId).size());
        assertEquals(RoleInTeam.MEMBER, teamMemberRepository.findByStudentId(importedId)
                .get(0).getRoleInTeam());
        mockMvc.perform(get("/api/me/courses/{courseId}/team/members", course.getId())
                        .session(session(authenticated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andExpect(jsonPath("$.roleInTeam").value("MEMBER"));
    }

    @Test
    void existingLinkedPendingStudentWithMembershipRecoversOnLogin() {
        Student student = linkedStudent("legacyse170508@example.test", "SE170508", AccountStatus.PENDING,
                "legacy-linked-subject");
        Team leaderTeam = team("Legacy Course A", student, RoleInTeam.LEADER);
        Team memberTeam = team("Legacy Course B", student, RoleInTeam.MEMBER);

        AuthenticatedProfile recovered = profileService.synchronize(new AuthenticatedIdentity(
                student.getCognitoSub(), student.getEmail(), "Recovered Student", ApplicationRole.STUDENT
        ));

        Student reloaded = studentRepository.findById(student.getId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, recovered.accountStatus());
        assertEquals(AccountStatus.ACTIVE, reloaded.getAccountStatus());
        assertEquals(2, teamMemberRepository.findByStudentId(student.getId()).size());
        assertEquals(RoleInTeam.LEADER, teamMemberRepository.findByTeamIdAndStudentId(
                leaderTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());
        assertEquals(RoleInTeam.MEMBER, teamMemberRepository.findByTeamIdAndStudentId(
                memberTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());
    }

    @Test
    void legacyLinkedPendingStudentWithoutMembershipRecoversOnSameIdentityLogin() throws Exception {
        Student legacy = linkedStudent(
                "nomembershipse170509@example.test",
                "SE170509",
                AccountStatus.PENDING,
                "no-membership-subject"
        );

        AuthenticatedProfile recovered = profileService.synchronize(new AuthenticatedIdentity(
                legacy.getCognitoSub(), legacy.getEmail(), "No Membership", ApplicationRole.STUDENT
        ));

        assertEquals(legacy.getId(), recovered.localProfileId());
        assertEquals(AccountStatus.ACTIVE, recovered.accountStatus());
        assertEquals(AccountStatus.ACTIVE, studentRepository.findById(legacy.getId())
                .orElseThrow().getAccountStatus());
        assertEquals(0, teamMemberRepository.count());
        mockMvc.perform(get("/api/v1/subjects").session(session(recovered)))
                .andExpect(status().isOk());
    }

    @Test
    void existingSubjectLoginNeverReactivatesInactiveOrSuspendedStudent() throws Exception {
        assertExistingSubjectStatusIsPreserved("inactivese170510@example.test", "SE170510",
                "inactive-linked-subject", AccountStatus.INACTIVE);
        assertExistingSubjectStatusIsPreserved("suspendedse170511@example.test", "SE170511",
                "suspended-linked-subject", AccountStatus.SUSPENDED);
    }

    @Test
    void courseProvisioningDoesNotReactivateInactiveOrSuspendedStudent() throws Exception {
        assertCourseProvisioningPreservesStatus(
                "import-inactivese170513@example.test",
                "SE170513",
                "import-inactive-subject",
                AccountStatus.INACTIVE
        );
        assertCourseProvisioningPreservesStatus(
                "import-suspendedse170514@example.test",
                "SE170514",
                "import-suspended-subject",
                AccountStatus.SUSPENDED
        );
    }

    @Test
    void bindsOneImportedStudentAndKeepsMembershipsAndRolesAcrossCourses() {
        Student student = importedStudent(AccountStatus.PENDING);
        Team leaderTeam = team("Course A", student, RoleInTeam.LEADER);
        Team memberTeam = team("Course B", student, RoleInTeam.MEMBER);
        Team mentorTeam = team("Course C", student, RoleInTeam.MENTOR);

        AuthenticatedProfile profile = profileService.synchronize(identity("first-login-subject"));

        Student bound = studentRepository.findById(student.getId()).orElseThrow();
        assertEquals(student.getId(), profile.localProfileId());
        assertEquals("first-login-subject", bound.getCognitoSub());
        assertEquals(AccountStatus.ACTIVE, bound.getAccountStatus());
        assertEquals(1, studentRepository.count());
        assertEquals(3, teamMemberRepository.findByStudentId(student.getId()).size());
        assertEquals(RoleInTeam.LEADER, teamMemberRepository.findByTeamIdAndStudentId(
                leaderTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());
        assertEquals(RoleInTeam.MEMBER, teamMemberRepository.findByTeamIdAndStudentId(
                memberTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());
        assertEquals(RoleInTeam.MENTOR, teamMemberRepository.findByTeamIdAndStudentId(
                mentorTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());

        AuthenticatedProfile repeat = profileService.synchronize(identity("first-login-subject"));
        assertEquals(profile.localProfileId(), repeat.localProfileId());
        assertEquals(1, studentRepository.count());
        assertEquals(3, teamMemberRepository.findByStudentId(student.getId()).size());
    }

    @Test
    void rejectsCognitoSubjectAlreadyOwnedByAnotherProfile() {
        importedStudent(AccountStatus.PENDING);
        lecturerRepository.save(Lecturer.builder()
                .cognitoSub("already-a-lecturer")
                .email("lecturer@example.test")
                .fullName("Lecturer")
                .build());

        assertThrows(IdentityConflictException.class, () -> profileService.synchronize(
                identity("already-a-lecturer")
        ));
        assertEquals(1, studentRepository.count());
    }

    @Test
    void createsActiveStudentWithoutMembershipWhenNoImportedIdentityMatches() {
        AuthenticatedProfile profile = profileService.synchronize(new AuthenticatedIdentity(
                "new-subject",
                "newse170507@example.test",
                "New Student",
                ApplicationRole.STUDENT
        ));

        Student created = studentRepository.findById(profile.localProfileId()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, created.getAccountStatus());
        assertEquals("new-subject", created.getCognitoSub());
        assertEquals(0, teamMemberRepository.count());
        assertEquals(0, teamRepository.count());
        assertEquals(0, courseRepository.count());
    }

    @Test
    void concurrentFirstLoginsCannotBindTwoSubjectsToOneImportedStudent() throws Exception {
        Student student = importedStudent(AccountStatus.PENDING);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> attemptBind(
                    ready, start, "concurrent-subject-one"
            ));
            Future<Boolean> second = executor.submit(() -> attemptBind(
                    ready, start, "concurrent-subject-two"
            ));
            ready.await();
            start.countDown();

            assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
            Student bound = studentRepository.findById(student.getId()).orElseThrow();
            assertEquals(AccountStatus.ACTIVE, bound.getAccountStatus());
            org.junit.jupiter.api.Assertions.assertTrue(
                    java.util.Set.of("concurrent-subject-one", "concurrent-subject-two")
                            .contains(bound.getCognitoSub())
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptBind(CountDownLatch ready, CountDownLatch start, String subject)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            profileService.synchronize(identity(subject));
            return true;
        } catch (IdentityConflictException exception) {
            return false;
        }
    }

    private Student importedStudent(AccountStatus status) {
        return studentRepository.save(Student.builder()
                .email(EMAIL)
                .studentCode("SE170506")
                .fullName("Imported Student")
                .accountStatus(status)
                .build());
    }

    private Team team(String courseName, Student student, RoleInTeam role) {
        Course course = course(courseName);
        Team team = teamRepository.save(Team.builder().course(course).name(courseName + " Team").build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).student(student).roleInTeam(role).build());
        return team;
    }

    private AuthenticatedIdentity identity(String subject) {
        return new AuthenticatedIdentity(subject, EMAIL, "Cognito Student", ApplicationRole.STUDENT);
    }

    private Student linkedStudent(String email, String studentCode, AccountStatus status, String subject) {
        return studentRepository.saveAndFlush(Student.builder()
                .email(email)
                .studentCode(studentCode)
                .fullName("Linked Student")
                .accountStatus(status)
                .cognitoSub(subject)
                .build());
    }

    private Course course(String name) {
        return courseRepository.saveAndFlush(Course.builder()
                .courseCode("COURSE-" + UUID.randomUUID())
                .name(name)
                .build());
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

    private MockMultipartFile workbook(
            String email,
            String studentCode,
            String group,
            String leader
    ) throws Exception {
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
            row.createCell(1).setCellValue(studentCode);
            row.createCell(2).setCellValue(email);
            row.createCell(3).setCellValue("Member A");
            row.createCell(4).setCellValue("Imported Student");
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

    private MockHttpSession session(AuthenticatedProfile profile) {
        SagaPrincipal principal = SagaPrincipal.from(profile);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private void assertExistingSubjectStatusIsPreserved(
            String email,
            String studentCode,
            String subject,
            AccountStatus status
    ) throws Exception {
        Student student = linkedStudent(email, studentCode, status, subject);
        team("Status " + status, student, RoleInTeam.MEMBER);

        AuthenticatedProfile profile = profileService.synchronize(new AuthenticatedIdentity(
                subject, email, "Status Student", ApplicationRole.STUDENT
        ));

        assertEquals(status, profile.accountStatus());
        assertEquals(status, studentRepository.findById(student.getId()).orElseThrow().getAccountStatus());
        mockMvc.perform(get("/api/v1/subjects").session(session(profile)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_STATUS_ACCESS_DENIED"));
    }

    private void assertCourseProvisioningPreservesStatus(
            String email,
            String studentCode,
            String subject,
            AccountStatus status
    ) throws Exception {
        Student student = linkedStudent(email, studentCode, status, subject);
        Course course = course("Import " + status);

        excelImportService.importStudentsToCourse(
                admin(),
                course.getId(),
                workbook(email, studentCode, status.name(), "")
        );

        assertEquals(status, studentRepository.findById(student.getId()).orElseThrow().getAccountStatus());
        assertEquals(1, teamMemberRepository.findByStudentId(student.getId()).size());
    }
}
