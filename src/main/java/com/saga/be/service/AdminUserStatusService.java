package com.saga.be.service;

import com.saga.be.dto.response.AdminUserReadResponse;
import com.saga.be.entity.Admin;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.AccountStatusException;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.AccountDisabledEvent;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminUserStatusService {

    private final AdminRepository adminRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminUserStatusService(
            AdminRepository adminRepository,
            LecturerRepository lecturerRepository,
            StudentRepository studentRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.adminRepository = adminRepository;
        this.lecturerRepository = lecturerRepository;
        this.studentRepository = studentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AdminUserReadResponse updateStatus(
            SagaPrincipal actor,
            UUID profileId,
            AccountStatus requestedStatus
    ) {
        if (actor == null || actor.applicationRole() != ApplicationRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access is required");
        }
        if (requestedStatus == AccountStatus.PENDING) {
            throw new AccountStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ACCOUNT_STATUS_PENDING_NOT_ALLOWED",
                    "PENDING is managed by Student provisioning"
            );
        }

        List<ProfileTarget> targets = resolve(profileId);
        if (targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        if (targets.size() > 1) {
            throw new AccountStatusException(
                    HttpStatus.CONFLICT,
                    "ACCOUNT_STATUS_TARGET_AMBIGUOUS",
                    "The local profile identifier is ambiguous"
            );
        }

        ProfileTarget target = targets.get(0);
        if (target.role() == ApplicationRole.ADMIN) {
            throw new AccountStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ACCOUNT_STATUS_TARGET_UNSUPPORTED",
                    "Administrator profiles do not support account status"
            );
        }
        if (target.role() == ApplicationRole.STUDENT) {
            Student student = (Student) target.entity();
            student.setAccountStatus(requestedStatus);
            Student saved = studentRepository.save(student);
            publishDisabledAfterCommit(ApplicationRole.STUDENT, saved.getId(), saved.getAccountStatus());
            return new AdminUserReadResponse(
                    saved.getId(),
                    ApplicationRole.STUDENT,
                    saved.getFullName(),
                    saved.getEmail(),
                    saved.getAccountStatus(),
                    saved.getStudentCode()
            );
        }

        Lecturer lecturer = (Lecturer) target.entity();
        lecturer.setAccountStatus(requestedStatus);
        Lecturer saved = lecturerRepository.save(lecturer);
        publishDisabledAfterCommit(ApplicationRole.LECTURER, saved.getId(), saved.getAccountStatus());
        return new AdminUserReadResponse(
                saved.getId(),
                ApplicationRole.LECTURER,
                saved.getFullName(),
                saved.getEmail(),
                saved.getAccountStatus(),
                null
        );
    }

    private void publishDisabledAfterCommit(
            ApplicationRole role,
            UUID localProfileId,
            AccountStatus accountStatus
    ) {
        if (accountStatus == AccountStatus.ACTIVE) {
            return;
        }
        eventPublisher.publishEvent(new AccountDisabledEvent(role, localProfileId, accountStatus));
    }

    private List<ProfileTarget> resolve(UUID profileId) {
        List<ProfileTarget> targets = new ArrayList<>();
        adminRepository.findById(profileId).ifPresent(admin -> targets.add(ProfileTarget.admin(admin)));
        lecturerRepository.findById(profileId).ifPresent(lecturer -> targets.add(ProfileTarget.lecturer(lecturer)));
        studentRepository.findById(profileId).ifPresent(student -> targets.add(ProfileTarget.student(student)));
        return targets;
    }

    private record ProfileTarget(ApplicationRole role, Object entity) {
        private static ProfileTarget admin(Admin admin) {
            return new ProfileTarget(ApplicationRole.ADMIN, admin);
        }

        private static ProfileTarget lecturer(Lecturer lecturer) {
            return new ProfileTarget(ApplicationRole.LECTURER, lecturer);
        }

        private static ProfileTarget student(Student student) {
            return new ProfileTarget(ApplicationRole.STUDENT, student);
        }
    }
}
