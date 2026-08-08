package com.saga.be.service;

import com.saga.be.dto.request.JiraSprintCreateRequest;
import com.saga.be.dto.request.JiraSprintUpdateRequest;
import com.saga.be.dto.response.JiraSprintResponse;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.project.JiraBoardResolutionService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraSprintSnapshot;
import com.saga.be.integration.provider.JiraWriteScope;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraSprintWriteService {
    private static final Logger log = LoggerFactory.getLogger(JiraSprintWriteService.class);
    private final ProjectIntegrationAuthorizationService authorization;
    private final JiraBoardRepository boards;
    private final JiraCredentialService credentials;
    private final JiraBoardResolutionService boardResolver;
    private final JiraProviderClient provider;
    private final JiraSprintUpsertService upserts;
    private final JiraWriteOperationService operations;
    private final SprintRepository sprints;
    private final TaskRepository tasks;

    public JiraSprintWriteService(ProjectIntegrationAuthorizationService authorization, JiraBoardRepository boards,
            JiraCredentialService credentials, JiraBoardResolutionService boardResolver, JiraProviderClient provider, JiraSprintUpsertService upserts,
            JiraWriteOperationService operations, SprintRepository sprints, TaskRepository tasks) {
        this.authorization = authorization; this.boards = boards; this.credentials = credentials;
        this.boardResolver = boardResolver; this.provider = provider; this.upserts = upserts; this.operations = operations;
        this.sprints = sprints; this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public JiraSprintResponse detail(SagaPrincipal principal, UUID projectId, UUID sprintId) {
        authorization.requireProjectManager(principal, projectId);
        return JiraSprintResponse.from(sprint(projectId, sprintId));
    }

    @Transactional
    public JiraSprintResponse create(SagaPrincipal principal, UUID projectId, String key, JiraSprintCreateRequest request) {
        if (request.endDate() != null && request.startDate() != null && !request.endDate().isAfter(request.startDate()))
            throw IntegrationException.invalid("JIRA_SPRINT_DATE_INVALID", "Sprint endDate must be after startDate");
        Project project = authorization.requireProjectManager(principal, projectId);
        JiraBoard board = board(projectId);
        JiraWriteScope.requireGranted(board, JiraWriteScope.WRITE_SPRINT_SCOPE);
        String token = credentials.validAccessToken(board);
        String externalBoardId = boardResolver.resolve(board);
        JiraWriteOperation op = operations.claim(project, principal, JiraWriteOperationType.SPRINT_CREATE, key,
                operations.fingerprint(request.name() + "|" + request.goal() + "|" + request.startDate() + "|" + request.endDate()));
        if (op.getStatus() == JiraWriteOperationStatus.COMPLETED) return result(op, projectId);
        if (op.getStatus() == JiraWriteOperationStatus.PENDING) {
            try {
                JiraSprintSnapshot remote = provider.createSprint(token, board.getCloudId(), externalBoardId, request.name(), request.goal(),
                        date(request.startDate()), date(request.endDate()));
                op.setRemoteResourceId(remote.id()); operations.markRemoteSucceeded(op.getId(), remote.id(), null);
            } catch (IntegrationException exception) { operations.failed(op.getId(), exception.getCode()); throw exception;
            } catch (RuntimeException exception) { operations.unknown(op.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN"); throw exception; }
        }
        return reconcile(op, board, projectId);
    }

    @Transactional
    public JiraSprintResponse update(SagaPrincipal principal, UUID projectId, UUID sprintId, String key, JiraSprintUpdateRequest request) {
        Sprint sprint = sprint(projectId, sprintId);
        Map<String, Object> changes = changes(request);
        if (changes.isEmpty()) throw IntegrationException.invalid("JIRA_SPRINT_UPDATE_EMPTY", "No sprint changes were supplied");
        return mutate(principal, projectId, sprint, key, JiraWriteOperationType.SPRINT_UPDATE, changes.toString(),
                JiraWriteScope.WRITE_SPRINT_SCOPE, (token, board) -> provider.updateSprint(token, board.getCloudId(), sprint.getExternalSprintId(), changes));
    }

    @Transactional
    public JiraSprintResponse start(SagaPrincipal principal, UUID projectId, UUID sprintId, String key) {
        Sprint sprint = sprint(projectId, sprintId);
        if (!"future".equalsIgnoreCase(sprint.getState()) || sprint.getStartDate() == null || sprint.getEndDate() == null)
            throw IntegrationException.invalid("JIRA_SPRINT_STATE_INVALID", "Only a dated future sprint can be started");
        return mutate(principal, projectId, sprint, key, JiraWriteOperationType.SPRINT_START, "active",
                JiraWriteScope.WRITE_SPRINT_SCOPE, (token, board) -> provider.updateSprint(token, board.getCloudId(), sprint.getExternalSprintId(), Map.of("state", "active")));
    }

    @Transactional
    public JiraSprintResponse close(SagaPrincipal principal, UUID projectId, UUID sprintId, String key) {
        Sprint sprint = sprint(projectId, sprintId);
        if (!"active".equalsIgnoreCase(sprint.getState())) throw IntegrationException.invalid("JIRA_SPRINT_STATE_INVALID", "Only an active sprint can be closed");
        return mutate(principal, projectId, sprint, key, JiraWriteOperationType.SPRINT_CLOSE, "closed",
                JiraWriteScope.WRITE_SPRINT_SCOPE, (token, board) -> provider.updateSprint(token, board.getCloudId(), sprint.getExternalSprintId(), Map.of("state", "closed")));
    }

    @Transactional
    public void delete(SagaPrincipal principal, UUID projectId, UUID sprintId, String key) {
        Sprint sprint = sprint(projectId, sprintId); Project project = authorization.requireProjectManager(principal, projectId);
        JiraWriteOperation op = operations.claim(project, principal, JiraWriteOperationType.SPRINT_DELETE, key, operations.fingerprint(sprint.getExternalSprintId()));
        if (op.getStatus() == JiraWriteOperationStatus.COMPLETED) return;
        JiraBoard board = board(projectId);
        JiraWriteScope.requireGranted(board, JiraWriteScope.DELETE_SPRINT_SCOPE);
        String token = credentials.validAccessToken(board);
        if (op.getStatus() == JiraWriteOperationStatus.PENDING) {
            try { provider.deleteSprint(token, board.getCloudId(), sprint.getExternalSprintId()); op.setRemoteResourceId(sprint.getExternalSprintId()); operations.markRemoteSucceeded(op.getId(), sprint.getExternalSprintId(), null);
            } catch (IntegrationException exception) { operations.failed(op.getId(), exception.getCode()); throw exception;
            } catch (RuntimeException exception) { operations.unknown(op.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN"); throw exception; }
        }
        for (Task task : tasks.findByProjectId(projectId)) if (task.getSprint() != null && sprintId.equals(task.getSprint().getId())) task.setSprint(null);
        tasks.flush(); sprint.setDeletedAt(LocalDateTime.now()); sprints.saveAndFlush(sprint); operations.complete(op.getId());
    }

    private JiraSprintResponse mutate(SagaPrincipal principal, UUID projectId, Sprint sprint, String key, JiraWriteOperationType type,
            String fingerprint, String scope, SprintMutation remote) {
        Project project = authorization.requireProjectManager(principal, projectId);
        JiraWriteOperation op = operations.claim(project, principal, type, key, operations.fingerprint(fingerprint));
        if (op.getStatus() == JiraWriteOperationStatus.COMPLETED) return JiraSprintResponse.from(sprint);
        JiraBoard board = board(projectId);
        try {
            JiraWriteScope.requireGranted(board, scope);
        } catch (IntegrationException exception) {
            failBeforeRemote(op, exception);
            logFailure(projectId, sprint, op, "SCOPE_PREFLIGHT", scope, "MISSING", exception,
                    statusAfterPreRemoteFailure(op));
            throw exception;
        }
        String token;
        try {
            token = credentials.validAccessToken(board);
        } catch (IntegrationException exception) {
            failBeforeRemote(op, exception);
            logFailure(projectId, sprint, op, "CREDENTIAL_RESOLUTION", scope, "GRANTED", exception,
                    statusAfterPreRemoteFailure(op));
            throw exception;
        }
        try {
            credentials.requireCurrentScopes(board, scope);
        } catch (IntegrationException exception) {
            failBeforeRemote(op, exception);
            logFailure(projectId, sprint, op, "SCOPE_PREFLIGHT_AFTER_CREDENTIAL", scope, "MISSING", exception,
                    statusAfterPreRemoteFailure(op));
            throw exception;
        }
        if (op.getStatus() == JiraWriteOperationStatus.PENDING) {
            try { remote.apply(token, board); op.setRemoteResourceId(sprint.getExternalSprintId()); operations.markRemoteSucceeded(op.getId(), sprint.getExternalSprintId(), null);
            } catch (IntegrationException exception) { operations.failed(op.getId(), exception.getCode()); logFailure(projectId, sprint, op, "REMOTE_SPRINT_MUTATION", scope, "GRANTED", exception, JiraWriteOperationStatus.FAILED); throw exception;
            } catch (RuntimeException exception) { operations.unknown(op.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN"); logUnknownFailure(projectId, sprint, op, "REMOTE_SPRINT_MUTATION", scope, JiraWriteOperationStatus.UNKNOWN); throw exception; }
        }
        if (op.getStatus() == JiraWriteOperationStatus.FAILED) throw IntegrationException.conflict(
                "JIRA_WRITE_PREVIOUSLY_FAILED", "The previous Jira write failed before a recoverable remote success");
        return reconcile(op, board, projectId);
    }

    private JiraSprintResponse reconcile(JiraWriteOperation op, JiraBoard board, UUID projectId) {
        if (op.getRemoteResourceId() == null) throw IntegrationException.conflict("JIRA_WRITE_OPERATION_IN_PROGRESS", "The Jira write outcome is still being recovered");
        try {
            JiraWriteScope.requireGranted(board, JiraWriteScope.READ_SPRINT_SCOPE);
            String token = credentials.validAccessToken(board);
            credentials.requireCurrentScopes(board, JiraWriteScope.READ_SPRINT_SCOPE);
            Sprint sprint = upserts.upsert(board.getId(), provider.getSprint(token, board.getCloudId(), op.getRemoteResourceId()));
            operations.complete(op.getId()); return JiraSprintResponse.from(sprint);
        } catch (IntegrationException exception) {
            logFailure(projectId, null, op, "CANONICAL_GET_SPRINT", JiraWriteScope.READ_SPRINT_SCOPE,
                    "GRANTED", exception, JiraWriteOperationStatus.REMOTE_SUCCEEDED);
            throw exception;
        } catch (RuntimeException exception) {
            logUnknownFailure(projectId, null, op, "LOCAL_UPSERT_OR_CANONICAL_GET_SPRINT",
                    JiraWriteScope.READ_SPRINT_SCOPE, JiraWriteOperationStatus.REMOTE_SUCCEEDED);
            throw exception;
        }
    }

    private JiraSprintResponse result(JiraWriteOperation op, UUID projectId) { return JiraSprintResponse.from(sprints.findByBoardProjectIdAndDeletedAtIsNull(projectId).stream().filter(value -> value.getExternalSprintId().equals(op.getRemoteResourceId())).findFirst().orElseThrow(() -> IntegrationException.conflict("JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery"))); }
    private Sprint sprint(UUID projectId, UUID id) { return sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(id, projectId).orElseThrow(() -> notFound("Sprint not found")); }
    private JiraBoard board(UUID projectId) { JiraBoard board = boards.findByProjectId(projectId).orElseThrow(() -> IntegrationException.conflict("JIRA_LINK_NOT_FOUND", "The project has no Jira connection")); if (board.getConnectionStatus() != IntegrationStatus.ACTIVE) throw IntegrationException.conflict("JIRA_INTEGRATION_NOT_ACTIVE", "The Jira integration is not active"); return board; }
    private IntegrationException notFound(String text) { return new IntegrationException(HttpStatus.NOT_FOUND, "JIRA_RESOURCE_NOT_FOUND", text); }
    private void failBeforeRemote(JiraWriteOperation operation, IntegrationException exception) {
        if (operation.getStatus() == JiraWriteOperationStatus.PENDING) {
            operations.failed(operation.getId(), exception.getCode());
        }
    }
    private JiraWriteOperationStatus statusAfterPreRemoteFailure(JiraWriteOperation operation) {
        return operation.getStatus() == JiraWriteOperationStatus.PENDING
                ? JiraWriteOperationStatus.FAILED
                : operation.getStatus();
    }
    private void logFailure(UUID projectId, Sprint sprint, JiraWriteOperation operation, String stage,
            String requiredScope, String scopePreflightResult, IntegrationException exception,
            JiraWriteOperationStatus writeOperationStatus) {
        log.warn("Jira sprint write failed: projectId={}, localSprintId={}, externalSprintId={}, operation={}, stage={}, "
                        + "requiredScopes={}, scopePreflightResult={}, upstreamHttpStatus={}, errorCategory={}, writeOperationStatus={}",
                projectId, sprint == null ? null : sprint.getId(), remoteSprintId(sprint, operation), operation.getOperationType(),
                stage, requiredScope, scopePreflightResult, upstreamHttpStatus(exception), exception.getCode(), writeOperationStatus);
    }
    private void logUnknownFailure(UUID projectId, Sprint sprint, JiraWriteOperation operation, String stage,
            String requiredScope, JiraWriteOperationStatus writeOperationStatus) {
        log.warn("Jira sprint write outcome is unknown: projectId={}, localSprintId={}, externalSprintId={}, operation={}, stage={}, "
                        + "requiredScopes={}, scopePreflightResult=GRANTED, upstreamHttpStatus=UNKNOWN, errorCategory=JIRA_WRITE_OUTCOME_UNKNOWN, writeOperationStatus={}",
                projectId, sprint == null ? null : sprint.getId(), remoteSprintId(sprint, operation), operation.getOperationType(),
                stage, requiredScope, writeOperationStatus);
    }
    private String remoteSprintId(Sprint sprint, JiraWriteOperation operation) { return sprint != null ? sprint.getExternalSprintId() : operation.getRemoteResourceId(); }
    private String upstreamHttpStatus(IntegrationException exception) { return switch (exception.getCode()) {
        case "JIRA_ACCESS_REVOKED" -> "401";
        case "JIRA_ACCESS_FORBIDDEN" -> "403";
        case "JIRA_SPRINT_NOT_FOUND", "JIRA_RESOURCE_NOT_FOUND" -> "404";
        case "JIRA_RATE_LIMITED" -> "429";
        default -> "UNKNOWN";
    }; }
    private String date(Instant date) { return date == null ? null : date.toString(); }
    private Map<String, Object> changes(JiraSprintUpdateRequest request) { Map<String, Object> result = new LinkedHashMap<>(); if (request.name() != null) result.put("name", request.name().trim()); if (request.goal() != null) result.put("goal", request.goal()); if (request.startDate() != null) result.put("startDate", date(request.startDate())); if (request.endDate() != null) result.put("endDate", date(request.endDate())); return result; }
    @FunctionalInterface private interface SprintMutation { void apply(String token, JiraBoard board); }
}
