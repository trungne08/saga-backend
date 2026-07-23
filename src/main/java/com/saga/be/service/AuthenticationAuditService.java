package com.saga.be.service;

import com.saga.be.auth.AuthenticatedProfile;
import com.saga.be.entity.SystemAuditLog;
import com.saga.be.exception.IdentityServiceException;
import com.saga.be.repository.SystemAuditLogRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationAuditService {

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
}
