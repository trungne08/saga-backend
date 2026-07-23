package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.SystemAuditLog;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.repository.SystemAuditLogRepository;
import com.saga.be.security.ApplicationRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
}
