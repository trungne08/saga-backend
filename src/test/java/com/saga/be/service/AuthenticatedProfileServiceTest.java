package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticatedProfileServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private LecturerRepository lecturerRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private AuthenticatedProfileService profileService;

    @Test
    void createsANewPendingStudentWithoutInventingAStudentCode() {
        String subject = "new-student-subject";
        String email = "student@fpt.edu.vn";
        UUID profileId = UUID.randomUUID();
        stubNoSubjectMatches(subject);
        stubNoEmailMatches(email);
        when(studentRepository.saveAndFlush(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(profileId);
            return student;
        });

        AuthenticatedProfile profile = profileService.synchronize(new AuthenticatedIdentity(
                subject,
                email,
                "New Student",
                ApplicationRole.STUDENT
        ));

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).saveAndFlush(studentCaptor.capture());
        Student savedStudent = studentCaptor.getValue();
        assertEquals(subject, savedStudent.getCognitoSub());
        assertEquals(email, savedStudent.getEmail());
        assertEquals("New Student", savedStudent.getFullName());
        assertNull(savedStudent.getStudentCode());
        assertEquals(AccountStatus.PENDING, savedStudent.getAccountStatus());
        assertEquals(profileId, profile.localProfileId());
        assertEquals(ApplicationRole.STUDENT, profile.role());
        assertEquals(AccountStatus.PENDING, profile.accountStatus());
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
        verify(lecturerRepository, never()).saveAndFlush(any(Lecturer.class));
    }

    @Test
    void linksAnExistingUnlinkedLecturerByEmail() {
        String subject = "lecturer-subject";
        String email = "lecturer@fpt.edu.vn";
        UUID profileId = UUID.randomUUID();
        Lecturer existing = Lecturer.builder()
                .email(email)
                .fullName("Old Name")
                .build();
        existing.setId(profileId);

        stubNoSubjectMatches(subject);
        when(adminRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(existing));
        when(studentRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(lecturerRepository.saveAndFlush(existing)).thenReturn(existing);

        AuthenticatedProfile profile = profileService.synchronize(new AuthenticatedIdentity(
                subject,
                email,
                "Updated Name",
                ApplicationRole.LECTURER
        ));

        verify(lecturerRepository).saveAndFlush(existing);
        assertEquals(subject, existing.getCognitoSub());
        assertEquals(email, existing.getEmail());
        assertEquals("Updated Name", existing.getFullName());
        assertEquals(profileId, profile.localProfileId());
        assertEquals(ApplicationRole.LECTURER, profile.role());
        assertNull(profile.accountStatus());
    }

    @Test
    void rejectsOneCognitoSubjectLinkedAcrossMultipleProfileTables() {
        String subject = "duplicate-subject";
        Admin admin = Admin.builder().cognitoSub(subject).build();
        Student student = Student.builder().cognitoSub(subject).build();
        when(adminRepository.findByCognitoSub(subject)).thenReturn(Optional.of(admin));
        when(lecturerRepository.findByCognitoSub(subject)).thenReturn(Optional.empty());
        when(studentRepository.findByCognitoSub(subject)).thenReturn(Optional.of(student));

        IdentityConflictException exception = assertThrows(
                IdentityConflictException.class,
                () -> profileService.synchronize(new AuthenticatedIdentity(
                        subject,
                        "user@fpt.edu.vn",
                        "Conflicting User",
                        ApplicationRole.ADMIN
                ))
        );

        assertEquals(
                "Cognito subject is linked to multiple profiles",
                exception.getMessage()
        );
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
        verify(lecturerRepository, never()).saveAndFlush(any(Lecturer.class));
        verify(studentRepository, never()).saveAndFlush(any(Student.class));
    }

    @Test
    void rejectsAnEmailAlreadyLinkedToAnotherCognitoSubject() {
        String subject = "new-subject";
        String email = "lecturer@fpt.edu.vn";
        Lecturer existing = Lecturer.builder()
                .cognitoSub("different-subject")
                .email(email)
                .fullName("Existing Lecturer")
                .build();

        stubNoSubjectMatches(subject);
        when(adminRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmailIgnoreCase(email))
                .thenReturn(Optional.of(existing));
        when(studentRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());

        IdentityConflictException exception = assertThrows(
                IdentityConflictException.class,
                () -> profileService.synchronize(new AuthenticatedIdentity(
                        subject,
                        email,
                        "Existing Lecturer",
                        ApplicationRole.LECTURER
                ))
        );

        assertEquals(
                "Email is already linked to another Cognito identity",
                exception.getMessage()
        );
        verify(lecturerRepository, never()).saveAndFlush(any(Lecturer.class));
    }

    private void stubNoSubjectMatches(String subject) {
        when(adminRepository.findByCognitoSub(subject)).thenReturn(Optional.empty());
        when(lecturerRepository.findByCognitoSub(subject)).thenReturn(Optional.empty());
        when(studentRepository.findByCognitoSub(subject)).thenReturn(Optional.empty());
    }

    private void stubNoEmailMatches(String email) {
        when(adminRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(lecturerRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(studentRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
    }
}
