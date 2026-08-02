package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.saga.be.repository.StudentCourseInvitationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(OAuth2TestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportedStudentProvisioningIntegrationTest {

    private static final String EMAIL = "alicese170506@example.test";

    @Autowired
    private AuthenticatedProfileService profileService;
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
    void bindsOneImportedStudentAndKeepsMembershipsAndRolesAcrossCourses() {
        Student student = importedStudent(AccountStatus.PENDING);
        Team leaderTeam = team("Course A", student, RoleInTeam.LEADER);
        Team memberTeam = team("Course B", student, RoleInTeam.MEMBER);

        AuthenticatedProfile profile = profileService.synchronize(identity("first-login-subject"));

        Student bound = studentRepository.findById(student.getId()).orElseThrow();
        assertEquals(student.getId(), profile.localProfileId());
        assertEquals("first-login-subject", bound.getCognitoSub());
        assertEquals(AccountStatus.ACTIVE, bound.getAccountStatus());
        assertEquals(1, studentRepository.count());
        assertEquals(2, teamMemberRepository.findByStudentId(student.getId()).size());
        assertEquals(RoleInTeam.LEADER, teamMemberRepository.findByTeamIdAndStudentId(
                leaderTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());
        assertEquals(RoleInTeam.MEMBER, teamMemberRepository.findByTeamIdAndStudentId(
                memberTeam.getId(), student.getId()).orElseThrow().getRoleInTeam());

        AuthenticatedProfile repeat = profileService.synchronize(identity("first-login-subject"));
        assertEquals(profile.localProfileId(), repeat.localProfileId());
        assertEquals(1, studentRepository.count());
        assertEquals(2, teamMemberRepository.findByStudentId(student.getId()).size());
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
    void createsPendingStudentWithoutMembershipWhenNoImportedIdentityMatches() {
        AuthenticatedProfile profile = profileService.synchronize(new AuthenticatedIdentity(
                "new-subject",
                "newse170507@example.test",
                "New Student",
                ApplicationRole.STUDENT
        ));

        Student created = studentRepository.findById(profile.localProfileId()).orElseThrow();
        assertEquals(AccountStatus.PENDING, created.getAccountStatus());
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
        Course course = courseRepository.save(Course.builder()
                .courseCode("COURSE-" + UUID.randomUUID())
                .name(courseName)
                .build());
        Team team = teamRepository.save(Team.builder().course(course).name(courseName + " Team").build());
        teamMemberRepository.save(TeamMember.builder()
                .team(team).student(student).roleInTeam(role).build());
        return team;
    }

    private AuthenticatedIdentity identity(String subject) {
        return new AuthenticatedIdentity(subject, EMAIL, "Cognito Student", ApplicationRole.STUDENT);
    }
}
