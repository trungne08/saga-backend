package com.saga.be.service;

import com.saga.be.dto.request.SelfProfileUpdateRequest;
import com.saga.be.dto.response.AuthMeResponse;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.exception.UnauthenticatedRequestException;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Own-profile read and update; never changes identity, role, status, or provider state. */
@Service
public class SelfProfileService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;

    public SelfProfileService(StudentRepository studentRepository, LecturerRepository lecturerRepository) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
    }

    @Transactional(readOnly = true)
    public AuthMeResponse read(SagaPrincipal principal) {
        requirePrincipal(principal);
        if (principal.applicationRole() == ApplicationRole.STUDENT) {
            return studentRepository.findById(principal.localProfileId())
                    .map(student -> response(principal, student))
                    .orElseGet(() -> AuthMeResponse.from(principal, principal.accountStatus()));
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER) {
            return lecturerRepository.findById(principal.localProfileId())
                    .map(lecturer -> response(principal, lecturer))
                    .orElseGet(() -> AuthMeResponse.from(principal, principal.accountStatus()));
        }
        return AuthMeResponse.from(principal, null);
    }

    @Transactional
    public AuthMeResponse update(SagaPrincipal principal, SelfProfileUpdateRequest request) {
        requirePrincipal(principal);
        if (principal.applicationRole() == ApplicationRole.STUDENT) {
            Student student = studentRepository.findById(principal.localProfileId())
                    .orElseThrow(UnauthenticatedRequestException::new);
            apply(student, request);
            return response(principal, studentRepository.saveAndFlush(student));
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER) {
            Lecturer lecturer = lecturerRepository.findById(principal.localProfileId())
                    .orElseThrow(UnauthenticatedRequestException::new);
            apply(lecturer, request);
            return response(principal, lecturerRepository.saveAndFlush(lecturer));
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Self profile update is not available");
    }

    private AuthMeResponse response(SagaPrincipal principal, Student student) {
        return AuthMeResponse.from(
                student.getCognitoSub(),
                student.getEmail(),
                student.getFullName(),
                ApplicationRole.STUDENT,
                student.getId(),
                student.getAccountStatus(),
                student.getAvatarUrl(),
                student.getStudentCode()
        );
    }

    private AuthMeResponse response(SagaPrincipal principal, Lecturer lecturer) {
        return AuthMeResponse.from(
                lecturer.getCognitoSub(),
                lecturer.getEmail(),
                lecturer.getFullName(),
                ApplicationRole.LECTURER,
                lecturer.getId(),
                lecturer.getAccountStatus(),
                lecturer.getAvatarUrl(),
                null
        );
    }

    private void apply(Student profile, SelfProfileUpdateRequest request) {
        if (request.isFullNamePresent()) {
            profile.setFullName(normalizedFullName(request.getFullName()));
        }
        if (request.isAvatarUrlPresent()) {
            profile.setAvatarUrl(normalizedAvatarUrl(request.getAvatarUrl()));
        }
    }

    private void apply(Lecturer profile, SelfProfileUpdateRequest request) {
        if (request.isFullNamePresent()) {
            profile.setFullName(normalizedFullName(request.getFullName()));
        }
        if (request.isAvatarUrlPresent()) {
            profile.setAvatarUrl(normalizedAvatarUrl(request.getAvatarUrl()));
        }
    }

    private String normalizedFullName(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fullName must not be blank");
        }
        return value.trim();
    }

    private String normalizedAvatarUrl(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = OidcAvatarUrl.sanitize(value);
        if (sanitized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl is invalid");
        }
        return sanitized;
    }

    private void requirePrincipal(SagaPrincipal principal) {
        if (principal == null || principal.localProfileId() == null) {
            throw new UnauthenticatedRequestException();
        }
    }
}
