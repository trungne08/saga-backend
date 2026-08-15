package com.saga.be.service;

import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.SystemAuditLog;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.repository.SystemAuditLogRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAuditService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            AuthenticationAuditService.class
    );

    private final SystemAuditLogRepository auditLogRepository;

    public AuthenticationAuditService(SystemAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void recordSuccessfulLogin(AuthenticatedProfile profile, String remoteAddress) {
        Map<String, Object> safeValues = new LinkedHashMap<>();
        // Store UUID as text so the Mongo driver does not need a UUID binary
        // representation configuration for this flexible audit-value map.
        safeValues.put("localProfileId", profile.localProfileId().toString());
        safeValues.put("applicationRole", profile.role().name());
        if (profile.accountStatus() != null) {
            safeValues.put("accountStatus", profile.accountStatus().name());
        }

        SystemAuditLog entry = new SystemAuditLog();
        entry.setActorId(profile.cognitoSub());
        entry.setActorLocalProfileId(profile.localProfileId().toString());
        entry.setActorRole(profile.role().name());
        entry.setAction("AUTH_LOGIN_SUCCESS");
        entry.setTargetEntity(profile.role().name());
        entry.setOldValues(null);
        entry.setNewValues(safeValues);
        entry.setIpAddress(remoteAddress);

        try {
            auditLogRepository.save(entry);
        } catch (DataAccessException exception) {
            throw new IdentityServiceException(
                    "The authentication audit service is unavailable",
                    exception
            );
        }
    }

    public void recordStudentCodeConflict(
            String cognitoSub,
            UUID localProfileId,
            String remoteAddress
    ) {
        Map<String, Object> safeValues = new LinkedHashMap<>();
        safeValues.put("reason", "STUDENT_CODE_MISMATCH");
        if (localProfileId != null) {
            safeValues.put("localProfileId", localProfileId.toString());
        }

        SystemAuditLog entry = new SystemAuditLog();
        entry.setActorId(cognitoSub);
        if (localProfileId != null) {
            entry.setActorLocalProfileId(localProfileId.toString());
        }
        entry.setAction("AUTH_STUDENT_CODE_CONFLICT");
        entry.setTargetEntity("STUDENT");
        entry.setOldValues(null);
        entry.setNewValues(safeValues);
        entry.setIpAddress(remoteAddress);

        try {
            auditLogRepository.save(entry);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Could not persist student-code conflict audit: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public void recordIdentityConflict(
            String cognitoSub,
            IdentityConflictException.Reason reason,
            String remoteAddress
    ) {
        Map<String, Object> safeValues = new LinkedHashMap<>();
        safeValues.put("reason", reason.name());

        SystemAuditLog entry = new SystemAuditLog();
        entry.setActorId(cognitoSub);
        entry.setAction("AUTH_IDENTITY_CONFLICT");
        entry.setTargetEntity("IDENTITY");
        entry.setOldValues(null);
        entry.setNewValues(safeValues);
        entry.setIpAddress(remoteAddress);

        try {
            auditLogRepository.save(entry);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Could not persist identity-conflict audit: {}",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public void recordIntegrationEvent(
            SagaPrincipal principal,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        saveIntegrationEvent(
                principal.cognitoSub(),
                principal.localProfileId(),
                principal.applicationRole(),
                action,
                targetEntity,
                targetId,
                outcome,
                remoteAddress
        );
    }

    public void recordIntegrationEvent(
            String cognitoSub,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        saveIntegrationEvent(cognitoSub, null, null, action, targetEntity, targetId, outcome, remoteAddress);
    }

    private void saveIntegrationEvent(
            String cognitoSub,
            UUID actorLocalProfileId,
            ApplicationRole actorRole,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        SystemAuditLog entry = integrationEntry(cognitoSub, actorLocalProfileId, actorRole,
                action, targetEntity, targetId, outcome, remoteAddress);
        try {
            auditLogRepository.save(entry);
        } catch (DataAccessException exception) {
            LOGGER.warn(
                    "Could not persist integration audit for action {}: {}",
                    action,
                    exception.getClass().getSimpleName()
            );
        }
    }

    public void recordRequiredIntegrationEvent(
            SagaPrincipal principal,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        saveRequiredIntegrationEvent(
                principal.cognitoSub(),
                principal.localProfileId(),
                principal.applicationRole(),
                action,
                targetEntity,
                targetId,
                outcome,
                remoteAddress
        );
    }

    public void recordRequiredIntegrationEvent(
            String cognitoSub,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        saveRequiredIntegrationEvent(cognitoSub, null, null, action, targetEntity, targetId, outcome,
                remoteAddress);
    }

    private void saveRequiredIntegrationEvent(
            String cognitoSub,
            UUID actorLocalProfileId,
            ApplicationRole actorRole,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        SystemAuditLog entry = integrationEntry(cognitoSub, actorLocalProfileId, actorRole,
                action, targetEntity, targetId, outcome, remoteAddress);
        try {
            auditLogRepository.save(entry);
        } catch (DataAccessException exception) {
            throw new IdentityServiceException(
                    "The required integration audit service is unavailable",
                    exception
            );
        }
    }

    private SystemAuditLog integrationEntry(
            String cognitoSub,
            UUID actorLocalProfileId,
            ApplicationRole actorRole,
            String action,
            String targetEntity,
            UUID targetId,
            String outcome,
            String remoteAddress
    ) {
        Map<String, Object> safeValues = new LinkedHashMap<>();
        safeValues.put("outcome", outcome);
        if (targetId != null) {
            safeValues.put("localTargetId", targetId.toString());
        }

        SystemAuditLog entry = new SystemAuditLog();
        entry.setActorId(cognitoSub);
        if (actorLocalProfileId != null) {
            entry.setActorLocalProfileId(actorLocalProfileId.toString());
        }
        if (actorRole != null) {
            entry.setActorRole(actorRole.name());
        }
        entry.setAction(action);
        entry.setTargetEntity(targetEntity);
        entry.setOldValues(null);
        entry.setNewValues(safeValues);
        entry.setIpAddress(remoteAddress);
        return entry;
    }
}
