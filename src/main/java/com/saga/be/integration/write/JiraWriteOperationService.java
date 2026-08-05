package com.saga.be.integration.write;

import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.security.SagaPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class JiraWriteOperationService {

    private final JiraWriteOperationRepository operationRepository;
    private final TransactionTemplate transactionTemplate;

    public JiraWriteOperationService(
            JiraWriteOperationRepository operationRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.operationRepository = operationRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public JiraWriteOperation claim(
            Project project,
            SagaPrincipal principal,
            JiraWriteOperationType type,
            String idempotencyKey,
            String requestFingerprint
    ) {
        try {
            return transactionTemplate.execute(status -> claimAttempt(project, principal, type, idempotencyKey, requestFingerprint));
        } catch (DataIntegrityViolationException exception) {
            if (!isTargetDuplicate(exception)) throw exception;
            return transactionTemplate.execute(status -> reloadClaim(project, type, idempotencyKey, requestFingerprint));
        }
    }

    private JiraWriteOperation claimAttempt(
            Project project, SagaPrincipal principal, JiraWriteOperationType type,
            String idempotencyKey, String requestFingerprint
    ) {
        validateKey(idempotencyKey);
        UUID actorProfileId = requireActor(principal);
        JiraWriteOperation existing = operationRepository
                .findByProjectIdAndIdempotencyKey(project.getId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return existingOrConflict(existing, type, requestFingerprint);
        }
        return operationRepository.saveAndFlush(JiraWriteOperation.builder()
                    .project(project)
                    .actorProfileId(actorProfileId)
                    .operationType(type)
                    .idempotencyKey(idempotencyKey.trim())
                    .requestFingerprint(requestFingerprint)
                    .status(JiraWriteOperationStatus.PENDING)
                    .build());
    }

    private JiraWriteOperation reloadClaim(Project project, JiraWriteOperationType type, String key, String fingerprint) {
        return operationRepository.findByProjectIdAndIdempotencyKey(project.getId(), key.trim())
                .map(value -> existingOrConflict(value, type, fingerprint))
                .orElseThrow(() -> IntegrationException.conflict("JIRA_IDEMPOTENCY_CLAIM_CONFLICT", "The idempotency operation could not be recovered"));
    }

    private boolean isTargetDuplicate(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("uk_jira_write_operation_project_key")) return true;
            current = current.getCause();
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRemoteSucceeded(UUID operationId, String remoteId, String remoteKey) {
        JiraWriteOperation operation = required(operationId);
        operation.setRemoteResourceId(remoteId);
        operation.setRemoteResourceKey(remoteKey);
        operation.setStatus(JiraWriteOperationStatus.REMOTE_SUCCEEDED);
        operation.setSafeErrorCode(null);
        operationRepository.saveAndFlush(operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID operationId) {
        JiraWriteOperation operation = required(operationId);
        operation.setStatus(JiraWriteOperationStatus.COMPLETED);
        operation.setSafeErrorCode(null);
        operation.setCompletedAt(LocalDateTime.now());
        operationRepository.saveAndFlush(operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unknown(UUID operationId, String safeErrorCode) {
        setFailure(operationId, JiraWriteOperationStatus.UNKNOWN, safeErrorCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(UUID operationId, String safeErrorCode) {
        setFailure(operationId, JiraWriteOperationStatus.FAILED, safeErrorCode);
    }

    public String fingerprint(String canonicalRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JiraWriteOperation existingOrConflict(
            JiraWriteOperation existing,
            JiraWriteOperationType type,
            String fingerprint
    ) {
        if (existing.getOperationType() != type
                || !existing.getRequestFingerprint().equals(fingerprint)) {
            throw IntegrationException.conflict(
                    "JIRA_IDEMPOTENCY_KEY_REUSED",
                    "The idempotency key was already used for a different request"
            );
        }
        if (existing.getStatus() == JiraWriteOperationStatus.PENDING
                || existing.getStatus() == JiraWriteOperationStatus.UNKNOWN) {
            throw IntegrationException.conflict(
                    "JIRA_WRITE_OPERATION_IN_PROGRESS",
                    "The previous Jira write outcome is still being recovered"
            );
        }
        return existing;
    }

    private void setFailure(
            UUID operationId,
            JiraWriteOperationStatus status,
            String safeErrorCode
    ) {
        JiraWriteOperation operation = required(operationId);
        operation.setStatus(status);
        operation.setSafeErrorCode(safeErrorCode == null ? null : safeErrorCode.substring(
                0, Math.min(64, safeErrorCode.length())
        ));
        operationRepository.saveAndFlush(operation);
    }

    private JiraWriteOperation required(UUID operationId) {
        return operationRepository.findById(operationId).orElseThrow(() ->
                new IllegalStateException("Jira write operation is no longer available")
        );
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 128) {
            throw IntegrationException.invalid(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key is required and must be at most 128 characters"
            );
        }
    }

    private UUID requireActor(SagaPrincipal principal) {
        if (principal == null || principal.localProfileId() == null) {
            throw IntegrationException.forbidden(
                    "An authenticated local profile is required for Jira writes"
            );
        }
        return principal.localProfileId();
    }
}
