package com.saga.be.service;

import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves current local account state without contacting the identity provider. */
@Service
public class CurrentAccountStatusService {

    private final StudentRepository studentRepository;
    private final LecturerRepository lecturerRepository;

    public CurrentAccountStatusService(
            StudentRepository studentRepository,
            LecturerRepository lecturerRepository
    ) {
        this.studentRepository = studentRepository;
        this.lecturerRepository = lecturerRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAllowedForBusinessApi(SagaPrincipal principal) {
        if (principal == null || principal.localProfileId() == null) {
            return false;
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return true;
        }
        return currentStatus(principal)
                .map(status -> status == AccountStatus.ACTIVE)
                // Normal browser sessions have a profile created by OIDC synchronization. Preserve
                // the established authorization behavior for a profile that has since disappeared.
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public AccountStatus currentStatusForAuthRoute(SagaPrincipal principal) {
        if (principal == null || principal.applicationRole() == ApplicationRole.ADMIN) {
            return null;
        }
        return currentStatus(principal).orElse(principal.accountStatus());
    }

    /**
     * Deduplicated status lookup for connected SSE profiles. ADMIN is never status-gated.
     * Missing profiles keep the established "disappeared profile remains allowed" behavior.
     */
    @Transactional(readOnly = true)
    public Set<UUID> findDisabledIds(ApplicationRole role, Collection<UUID> ids) {
        if (role == null || ids == null || ids.isEmpty() || role == ApplicationRole.ADMIN) {
            return Set.of();
        }
        Set<UUID> uniqueIds = new LinkedHashSet<>(ids);
        Set<UUID> disabled = new LinkedHashSet<>();
        if (role == ApplicationRole.STUDENT) {
            for (Student student : studentRepository.findAllById(uniqueIds)) {
                if (student.getAccountStatus() != AccountStatus.ACTIVE) {
                    disabled.add(student.getId());
                }
            }
            return disabled;
        }
        if (role == ApplicationRole.LECTURER) {
            for (Lecturer lecturer : lecturerRepository.findAllById(uniqueIds)) {
                AccountStatus status = lecturer.getAccountStatus() == null
                        ? AccountStatus.ACTIVE
                        : lecturer.getAccountStatus();
                if (status != AccountStatus.ACTIVE) {
                    disabled.add(lecturer.getId());
                }
            }
            return disabled;
        }
        return Set.of();
    }

    private Optional<AccountStatus> currentStatus(SagaPrincipal principal) {
        if (principal.applicationRole() == ApplicationRole.STUDENT) {
            return studentRepository.findById(principal.localProfileId()).map(student -> student.getAccountStatus());
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER) {
            return lecturerRepository.findById(principal.localProfileId())
                    .map(lecturer -> lecturer.getAccountStatus() == null
                            ? AccountStatus.ACTIVE
                            : lecturer.getAccountStatus());
        }
        return Optional.empty();
    }
}
