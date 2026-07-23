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
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedProfileService {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;

    public AuthenticatedProfileService(
            AdminRepository adminRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository
    ) {
        this.adminRepository = adminRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional
    public AuthenticatedProfile synchronize(AuthenticatedIdentity identity) {
        try {
            return synchronizeInternal(identity);
        } catch (IdentityConflictException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException(
                    "The Cognito identity conflicts with an existing local profile"
            );
        } catch (DataAccessException exception) {
            throw new IdentityServiceException(
                    "The local identity store is unavailable",
                    exception
            );
        }
    }

    private AuthenticatedProfile synchronizeInternal(AuthenticatedIdentity identity) {
        List<ProfileReference> subjectMatches = findBySubject(identity.cognitoSub());
        requireAtMostOne(subjectMatches, "Cognito subject is linked to multiple profiles");

        if (!subjectMatches.isEmpty()) {
            ProfileReference match = subjectMatches.get(0);
            requireExpectedRole(match, identity.role());
            requireEmailAvailable(identity.email(), match);
            return update(match, identity);
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
                        "Email is already linked to another Cognito identity"
                );
            }
            return update(match, identity);
        }

        return create(identity);
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
            AuthenticatedIdentity identity
    ) {
        return switch (profile.role()) {
            case ADMIN -> updateAdmin((Admin) profile.entity(), identity);
            case LECTURER -> updateLecturer((Lecturer) profile.entity(), identity);
            case STUDENT -> updateStudent((Student) profile.entity(), identity);
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
        Lecturer saved = lecturerRepository.saveAndFlush(lecturer);
        return toProfile(saved);
    }

    private AuthenticatedProfile updateStudent(
            Student student,
            AuthenticatedIdentity identity
    ) {
        student.setCognitoSub(identity.cognitoSub());
        student.setEmail(identity.email());
        student.setFullName(identity.fullName());
        if (student.getAccountStatus() == null) {
            student.setAccountStatus(AccountStatus.PENDING);
        }
        Student saved = studentRepository.saveAndFlush(student);
        return toProfile(saved);
    }

    private AuthenticatedProfile create(AuthenticatedIdentity identity) {
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
                        .build();
                yield toProfile(lecturerRepository.saveAndFlush(lecturer));
            }
            case STUDENT -> {
                Student student = Student.builder()
                        .cognitoSub(identity.cognitoSub())
                        .email(identity.email())
                        .fullName(identity.fullName())
                        .studentCode(null)
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
                null
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
