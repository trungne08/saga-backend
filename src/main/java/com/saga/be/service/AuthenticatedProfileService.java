package com.saga.be.service;

import com.saga.be.auth.AuthenticatedIdentity;
import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.Admin;
import com.saga.be.entity.BaseEntity;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.exception.InvalidIdentityException;
import com.saga.be.exception.StudentCodeConflictException;
import com.saga.be.helper.StudentCodeExtractor;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedProfileService {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final StudentCodeExtractor studentCodeExtractor;
    private final EntityManager entityManager;

    public AuthenticatedProfileService(
            AdminRepository adminRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository,
            StudentCodeExtractor studentCodeExtractor,
            EntityManager entityManager
    ) {
        this.adminRepository = adminRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
        this.studentCodeExtractor = studentCodeExtractor;
        this.entityManager = entityManager;
    }

    @Transactional
    public AuthenticatedProfile synchronize(AuthenticatedIdentity identity) {
        String extractedStudentCode = extractRequiredStudentCode(identity);
        try {
            if (identity.role() == ApplicationRole.STUDENT) {
                return synchronizeStudentIdentity(identity, extractedStudentCode);
            }
            return synchronizeInternal(identity, extractedStudentCode);
        } catch (IdentityConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException(
                    "The Cognito identity conflicts with an existing local profile"
            );
        } catch (OptimisticLockingFailureException exception) {
            throw new IdentityConflictException(
                    "The imported Student identity was changed by another login"
            );
        } catch (DataAccessException exception) {
            throw new IdentityServiceException(
                    "The local identity store is unavailable",
                    exception
            );
        }
    }

    private String extractRequiredStudentCode(AuthenticatedIdentity identity) {
        if (identity.role() != ApplicationRole.STUDENT) {
            return null;
        }
        return studentCodeExtractor.extract(identity.email())
                .orElseThrow(() -> new InvalidIdentityException(
                        "A STUDENT email must end with a valid student code"
                ));
    }

    private AuthenticatedProfile synchronizeInternal(
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        List<ProfileReference> subjectMatches = findBySubject(identity.cognitoSub());
        requireAtMostOne(subjectMatches, "Cognito subject is linked to multiple profiles");

        if (!subjectMatches.isEmpty()) {
            ProfileReference match = subjectMatches.get(0);
            requireExpectedRole(match, identity.role());
            requireEmailAvailable(identity.email(), match);
            return update(match, identity, extractedStudentCode);
        }

        List<ProfileReference> emailMatches = findByEmail(identity.email());
        requireAtMostOne(emailMatches, "Email is linked to multiple profiles");

        if (!emailMatches.isEmpty()) {
            ProfileReference match = emailMatches.get(0);
            requireExpectedRole(match, identity.role());
            String existingSubject = cognitoSub(match.entity());
            if (existingSubject != null
                    && !existingSubject.isBlank()
                    && !existingSubject.equals(identity.cognitoSub())) {
                throw new IdentityConflictException(
                        "Email is already linked to another Cognito identity",
                        IdentityConflictException.Reason
                                .EMAIL_LINKED_TO_DIFFERENT_COGNITO_SUB
                );
            }
            return update(match, identity, extractedStudentCode);
        }

        return create(identity, extractedStudentCode);
    }

    /**
     * Imported Students are identified by the pair (normalized email, student code).
     * A partial match is deliberately not enough to bind a Cognito subject.
     */
    private AuthenticatedProfile synchronizeStudentIdentity(
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        List<ProfileReference> subjectMatches = findBySubject(identity.cognitoSub());
        requireAtMostOne(subjectMatches, "Cognito subject is linked to multiple profiles");
        if (!subjectMatches.isEmpty()) {
            ProfileReference match = subjectMatches.get(0);
            requireExpectedRole(match, ApplicationRole.STUDENT);
            return updateStudent((Student) match.entity(), identity, extractedStudentCode);
        }

        List<ProfileReference> emailMatches = findByEmail(identity.email());
        requireAtMostOne(emailMatches, "Email is linked to multiple profiles");
        Student studentByCode = studentRepository.findByStudentCodeIgnoreCase(extractedStudentCode)
                .orElse(null);

        if (emailMatches.isEmpty() && studentByCode == null) {
            return create(identity, extractedStudentCode);
        }
        if (emailMatches.isEmpty()) {
            throw new IdentityConflictException(
                    "Student code matches an existing Student but the email does not"
            );
        }

        ProfileReference emailMatch = emailMatches.get(0);
        requireExpectedRole(emailMatch, ApplicationRole.STUDENT);
        Student studentByEmail = (Student) emailMatch.entity();
        if (studentByCode == null) {
            throw new IdentityConflictException(
                    "Student email matches an existing Student but the student code does not"
            );
        }
        if (!studentByEmail.getId().equals(studentByCode.getId())) {
            throw new IdentityConflictException(
                    "Student email and student code identify different local Students"
            );
        }
        return bindImportedStudent(studentByEmail.getId(), identity, extractedStudentCode);
    }

    private AuthenticatedProfile bindImportedStudent(
            UUID studentId,
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        Student student = studentRepository.findForIdentityBindingById(studentId)
                .orElseThrow(() -> new IdentityConflictException(
                        "The imported Student profile no longer exists"
                ));
        entityManager.refresh(student, LockModeType.PESSIMISTIC_WRITE);

        List<ProfileReference> subjectMatches = findBySubject(identity.cognitoSub());
        requireAtMostOne(subjectMatches, "Cognito subject is linked to multiple profiles");
        if (!subjectMatches.isEmpty() && !sameStudent(subjectMatches.get(0), student)) {
            throw new IdentityConflictException(
                    "The Cognito subject is already linked to another local profile"
            );
        }

        String existingSubject = student.getCognitoSub();
        if (existingSubject != null && !existingSubject.isBlank()) {
            if (!existingSubject.equals(identity.cognitoSub())) {
                throw new IdentityConflictException(
                        "The imported Student is already linked to another Cognito identity",
                        IdentityConflictException.Reason.EMAIL_LINKED_TO_DIFFERENT_COGNITO_SUB
                );
            }
            return toProfile(student);
        }

        if (!student.getEmail().equalsIgnoreCase(identity.email())
                || !student.getStudentCode().equalsIgnoreCase(extractedStudentCode)) {
            throw new IdentityConflictException(
                    "The imported Student identity no longer matches the Cognito identity"
            );
        }
        if (student.getAccountStatus() == AccountStatus.INACTIVE
                || student.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw new IdentityConflictException(
                    "The imported Student account cannot be activated"
            );
        }

        student.setCognitoSub(identity.cognitoSub());
        if (student.getAccountStatus() == AccountStatus.PENDING) {
            student.setAccountStatus(AccountStatus.ACTIVE);
        }
        Student saved = studentRepository.saveAndFlush(student);
        return toProfile(saved);
    }

    private boolean sameStudent(ProfileReference reference, Student student) {
        return reference.role() == ApplicationRole.STUDENT
                && Objects.equals(reference.entity().getId(), student.getId());
    }

    private List<ProfileReference> findBySubject(String subject) {
        List<ProfileReference> matches = new ArrayList<>();
        adminRepository.findByCognitoSub(subject)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.ADMIN, entity)));
        lecturerRepository.findByCognitoSub(subject)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.LECTURER, entity)));
        studentRepository.findByCognitoSub(subject)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.STUDENT, entity)));
        return matches;
    }

    private List<ProfileReference> findByEmail(String email) {
        List<ProfileReference> matches = new ArrayList<>();
        adminRepository.findByEmailIgnoreCase(email)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.ADMIN, entity)));
        lecturerRepository.findByEmailIgnoreCase(email)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.LECTURER, entity)));
        studentRepository.findByEmailIgnoreCase(email)
                .ifPresent(entity -> matches.add(reference(ApplicationRole.STUDENT, entity)));
        return matches;
    }

    private void requireAtMostOne(List<ProfileReference> matches, String message) {
        if (matches.size() > 1) {
            throw new IdentityConflictException(message);
        }
    }

    private void requireExpectedRole(ProfileReference profile, ApplicationRole expectedRole) {
        if (profile.role() != expectedRole) {
            throw new IdentityConflictException(
                    "The local profile type does not match the Cognito application role"
            );
        }
    }

    private void requireEmailAvailable(String email, ProfileReference currentProfile) {
        List<ProfileReference> matches = findByEmail(email);
        for (ProfileReference match : matches) {
            if (!sameProfile(match, currentProfile)) {
                throw new IdentityConflictException(
                        "Email is already linked to another local profile"
                );
            }
        }
    }

    private boolean sameProfile(ProfileReference left, ProfileReference right) {
        return left.role() == right.role()
                && Objects.equals(left.entity().getId(), right.entity().getId());
    }

    private AuthenticatedProfile update(
            ProfileReference profile,
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        return switch (profile.role()) {
            case ADMIN -> updateAdmin((Admin) profile.entity(), identity);
            case LECTURER -> updateLecturer((Lecturer) profile.entity(), identity);
            case STUDENT -> updateStudent(
                    (Student) profile.entity(),
                    identity,
                    extractedStudentCode
            );
        };
    }

    private AuthenticatedProfile updateAdmin(Admin admin, AuthenticatedIdentity identity) {
        admin.setCognitoSub(identity.cognitoSub());
        admin.setEmail(identity.email());
        admin.setFullName(identity.fullName());
        Admin saved = adminRepository.saveAndFlush(admin);
        return toProfile(saved);
    }

    private AuthenticatedProfile updateLecturer(
            Lecturer lecturer,
            AuthenticatedIdentity identity
    ) {
        lecturer.setCognitoSub(identity.cognitoSub());
        lecturer.setEmail(identity.email());
        lecturer.setFullName(identity.fullName());
        if (lecturer.getAccountStatus() == null) {
            lecturer.setAccountStatus(AccountStatus.ACTIVE);
        }
        Lecturer saved = lecturerRepository.saveAndFlush(lecturer);
        return toProfile(saved);
    }

    private AuthenticatedProfile updateStudent(
            Student student,
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        synchronizeStudentCode(student, identity, extractedStudentCode);
        student.setCognitoSub(identity.cognitoSub());
        student.setEmail(identity.email());
        student.setFullName(identity.fullName());
        if (student.getAccountStatus() == null) {
            student.setAccountStatus(AccountStatus.PENDING);
        }
        Student saved = studentRepository.saveAndFlush(student);
        return toProfile(saved);
    }

    private void synchronizeStudentCode(
            Student student,
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        String storedStudentCode = student.getStudentCode();
        if (storedStudentCode == null || storedStudentCode.isBlank()) {
            student.setStudentCode(extractedStudentCode);
            return;
        }
        if (!storedStudentCode.equalsIgnoreCase(extractedStudentCode)) {
            throw new StudentCodeConflictException(
                    identity.cognitoSub(),
                    student.getId()
            );
        }
    }

    private AuthenticatedProfile create(
            AuthenticatedIdentity identity,
            String extractedStudentCode
    ) {
        return switch (identity.role()) {
            case ADMIN -> {
                Admin admin = Admin.builder()
                        .cognitoSub(identity.cognitoSub())
                        .email(identity.email())
                        .fullName(identity.fullName())
                        .build();
                yield toProfile(adminRepository.saveAndFlush(admin));
            }
            case LECTURER -> {
                Lecturer lecturer = Lecturer.builder()
                        .cognitoSub(identity.cognitoSub())
                        .email(identity.email())
                        .fullName(identity.fullName())
                        .accountStatus(AccountStatus.ACTIVE)
                        .build();
                yield toProfile(lecturerRepository.saveAndFlush(lecturer));
            }
            case STUDENT -> {
                Student student = Student.builder()
                        .cognitoSub(identity.cognitoSub())
                        .email(identity.email())
                        .fullName(identity.fullName())
                        .studentCode(extractedStudentCode)
                        .accountStatus(AccountStatus.PENDING)
                        .build();
                yield toProfile(studentRepository.saveAndFlush(student));
            }
        };
    }

    private AuthenticatedProfile toProfile(Admin admin) {
        return profile(
                admin.getCognitoSub(),
                admin.getEmail(),
                admin.getFullName(),
                ApplicationRole.ADMIN,
                admin.getId(),
                null
        );
    }

    private AuthenticatedProfile toProfile(Lecturer lecturer) {
        return profile(
                lecturer.getCognitoSub(),
                lecturer.getEmail(),
                lecturer.getFullName(),
                ApplicationRole.LECTURER,
                lecturer.getId(),
                lecturer.getAccountStatus()
        );
    }

    private AuthenticatedProfile toProfile(Student student) {
        return profile(
                student.getCognitoSub(),
                student.getEmail(),
                student.getFullName(),
                ApplicationRole.STUDENT,
                student.getId(),
                student.getAccountStatus()
        );
    }

    private AuthenticatedProfile profile(
            String cognitoSub,
            String email,
            String fullName,
            ApplicationRole role,
            UUID id,
            AccountStatus status
    ) {
        return new AuthenticatedProfile(cognitoSub, email, fullName, role, id, status);
    }

    private String cognitoSub(BaseEntity entity) {
        if (entity instanceof Admin admin) {
            return admin.getCognitoSub();
        }
        if (entity instanceof Lecturer lecturer) {
            return lecturer.getCognitoSub();
        }
        return ((Student) entity).getCognitoSub();
    }

    private ProfileReference reference(ApplicationRole role, BaseEntity entity) {
        return new ProfileReference(role, entity);
    }

    private record ProfileReference(ApplicationRole role, BaseEntity entity) {
    }
}
