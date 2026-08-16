package com.saga.be.service;

import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.repository.JiraWriteOperationRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgentTaskCreateSprintRecoveryGate {

    static final String SPRINT_KEY_SUFFIX = ":sprint";

    private final JiraWriteOperationRepository operations;

    public AgentTaskCreateSprintRecoveryGate(JiraWriteOperationRepository operations) {
        this.operations = operations;
    }

    public String sprintKey(String createKey) {
        if (createKey == null) {
            return null;
        }
        return createKey.trim() + SPRINT_KEY_SUFFIX;
    }

    public boolean allowsExecutingReentry(AgentApiResponses.PendingAction action) {
        if (action == null || !"EXECUTING".equals(action.status()) || !"TASK_CREATE".equals(action.actionType())) {
            return false;
        }
        UUID projectId = projectId(action);
        UUID sprintId = sprintId(action);
        String createKey = action.idempotencyKey();
        if (projectId == null || sprintId == null || createKey == null || createKey.isBlank()) {
            return false;
        }
        return matches(
                operations.findByProjectIdAndIdempotencyKey(projectId, createKey.trim()).orElse(null),
                JiraWriteOperationType.TASK_CREATE,
                JiraWriteOperationStatus.COMPLETED
        ) && matches(
                operations.findByProjectIdAndIdempotencyKey(projectId, sprintKey(createKey)).orElse(null),
                JiraWriteOperationType.TASK_SPRINT,
                JiraWriteOperationStatus.REMOTE_SUCCEEDED,
                JiraWriteOperationStatus.COMPLETED
        );
    }

    public boolean keepExecutingAfterFailure(AgentApiResponses.PendingAction action) {
        if (action == null || !"TASK_CREATE".equals(action.actionType())) {
            return false;
        }
        UUID projectId = projectId(action);
        UUID sprintId = sprintId(action);
        String createKey = action.idempotencyKey();
        if (projectId == null || sprintId == null || createKey == null || createKey.isBlank()) {
            return false;
        }
        return matches(
                operations.findByProjectIdAndIdempotencyKey(projectId, createKey.trim()).orElse(null),
                JiraWriteOperationType.TASK_CREATE,
                JiraWriteOperationStatus.COMPLETED
        ) && matches(
                operations.findByProjectIdAndIdempotencyKey(projectId, sprintKey(createKey)).orElse(null),
                JiraWriteOperationType.TASK_SPRINT,
                JiraWriteOperationStatus.REMOTE_SUCCEEDED
        );
    }

    public UUID projectId(AgentApiResponses.PendingAction action) {
        return uuid(payload(action).get("projectId"));
    }

    public UUID sprintId(AgentApiResponses.PendingAction action) {
        return uuid(payload(action).get("sprintId"));
    }

    private Map<String, Object> payload(AgentApiResponses.PendingAction action) {
        return action.payload() == null ? Map.of() : action.payload();
    }

    private boolean matches(
            JiraWriteOperation operation,
            JiraWriteOperationType type,
            JiraWriteOperationStatus... allowed
    ) {
        if (operation == null || operation.getOperationType() != type) {
            return false;
        }
        for (JiraWriteOperationStatus status : allowed) {
            if (operation.getStatus() == status) {
                return true;
            }
        }
        return false;
    }

    private UUID uuid(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
