package com.saga.be.service;

import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.dto.request.JiraTaskAssigneeRequest;
import com.saga.be.dto.request.JiraTaskSprintRequest;
import com.saga.be.dto.request.JiraTaskEstimationRequest;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.dto.response.JiraTaskTransitionResponse;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraCreateField;
import com.saga.be.integration.provider.JiraCreateFieldAllowedValue;
import com.saga.be.integration.provider.JiraCreateIssueType;
import com.saga.be.integration.provider.JiraIssueReference;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraWriteScope;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraTaskWriteService {

    private static final Logger log = LoggerFactory.getLogger(JiraTaskWriteService.class);

    private final ProjectIntegrationAuthorizationService authorization;
    private final JiraBoardRepository boardRepository;
    private final JiraCredentialService credentialService;
    private final JiraProviderClient jiraClient;
    private final JiraIssueUpsertService issueUpsertService;
    private final JiraWriteOperationService operationService;
    private final TaskRepository taskRepository;
    private final IdentityMapRepository identityMapRepository;
    private final SprintRepository sprintRepository;
    private final JiraSprintUpsertService sprintUpsertService;

    public JiraTaskWriteService(ProjectIntegrationAuthorizationService authorization, JiraBoardRepository boardRepository,
            JiraCredentialService credentialService, JiraProviderClient jiraClient,
            JiraIssueUpsertService issueUpsertService, JiraWriteOperationService operationService,
            TaskRepository taskRepository, IdentityMapRepository identityMapRepository,
            SprintRepository sprintRepository, JiraSprintUpsertService sprintUpsertService) {
        this.authorization = authorization;
        this.boardRepository = boardRepository;
        this.credentialService = credentialService;
        this.jiraClient = jiraClient;
        this.issueUpsertService = issueUpsertService;
        this.operationService = operationService;
        this.taskRepository = taskRepository;
        this.identityMapRepository = identityMapRepository;
        this.sprintRepository = sprintRepository;
        this.sprintUpsertService = sprintUpsertService;
    }

    @Transactional(readOnly = true)
    public List<JiraTaskTransitionResponse> transitions(
            SagaPrincipal principal, UUID projectId, UUID taskId
    ) {
        authorization.requireProjectManager(principal, projectId);
        Task task = task(projectId, taskId);
        JiraBoard board = activeBoard(projectId);
        String token = credentialService.validAccessToken(board);
        return jiraClient.getTransitions(token, board.getCloudId(), external(task)).stream()
                .map(value -> new JiraTaskTransitionResponse(value.id(), value.name(),
                        value.targetStatusId(), value.targetStatusName())).toList();
    }

    @Transactional
    public TaskReadResponse update(SagaPrincipal principal, UUID projectId, UUID taskId,
            String key, JiraTaskUpdateRequest request) {
        return mutate(principal, projectId, taskId, key, JiraWriteOperationType.TASK_UPDATE,
                fingerprint(request), JiraWriteScope.CLASSIC_WRITE_SCOPE, (token, board, task) -> {
                    Set<String> allowed = jiraClient.getEditMetadata(token, board.getCloudId(), external(task))
                            .stream().map(JiraCreateField::key).collect(java.util.stream.Collectors.toSet());
                    Map<String, Object> fields = updateFields(request, allowed);
                    if (fields.isEmpty()) throw IntegrationException.invalid("JIRA_TASK_UPDATE_EMPTY", "No editable task fields were supplied");
                    jiraClient.updateIssue(token, board.getCloudId(), external(task), fields);
                });
    }

    @Transactional
    public TaskReadResponse transition(SagaPrincipal principal, UUID projectId, UUID taskId,
            String key, String transitionId) {
        return mutate(principal, projectId, taskId, key, JiraWriteOperationType.TASK_TRANSITION,
                transitionId, JiraWriteScope.CLASSIC_WRITE_SCOPE,
                (token, board, task) -> jiraClient.transitionIssue(token, board.getCloudId(), external(task), transitionId));
    }

    @Transactional
    public TaskReadResponse assign(SagaPrincipal principal, UUID projectId, UUID taskId,
            String key, JiraTaskAssigneeRequest request) {
        if (request.unassign() == (request.assigneeId() != null)) throw IntegrationException.invalid(
                "JIRA_ASSIGNEE_REQUEST_INVALID", "Provide either assigneeId or explicit unassign");
        return mutate(principal, projectId, taskId, key, JiraWriteOperationType.TASK_ASSIGN,
                String.valueOf(request.assigneeId()) + "|" + request.unassign(), JiraWriteScope.CLASSIC_WRITE_SCOPE,
                (token, board, task) -> jiraClient.assignIssue(token, board.getCloudId(), external(task),
                        request.unassign() ? null : assignee(request.assigneeId())));
    }

    @Transactional
    public TaskReadResponse sprint(SagaPrincipal principal, UUID projectId, UUID taskId,
            String key, JiraTaskSprintRequest request) {
        if (request.backlog() == (request.sprintId() != null)) throw IntegrationException.invalid(
                "JIRA_SPRINT_REQUEST_INVALID", "Provide either sprintId or explicit backlog");
        Project project = authorization.requireProjectManager(principal, projectId);
        Task task = task(projectId, taskId);
        Sprint target = request.backlog() ? null : sprintRepository
                .findByIdAndBoardProjectIdAndDeletedAtIsNull(request.sprintId(), projectId)
                .orElseThrow(() -> notFound("Sprint not found"));
        JiraWriteOperation operation = operationService.claim(project, principal, JiraWriteOperationType.TASK_SPRINT,
                key, operationService.fingerprint(String.valueOf(request.sprintId()) + "|" + request.backlog()));
        if (operation.getStatus() == JiraWriteOperationStatus.COMPLETED) return TaskReadResponse.from(task);
        JiraBoard board = activeBoard(projectId);
        String token = credentialService.validAccessToken(board);
        if (request.backlog()) {
            JiraWriteScope.requireGranted(board, JiraWriteScope.WRITE_BOARD_SCOPE);
        } else {
            JiraWriteScope.requireGranted(
                    board,
                    JiraWriteScope.WRITE_SPRINT_SCOPE,
                    JiraWriteScope.READ_SPRINT_SCOPE
            );
        }
        if (board.getJiraBoardId() == null || board.getJiraBoardId().isBlank()) {
            throw IntegrationException.conflict("JIRA_BOARD_NOT_CONFIGURED", "The Jira board is not configured");
        }
        if (operation.getStatus() == JiraWriteOperationStatus.PENDING) {
            try {
                if (request.backlog()) {
                    jiraClient.moveIssuesToBacklog(token, board.getCloudId(), board.getJiraBoardId(), List.of(external(task)));
                } else {
                    jiraClient.moveIssuesToSprint(token, board.getCloudId(), target.getExternalSprintId(), List.of(external(task)));
                }
                operationService.markRemoteSucceeded(operation.getId(), task.getExternalId(), task.getExternalKey());
            } catch (IntegrationException exception) {
                operationService.failed(operation.getId(), exception.getCode());
                throw exception;
            } catch (RuntimeException exception) {
                operationService.unknown(operation.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN");
                throw exception;
            }
        }
        reconcile(operation, board, projectId);
        Task reconciled = taskRepository.findByProjectIdAndExternalId(projectId, task.getExternalId())
                .orElseThrow(() -> IntegrationException.conflict("JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery"));
        if (target == null) {
            reconciled.setSprint(null);
        } else {
            Sprint canonical = sprintUpsertService.upsert(board.getId(), jiraClient.getSprint(
                    token, board.getCloudId(), target.getExternalSprintId()));
            reconciled.setSprint(canonical);
        }
        taskRepository.saveAndFlush(reconciled);
        return TaskReadResponse.from(reconciled);
    }

    @Transactional
    public TaskReadResponse estimate(SagaPrincipal principal, UUID projectId, UUID taskId,
            String key, JiraTaskEstimationRequest request) {
        Project project = authorization.requireProjectManager(principal, projectId);
        Task task = task(projectId, taskId);
        JiraWriteOperation operation = operationService.claim(project, principal,
                JiraWriteOperationType.TASK_ESTIMATION, key,
                operationService.fingerprint(String.valueOf(request.value())));
        if (operation.getStatus() == JiraWriteOperationStatus.COMPLETED) {
            return TaskReadResponse.from(task);
        }
        JiraBoard board = activeBoard(projectId);
        String token = credentialService.validAccessToken(board);
        JiraWriteScope.requireGranted(
                board,
                JiraWriteScope.WRITE_ISSUE_SOFTWARE_SCOPE,
                JiraWriteScope.READ_BOARD_ADMIN_SCOPE,
                JiraWriteScope.READ_PROJECT_SCOPE
        );
        if (board.getJiraBoardId() == null || board.getJiraBoardId().isBlank()) {
            throw IntegrationException.conflict("JIRA_BOARD_NOT_CONFIGURED", "The Jira board is not configured");
        }
        String estimationFieldId = jiraClient.estimationFieldId(token, board.getCloudId(), board.getJiraBoardId());
        if (estimationFieldId == null) {
            throw IntegrationException.conflict("JIRA_ESTIMATION_UNSUPPORTED", "The Jira board does not support estimation");
        }
        if (operation.getStatus() == JiraWriteOperationStatus.PENDING) {
            try {
                jiraClient.estimateIssue(token, board.getCloudId(), board.getJiraBoardId(), external(task), request.value());
                operationService.markRemoteSucceeded(operation.getId(), task.getExternalId(), task.getExternalKey());
            } catch (IntegrationException exception) {
                operationService.failed(operation.getId(), exception.getCode());
                throw exception;
            } catch (RuntimeException exception) {
                operationService.unknown(operation.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN");
                throw exception;
            }
        }
        return reconcile(operation, board, projectId, estimationFieldId);
    }

    @Transactional
    public void delete(SagaPrincipal principal, UUID projectId, UUID taskId, String key) {
        Project project = authorization.requireProjectManager(principal, projectId);
        Task task = task(projectId, taskId);
        JiraWriteOperation operation = operationService.claim(project, principal, JiraWriteOperationType.TASK_DELETE,
                key, operationService.fingerprint(task.getExternalId()));
        if (operation.getStatus() == JiraWriteOperationStatus.COMPLETED) return;
        JiraBoard board = activeBoard(projectId);
        String token = credentialService.validAccessToken(board);
        JiraWriteScope.requireGranted(board);
        if (operation.getStatus() == JiraWriteOperationStatus.PENDING) {
            try {
                jiraClient.deleteIssue(token, board.getCloudId(), external(task));
                operation.setRemoteResourceId(task.getExternalId());
                operation.setRemoteResourceKey(task.getExternalKey());
                operationService.markRemoteSucceeded(operation.getId(), task.getExternalId(), task.getExternalKey());
            } catch (IntegrationException exception) {
                operationService.failed(operation.getId(), exception.getCode());
                throw exception;
            } catch (RuntimeException exception) {
                operationService.unknown(operation.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN");
                throw exception;
            }
        }
        task.setDeletedAt(LocalDateTime.now());
        taskRepository.saveAndFlush(task);
        operationService.complete(operation.getId());
    }

    private TaskReadResponse mutate(SagaPrincipal principal, UUID projectId, UUID taskId, String key,
            JiraWriteOperationType type, String fingerprint, String requiredScope, TaskMutation remote) {
        Project project = authorization.requireProjectManager(principal, projectId);
        Task task = task(projectId, taskId);
        JiraWriteOperation operation = operationService.claim(project, principal, type, key, operationService.fingerprint(fingerprint));
        if (operation.getStatus() == JiraWriteOperationStatus.COMPLETED) return TaskReadResponse.from(task);
        JiraBoard board = activeBoard(projectId); String token = credentialService.validAccessToken(board);
        JiraWriteScope.requireGranted(board, requiredScope);
        if (operation.getStatus() == JiraWriteOperationStatus.REMOTE_SUCCEEDED) return reconcile(operation, board, projectId);
        try {
            remote.apply(token, board, task);
            operation.setRemoteResourceId(task.getExternalId()); operation.setRemoteResourceKey(task.getExternalKey());
            operationService.markRemoteSucceeded(operation.getId(), task.getExternalId(), task.getExternalKey());
        } catch (IntegrationException exception) { operationService.failed(operation.getId(), exception.getCode()); throw exception;
        } catch (RuntimeException exception) { operationService.unknown(operation.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN"); throw exception; }
        return reconcile(operation, board, projectId);
    }

    private Task task(UUID projectId, UUID taskId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId).orElseThrow(() -> notFound("Task not found"));
    }

    private IntegrationException notFound(String message) { return new IntegrationException(HttpStatus.NOT_FOUND, "JIRA_RESOURCE_NOT_FOUND", message); }

    private String external(Task task) { if (task.getExternalId() == null || task.getExternalId().isBlank()) throw IntegrationException.conflict("JIRA_TASK_NOT_LINKED", "The task is not linked to Jira"); return task.getExternalId(); }

    private Map<String, Object> updateFields(JiraTaskUpdateRequest request, Set<String> allowed) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (request.title() != null) fields.put("summary", requireEditable(allowed, "summary", request.title().trim()));
        if (request.description() != null) fields.put("description", requireEditable(allowed, "description", adf(request.description())));
        if (request.priorityId() != null) fields.put("priority", requireEditable(allowed, "priority", Map.of("id", request.priorityId())));
        if (request.dueDate() != null) fields.put("duedate", requireEditable(allowed, "duedate", request.dueDate().toString()));
        if (request.labels() != null) fields.put("labels", requireEditable(allowed, "labels", List.copyOf(request.labels())));
        if (request.componentIds() != null) fields.put("components", requireEditable(allowed, "components", request.componentIds().stream().map(id -> Map.of("id", id)).toList()));
        return fields;
    }

    private Object requireEditable(Set<String> allowed, String field, Object value) { if (!allowed.contains(field)) throw IntegrationException.invalid("JIRA_EDIT_FIELD_NOT_ALLOWED", "The Jira edit metadata does not allow the requested field"); return value; }

    @FunctionalInterface private interface TaskMutation { void apply(String token, JiraBoard board, Task task); }

    @Transactional
    public TaskReadResponse create(SagaPrincipal principal, UUID projectId, String idempotencyKey, JiraTaskCreateRequest request) {
        JiraWriteOperation operation = null;
        JiraBoard board = null;
        TaskCreateStage stage = TaskCreateStage.SCOPE_PREFLIGHT;
        TaskCreateResourceType resourceType = TaskCreateResourceType.PROJECT;
        TaskCreateResolutionMode resolutionMode = TaskCreateResolutionMode.NOT_APPLICABLE;
        TaskCreateResolutionResult resolutionResult = TaskCreateResolutionResult.NOT_APPLICABLE;
        try {
            Project project = authorization.requireProjectManager(principal, projectId);
            stage = TaskCreateStage.WRITE_OPERATION_CLAIM;
            String fingerprint = operationService.fingerprint(fingerprint(request));
            operation = operationService.claim(project, principal,
                    JiraWriteOperationType.TASK_CREATE, idempotencyKey, fingerprint);
            if (operation.getStatus() == JiraWriteOperationStatus.COMPLETED) {
                stage = TaskCreateStage.LOCAL_UPSERT;
                resourceType = TaskCreateResourceType.CREATED_ISSUE;
                return completed(operation, projectId);
            }

            stage = TaskCreateStage.SCOPE_PREFLIGHT;
            resourceType = TaskCreateResourceType.PROJECT;
            board = activeBoard(projectId);
            String accessToken = credentialService.validAccessToken(board);
            JiraWriteScope.requireGranted(board);
            if (operation.getStatus() == JiraWriteOperationStatus.REMOTE_SUCCEEDED) {
                stage = TaskCreateStage.CANONICAL_ISSUE_FETCH;
                resourceType = TaskCreateResourceType.CREATED_ISSUE;
                return reconcile(operation, board, projectId);
            }

            stage = TaskCreateStage.JIRA_METADATA_ISSUE_TYPES;
            resourceType = TaskCreateResourceType.ISSUE_TYPE;
            List<JiraCreateIssueType> issueTypes = jiraClient.getCreateIssueTypes(
                    accessToken, board.getCloudId(), board.getJiraProjectId());
            stage = TaskCreateStage.ISSUE_TYPE_RESOLUTION;
            resolutionMode = request.issueTypeId() == null
                    ? TaskCreateResolutionMode.AUTO
                    : TaskCreateResolutionMode.EXPLICIT;
            resolutionResult = TaskCreateResolutionResult.NOT_APPLICABLE;
            String issueTypeId = resolveIssueType(issueTypes, request, resolutionMode);
            resolutionResult = TaskCreateResolutionResult.RESOLVED;
            stage = TaskCreateStage.JIRA_METADATA_CREATE_FIELDS;
            List<JiraCreateField> createFields = jiraClient.getCreateFields(
                    accessToken, board.getCloudId(), board.getJiraProjectId(), issueTypeId);
            Set<String> allowed = allowedCreateFields(createFields);
            requireCreateRequiredFields(allowed);
            requireAllowed(allowed, "description", request.description());
            stage = TaskCreateStage.PRIORITY_RESOLUTION;
            resourceType = TaskCreateResourceType.PRIORITY;
            resolutionMode = request.priorityId() == null
                    ? TaskCreateResolutionMode.AUTO
                    : TaskCreateResolutionMode.EXPLICIT;
            resolutionResult = TaskCreateResolutionResult.NOT_APPLICABLE;
            String priorityId = resolvePriority(createFields, request, allowed, resolutionMode);
            resolutionResult = priorityId == null
                    ? TaskCreateResolutionResult.NOT_APPLICABLE
                    : TaskCreateResolutionResult.RESOLVED;
            stage = TaskCreateStage.JIRA_METADATA_CREATE_FIELDS;
            resourceType = TaskCreateResourceType.PROJECT;
            requireAllowed(allowed, "duedate", request.dueDate());
            requireAllowed(allowed, "labels", request.labels());
            requireAllowed(allowed, "components", request.componentIds());
            stage = TaskCreateStage.ASSIGNEE_RESOLUTION;
            resourceType = TaskCreateResourceType.ASSIGNEE;
            requireAllowed(allowed, "assignee", request.assigneeId());
            String externalAssignee = request.assigneeId() == null ? null : assignee(request.assigneeId());
            stage = TaskCreateStage.JIRA_PROVIDER_CREATE_ISSUE;
            resourceType = TaskCreateResourceType.PROJECT;
            resolutionMode = TaskCreateResolutionMode.NOT_APPLICABLE;
            resolutionResult = TaskCreateResolutionResult.NOT_APPLICABLE;
            JiraIssueReference remote = jiraClient.createIssue(
                    accessToken, board.getCloudId(), fields(board, request, issueTypeId, priorityId, externalAssignee));
            operation.setRemoteResourceId(remote.id());
            operation.setRemoteResourceKey(remote.key());
            operation.setStatus(JiraWriteOperationStatus.REMOTE_SUCCEEDED);
            operationService.markRemoteSucceeded(operation.getId(), remote.id(), remote.key());
        } catch (IntegrationException exception) {
            if (operation != null && stage.isBeforeRemoteSuccess()) {
                operationService.failed(operation.getId(), exception.getCode());
            }
            logCreateFailure(projectId, stage, resourceType, resolutionMode, resolutionResult(resolutionResult, exception), exception,
                    operationStatus(operation, stage.isBeforeRemoteSuccess() ? JiraWriteOperationStatus.FAILED : null));
            throw exception;
        } catch (RuntimeException exception) {
            if (operation != null && stage.isBeforeRemoteSuccess()) {
                operationService.unknown(operation.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN");
            }
            logCreateFailure(projectId, stage, resourceType, resolutionMode, resolutionResult, null,
                    operationStatus(operation, stage.isBeforeRemoteSuccess() ? JiraWriteOperationStatus.UNKNOWN : null));
            throw exception;
        }
        stage = TaskCreateStage.CANONICAL_ISSUE_FETCH;
        resourceType = TaskCreateResourceType.CREATED_ISSUE;
        try {
            return reconcile(operation, board, projectId);
        } catch (IntegrationException exception) {
            logCreateFailure(projectId, stage, resourceType, resolutionMode, resolutionResult, exception, operationStatus(operation, null));
            throw exception;
        } catch (RuntimeException exception) {
            logCreateFailure(projectId, stage, resourceType, resolutionMode, resolutionResult, null, operationStatus(operation, null));
            throw exception;
        }
    }

    private TaskReadResponse reconcile(JiraWriteOperation operation, JiraBoard board, UUID projectId) {
        return reconcile(operation, board, projectId, null);
    }

    private TaskReadResponse reconcile(
            JiraWriteOperation operation,
            JiraBoard board,
            UUID projectId,
            String estimationFieldId
    ) {
        if (operation.getRemoteResourceId() == null) throw IntegrationException.conflict(
                "JIRA_WRITE_OPERATION_IN_PROGRESS", "The Jira write outcome is still being recovered");
        String token = credentialService.validAccessToken(board);
        issueUpsertService.upsert(board.getId(), estimationFieldId == null
                ? jiraClient.getIssue(token, board.getCloudId(), operation.getRemoteResourceId())
                : jiraClient.getIssue(token, board.getCloudId(), operation.getRemoteResourceId(), estimationFieldId));
        operationService.complete(operation.getId());
        return completed(operation, projectId);
    }

    private TaskReadResponse completed(JiraWriteOperation operation, UUID projectId) {
        return taskRepository.findByProjectIdAndExternalId(projectId, operation.getRemoteResourceId())
                .map(TaskReadResponse::from).orElseThrow(() -> IntegrationException.conflict(
                        "JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery"));
    }

    private JiraBoard activeBoard(UUID projectId) {
        JiraBoard board = boardRepository.findByProjectId(projectId).orElseThrow(() -> IntegrationException.conflict(
                "JIRA_LINK_NOT_FOUND", "The project has no Jira connection"));
        if (board.getConnectionStatus() != IntegrationStatus.ACTIVE) throw IntegrationException.conflict(
                "JIRA_INTEGRATION_NOT_ACTIVE", "The Jira integration is not active");
        return board;
    }

    private String resolveIssueType(
            List<JiraCreateIssueType> issueTypes,
            JiraTaskCreateRequest request,
            TaskCreateResolutionMode resolutionMode
    ) {
        if (resolutionMode == TaskCreateResolutionMode.EXPLICIT) {
            return issueTypes.stream()
                    .filter(issueType -> issueType.id().equals(request.issueTypeId()))
                    .map(JiraCreateIssueType::id)
                    .findFirst()
                    .orElseThrow(() -> IntegrationException.invalid(
                            "JIRA_ISSUE_TYPE_INVALID", "The Jira issue type is not available for this project"));
        }
        if (request.type() == null) {
            throw IntegrationException.invalid(
                    "JIRA_TASK_TYPE_REQUIRED", "A business task type is required when no Jira issue type override is supplied");
        }
        return exactlyOne(
                issueTypes.stream().filter(issueType -> taskType(issueType.name()) == request.type()).map(JiraCreateIssueType::id).toList(),
                "JIRA_ISSUE_TYPE_RESOLUTION_NOT_FOUND",
                "JIRA_ISSUE_TYPE_RESOLUTION_AMBIGUOUS",
                "The Jira issue type could not be resolved uniquely for this project"
        );
    }

    private Set<String> allowedCreateFields(List<JiraCreateField> fields) {
        return fields.stream().map(JiraCreateField::key).collect(java.util.stream.Collectors.toSet());
    }

    private void requireCreateRequiredFields(Set<String> allowed) {
        if (!allowed.contains("summary") || !allowed.contains("issuetype"))
            throw IntegrationException.conflict("JIRA_CREATE_METADATA_INVALID", "Jira create metadata does not permit required issue fields");
    }

    private void requireAllowed(Set<String> allowed, String field, Object value) {
        if (value != null && !allowed.contains(field)) {
            throw IntegrationException.invalid(
                    "JIRA_CREATE_FIELD_NOT_ALLOWED",
                    "The Jira create metadata does not allow the requested field"
            );
        }
    }

    private String resolvePriority(
            List<JiraCreateField> createFields,
            JiraTaskCreateRequest request,
            Set<String> allowed,
            TaskCreateResolutionMode resolutionMode
    ) {
        if (request.priorityId() == null && request.priority() == null) return null;
        requireAllowed(allowed, "priority", request.priorityId() != null ? request.priorityId() : request.priority());
        JiraCreateField priority = createFields.stream()
                .filter(field -> "priority".equals(field.key()))
                .findFirst()
                .orElseThrow(() -> IntegrationException.invalid(
                        "JIRA_CREATE_FIELD_NOT_ALLOWED", "The Jira create metadata does not allow the requested field"));
        if (resolutionMode == TaskCreateResolutionMode.EXPLICIT) {
            return priority.allowedValues().stream()
                    .filter(value -> request.priorityId().equals(value.id()))
                    .map(JiraCreateFieldAllowedValue::id)
                    .findFirst()
                    .orElseThrow(() -> IntegrationException.invalid(
                            "JIRA_PRIORITY_INVALID", "The Jira priority is not available for this project"));
        }
        if (request.priority() == null) return null;
        return exactlyOne(
                priority.allowedValues().stream()
                        .filter(value -> priority(value.name()) == request.priority())
                        .map(JiraCreateFieldAllowedValue::id)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                "JIRA_PRIORITY_RESOLUTION_NOT_FOUND",
                "JIRA_PRIORITY_RESOLUTION_AMBIGUOUS",
                "The Jira priority could not be resolved uniquely for this project"
        );
    }

    private String exactlyOne(List<String> candidates, String notFoundCode, String ambiguousCode, String message) {
        if (candidates.size() == 1) return candidates.get(0);
        if (candidates.isEmpty()) throw IntegrationException.conflict(notFoundCode, message);
        throw IntegrationException.conflict(ambiguousCode, message);
    }

    private TaskType taskType(String value) {
        return switch (normalize(value)) {
            case "BUG" -> TaskType.BUG;
            case "FEATURE", "NEW_FEATURE" -> TaskType.FEATURE;
            case "STORY", "USER_STORY" -> TaskType.STORY;
            case "EPIC" -> TaskType.EPIC;
            case "SUBTASK", "SUB_TASK" -> TaskType.SUBTASK;
            default -> TaskType.TASK;
        };
    }

    private Priority priority(String value) {
        String normalized = normalize(value);
        if (normalized.contains("HIGHEST") || normalized.contains("CRITICAL")) return Priority.CRITICAL;
        if (normalized.contains("HIGH")) return Priority.HIGH;
        if (normalized.contains("LOW")) return Priority.LOW;
        return Priority.MEDIUM;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private Map<String, Object> fields(
            JiraBoard board,
            JiraTaskCreateRequest request,
            String issueTypeId,
            String priorityId,
            String externalAssignee
    ) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("id", board.getJiraProjectId()));
        fields.put("summary", request.title().trim());
        fields.put("issuetype", Map.of("id", issueTypeId));
        if (request.description() != null) fields.put("description", adf(request.description()));
        if (priorityId != null) fields.put("priority", Map.of("id", priorityId));
        if (request.dueDate() != null) fields.put("duedate", request.dueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (request.labels() != null) fields.put("labels", List.copyOf(request.labels()));
        if (request.componentIds() != null) fields.put("components", request.componentIds().stream().map(id -> Map.of("id", id)).toList());
        if (externalAssignee != null) fields.put("assignee", Map.of("accountId", externalAssignee));
        return fields;
    }

    private void logCreateFailure(
            UUID projectId,
            TaskCreateStage stage,
            TaskCreateResourceType resourceType,
            TaskCreateResolutionMode resolutionMode,
            TaskCreateResolutionResult resolutionResult,
            IntegrationException exception,
            String writeOperationStatus
    ) {
        log.warn("Jira task create failed: projectId={}, operation=TASK_CREATE, stage={}, resourceType={}, "
                        + "resolutionMode={}, resolutionResult={}, upstreamHttpStatus={}, errorCategory={}, writeOperationStatus={}",
                projectId,
                stage,
                resourceType,
                resolutionMode,
                resolutionResult,
                upstreamHttpStatus(exception),
                exception == null ? "JIRA_WRITE_OUTCOME_UNKNOWN" : exception.getCode(),
                writeOperationStatus);
    }

    private String upstreamHttpStatus(IntegrationException exception) {
        if (exception == null) return "UNKNOWN";
        return switch (exception.getCode()) {
            case "JIRA_REQUEST_REJECTED" -> "400";
            case "JIRA_ACCESS_REVOKED" -> "401";
            case "JIRA_ACCESS_FORBIDDEN" -> "403";
            case "JIRA_RESOURCE_NOT_FOUND" -> "404";
            case "JIRA_RATE_LIMITED" -> "429";
            default -> "NONE";
        };
    }

    private TaskCreateResolutionResult resolutionResult(
            TaskCreateResolutionResult current,
            IntegrationException exception
    ) {
        if (exception == null) return current;
        return switch (exception.getCode()) {
            case "JIRA_ISSUE_TYPE_INVALID", "JIRA_PRIORITY_INVALID", "JIRA_TASK_TYPE_REQUIRED" ->
                    TaskCreateResolutionResult.INVALID;
            case "JIRA_ISSUE_TYPE_RESOLUTION_NOT_FOUND", "JIRA_PRIORITY_RESOLUTION_NOT_FOUND" ->
                    TaskCreateResolutionResult.NOT_FOUND;
            case "JIRA_ISSUE_TYPE_RESOLUTION_AMBIGUOUS", "JIRA_PRIORITY_RESOLUTION_AMBIGUOUS" ->
                    TaskCreateResolutionResult.AMBIGUOUS;
            default -> current;
        };
    }

    private String operationStatus(JiraWriteOperation operation, JiraWriteOperationStatus replacement) {
        if (replacement != null) return replacement.name();
        return operation == null ? "NOT_CLAIMED" : operation.getStatus().name();
    }

    private enum TaskCreateStage {
        SCOPE_PREFLIGHT,
        WRITE_OPERATION_CLAIM,
        JIRA_METADATA_ISSUE_TYPES,
        JIRA_METADATA_CREATE_FIELDS,
        ISSUE_TYPE_RESOLUTION,
        PRIORITY_RESOLUTION,
        ASSIGNEE_RESOLUTION,
        JIRA_PROVIDER_CREATE_ISSUE,
        CANONICAL_ISSUE_FETCH,
        LOCAL_UPSERT;

        private boolean isBeforeRemoteSuccess() {
            return this != SCOPE_PREFLIGHT
                    && this != WRITE_OPERATION_CLAIM
                    && this != CANONICAL_ISSUE_FETCH
                    && this != LOCAL_UPSERT;
        }
    }

    private enum TaskCreateResourceType {
        ISSUE_TYPE,
        PRIORITY,
        ASSIGNEE,
        PROJECT,
        CREATED_ISSUE
    }

    private enum TaskCreateResolutionMode {
        AUTO,
        EXPLICIT,
        NOT_APPLICABLE
    }

    private enum TaskCreateResolutionResult {
        RESOLVED,
        NOT_FOUND,
        AMBIGUOUS,
        INVALID,
        NOT_APPLICABLE
    }

    private String assignee(UUID studentId) {
        IdentityMap mapping = identityMapRepository.findByStudentIdAndProvider(studentId, IntegrationProvider.JIRA)
                .filter(value -> value.getMappingStatus() == IdentityMappingStatus.ACTIVE).orElseThrow(() ->
                        IntegrationException.conflict("JIRA_IDENTITY_MAPPING_MISSING", "The assignee has no active Jira identity mapping"));
        return mapping.getExternalAccountId();
    }

    private Map<String, Object> adf(String value) {
        return Map.of("type", "doc", "version", 1, "content", List.of(Map.of("type", "paragraph",
                "content", List.of(Map.of("type", "text", "text", value)))));
    }

    private String fingerprint(JiraTaskCreateRequest request) {
        return String.join("|", request.title(), String.valueOf(request.type()), String.valueOf(request.issueTypeId()),
                String.valueOf(request.priority()), String.valueOf(request.description()), String.valueOf(request.priorityId()), String.valueOf(request.dueDate()), String.valueOf(request.labels()),
                String.valueOf(request.componentIds()), String.valueOf(request.assigneeId()));
    }

    private String fingerprint(JiraTaskUpdateRequest request) {
        return String.join("|", String.valueOf(request.title()), String.valueOf(request.description()),
                String.valueOf(request.priorityId()), String.valueOf(request.dueDate()),
                String.valueOf(request.labels()), String.valueOf(request.componentIds()));
    }
}
