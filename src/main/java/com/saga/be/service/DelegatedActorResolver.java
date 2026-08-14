package com.saga.be.service;

import com.saga.be.entity.Admin;
import com.saga.be.entity.Lecturer;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.AdminRepository;
import com.saga.be.repository.LecturerRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DelegatedActorResolver {

    private final AdminRepository admins;
    private final LecturerRepository lecturers;
    private final StudentRepository students;
    private final CurrentAccountStatusService accountStatuses;

    public DelegatedActorResolver(
            AdminRepository admins,
            LecturerRepository lecturers,
            StudentRepository students,
            CurrentAccountStatusService accountStatuses
    ) {
        this.admins = admins;
        this.lecturers = lecturers;
        this.students = students;
        this.accountStatuses = accountStatuses;
    }

    @Transactional(readOnly = true)
    public SagaPrincipal resolve(
            String cognitoSub,
            UUID expectedProfileId,
            ApplicationRole expectedRole
    ) {
        List<SagaPrincipal> matches = new ArrayList<>();
        admins.findByCognitoSub(cognitoSub).map(this::principal).ifPresent(matches::add);
        lecturers.findByCognitoSub(cognitoSub).map(this::principal).ifPresent(matches::add);
        students.findByCognitoSub(cognitoSub).map(this::principal).ifPresent(matches::add);
        if (matches.size() != 1) {
            throw invalid("AGENT_CONTEXT_ACTOR_INVALID");
        }
        SagaPrincipal current = matches.get(0);
        if (!Objects.equals(current.localProfileId(), expectedProfileId)
                || current.applicationRole() != expectedRole
                || !accountStatuses.isAllowedForBusinessApi(current)) {
            throw invalid("AGENT_CONTEXT_ACTOR_INVALID");
        }
        return current;
    }

    private SagaPrincipal principal(Admin value) {
        return new SagaPrincipal(
                value.getCognitoSub(), value.getEmail(), value.getFullName(),
                ApplicationRole.ADMIN, value.getId(), null
        );
    }

    private SagaPrincipal principal(Lecturer value) {
        return new SagaPrincipal(
                value.getCognitoSub(), value.getEmail(), value.getFullName(),
                ApplicationRole.LECTURER, value.getId(),
                value.getAccountStatus() == null ? AccountStatus.ACTIVE : value.getAccountStatus()
        );
    }

    private SagaPrincipal principal(Student value) {
        return new SagaPrincipal(
                value.getCognitoSub(), value.getEmail(), value.getFullName(),
                ApplicationRole.STUDENT, value.getId(), value.getAccountStatus()
        );
    }

    private IntegrationException invalid(String code) {
        return new IntegrationException(
                HttpStatus.UNAUTHORIZED, code, "The delegated agent context is invalid"
        );
    }
}

