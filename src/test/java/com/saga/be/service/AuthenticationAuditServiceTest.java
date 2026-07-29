package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.SystemAuditLog;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.repository.SystemAuditLogRepository;
import com.saga.be.security.ApplicationRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class AuthenticationAuditServiceTest {

    @Test
    void savesTheLocalProfileIdAsTextForMongoCompatibility() {
        SystemAuditLogRepository repository = mock(SystemAuditLogRepository.class);
        AuthenticationAuditService service = new AuthenticationAuditService(repository);
        UUID profileId = UUID.randomUUID();
        AuthenticatedProfile profile = new AuthenticatedProfile(
                "cognito-subject",
                "student@fpt.edu.vn",
                "Student User",
                ApplicationRole.STUDENT,
                profileId,
                AccountStatus.PENDING
        );

        service.recordSuccessfulLogin(profile, "127.0.0.1");

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(
                SystemAuditLog.class
        );
        verify(repository).save(captor.capture());
        Map<?, ?> values = assertInstanceOf(
                Map.class,
                captor.getValue().getNewValues()
        );
        assertEquals(profileId.toString(), values.get("localProfileId"));
        assertEquals("STUDENT", values.get("applicationRole"));
        assertEquals("PENDING", values.get("accountStatus"));
    }

    @Test
    void savesStudentCodeConflictWithoutEmailOrStudentCodes() {
        SystemAuditLogRepository repository = mock(SystemAuditLogRepository.class);
        AuthenticationAuditService service = new AuthenticationAuditService(repository);
        UUID profileId = UUID.randomUUID();

        service.recordStudentCodeConflict(
                "cognito-subject",
                profileId,
                "127.0.0.1"
        );

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(
                SystemAuditLog.class
        );
        verify(repository).save(captor.capture());
        SystemAuditLog entry = captor.getValue();
        Map<?, ?> values = assertInstanceOf(Map.class, entry.getNewValues());
        assertEquals("cognito-subject", entry.getActorId());
        assertEquals("AUTH_STUDENT_CODE_CONFLICT", entry.getAction());
        assertEquals("STUDENT", entry.getTargetEntity());
        assertEquals("STUDENT_CODE_MISMATCH", values.get("reason"));
        assertEquals(profileId.toString(), values.get("localProfileId"));
        assertFalse(values.containsKey("email"));
        assertFalse(values.containsKey("storedStudentCode"));
        assertFalse(values.containsKey("extractedStudentCode"));
    }

    @Test
    void savesIdentityConflictWithoutEmailOrOtherCognitoSubjects() {
        SystemAuditLogRepository repository = mock(SystemAuditLogRepository.class);
        AuthenticationAuditService service = new AuthenticationAuditService(repository);

        service.recordIdentityConflict(
                "new-cognito-subject",
                IdentityConflictException.Reason
                        .EMAIL_LINKED_TO_DIFFERENT_COGNITO_SUB,
                "127.0.0.1"
        );

        ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(
                SystemAuditLog.class
        );
        verify(repository).save(captor.capture());
        SystemAuditLog entry = captor.getValue();
        Map<?, ?> values = assertInstanceOf(Map.class, entry.getNewValues());
        assertEquals("new-cognito-subject", entry.getActorId());
        assertEquals("AUTH_IDENTITY_CONFLICT", entry.getAction());
        assertEquals("IDENTITY", entry.getTargetEntity());
        assertEquals(
                "EMAIL_LINKED_TO_DIFFERENT_COGNITO_SUB",
                values.get("reason")
        );
        assertFalse(values.containsKey("email"));
        assertFalse(values.containsKey("existingCognitoSub"));
    }

    @Test
    void requiredAdminOverrideAuditFailsClosedWhenStoreIsUnavailable() {
        SystemAuditLogRepository repository = mock(SystemAuditLogRepository.class);
        AuthenticationAuditService service = new AuthenticationAuditService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        assertThrows(
                IdentityServiceException.class,
                () -> service.recordRequiredIntegrationEvent(
                        "admin-sub",
                        "PROJECT_INTEGRATION_ADMIN_OVERRIDE",
                        "TEAM",
                        UUID.randomUUID(),
                        "AUTHORIZED",
                        null
                )
        );
    }
}
