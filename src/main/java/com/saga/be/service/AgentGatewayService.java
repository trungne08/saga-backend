package com.saga.be.service;

import com.saga.be.dto.request.AgentConversationCreateRequest;
import com.saga.be.dto.request.AgentMessageSendRequest;
import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.entity.Student;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentGatewayService {

    private static final Set<String> CREATE_FIELDS = Set.of(
            "projectId", "title", "type", "priority", "description", "dueDate",
            "labels", "componentIds", "assigneeId"
    );
    private static final Set<String> UPDATE_FIELDS = Set.of(
            "projectId", "taskId", "title", "description", "type", "priority",
            "dueDate", "labels", "componentIds"
    );

    private final AgentAiClient ai;
    private final AgentDelegationService delegations;
    private final JiraTaskWriteService taskWrites;
    private final ProjectDetailService projects;
    private final StudentRepository students;
    private final LecturerAnalyticsAuthorizationService courseAccess;
    private final TeamMemberRepository teamMembers;

    public AgentGatewayService(
            AgentAiClient ai,
            AgentDelegationService delegations,
            JiraTaskWriteService taskWrites,
            ProjectDetailService projects,
            StudentRepository students,
            LecturerAnalyticsAuthorizationService courseAccess,
            TeamMemberRepository teamMembers
    ) {
        this.ai = ai;
        this.delegations = delegations;
        this.taskWrites = taskWrites;
        this.projects = projects;
        this.students = students;
        this.courseAccess = courseAccess;
        this.teamMembers = teamMembers;
    }

    public AgentApiResponses.Conversation create(
            SagaPrincipal actor, AgentConversationCreateRequest request
    ) {
        return ai.createConversation(actor, request == null ? null : request.title(), studentCode(actor));
    }

    public AgentApiResponses.ConversationList list(SagaPrincipal actor) {
        return ai.listConversations(actor);
    }

    public AgentApiResponses.Conversation get(SagaPrincipal actor, UUID conversationId) {
        return ai.conversation(actor, conversationId);
    }

    public AgentApiResponses.Chat send(
            SagaPrincipal actor,
            UUID conversationId,
            AgentMessageSendRequest request
    ) {
        String context = delegations.issue(actor, conversationId);
        return ai.sendMessage(actor, conversationId, context, request.content().trim(), studentCode(actor));
    }

    public AgentApiResponses.ActionExecution confirm(SagaPrincipal actor, UUID actionId) {
        AgentApiResponses.PendingAction action = ai.claimAction(actor, actionId);
        try {
            TaskReadResponse result = switch (action.actionType()) {
                case "TASK_CREATE" -> createTask(actor, action);
                case "TASK_UPDATE" -> updateTask(actor, action);
                default -> throw IntegrationException.invalid(
                        "AI_AGENT_ACTION_UNSUPPORTED", "The proposed action type is unsupported"
                );
            };
            ai.finalizeAction(actor, actionId, true, null);
            return new AgentApiResponses.ActionExecution(actionId, "COMPLETED", result);
        } catch (IntegrationException exception) {
            finalizeFailure(actor, actionId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            finalizeFailure(actor, actionId, "AI_AGENT_ACTION_EXECUTION_FAILED");
            throw exception;
        }
    }

    public AgentApiResponses.PendingAction reject(SagaPrincipal actor, UUID actionId) {
        return ai.rejectAction(actor, actionId);
    }

    public DownloadedArtifact download(SagaPrincipal actor, UUID artifactId) {
        AgentApiResponses.GeneratedArtifact metadata = ai.artifact(actor, artifactId);
        reauthorizeArtifact(actor, metadata);
        if (metadata.filename() == null
                || !metadata.filename().matches("[A-Za-z0-9._-]{1,180}\\.docx")) {
            throw IntegrationException.unavailable("AI_ARTIFACT_FILENAME_INVALID");
        }
        byte[] content = ai.artifactContent(actor, artifactId);
        if (content.length == 0 || content.length > 10_000_000) {
            throw IntegrationException.unavailable("AI_ARTIFACT_CONTENT_INVALID");
        }
        return new DownloadedArtifact(metadata.filename(), metadata.mediaType(), content);
    }

    private void reauthorizeArtifact(SagaPrincipal actor, AgentApiResponses.GeneratedArtifact metadata) {
        String artifactType = metadata.artifactType();
        String scopeType = metadata.scopeType();
        if ("SRS_DOCX".equals(artifactType) && "PROJECT".equals(scopeType)) {
            projects.get(actor, uuid(metadata.scopeId(), "scopeId"));
            return;
        }
        if ("LECTURER_PROGRESS_REPORT".equals(artifactType) && "COURSE".equals(scopeType)) {
            requireCourseReportAccess(actor, uuid(metadata.scopeId(), "scopeId"));
            return;
        }
        if ("ADMIN_SYSTEM_REPORT".equals(artifactType) && "SYSTEM".equals(scopeType)) {
            if (actor == null || actor.applicationRole() != ApplicationRole.ADMIN) {
                throw IntegrationException.forbidden("The generated artifact is not available to the current actor");
            }
            return;
        }
        if ("LEADER_TEAM_PROGRESS_REPORT".equals(artifactType) && "TEAM".equals(scopeType)) {
            requireLeaderTeamAccess(actor, uuid(metadata.scopeId(), "scopeId"));
            return;
        }
        throw IntegrationException.invalid(
                "AI_ARTIFACT_SCOPE_INVALID", "The generated artifact scope is invalid"
        );
    }

    private void requireLeaderTeamAccess(SagaPrincipal actor, UUID teamId) {
        if (actor == null
                || actor.applicationRole() != ApplicationRole.STUDENT
                || actor.localProfileId() == null
                || !teamMembers.existsByTeamIdAndStudentIdAndRoleInTeam(
                        teamId, actor.localProfileId(), RoleInTeam.LEADER
                )) {
            throw IntegrationException.forbidden("The generated artifact is not available to the current actor");
        }
    }

    private void requireCourseReportAccess(SagaPrincipal actor, UUID courseId) {
        try {
            courseAccess.requireCourseAccess(actor, courseId);
        } catch (AccessDeniedException ignored) {
            throw IntegrationException.forbidden("The generated artifact is not available to the current actor");
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw IntegrationException.invalid(
                        "AI_ARTIFACT_SCOPE_INVALID", "The generated artifact scope is invalid"
                );
            }
            throw exception;
        }
    }

    private String studentCode(SagaPrincipal actor) {
        if (actor == null || actor.applicationRole() != ApplicationRole.STUDENT
                || actor.localProfileId() == null) {
            return null;
        }
        return students.findById(actor.localProfileId()).map(Student::getStudentCode).orElse(null);
    }

    private TaskReadResponse createTask(
            SagaPrincipal actor, AgentApiResponses.PendingAction action
    ) {
        Map<String, Object> payload = payload(action, CREATE_FIELDS);
        UUID projectId = uuid(payload.get("projectId"), "projectId");
        JiraTaskCreateRequest request = new JiraTaskCreateRequest(
                required(payload, "title"),
                enumValue(TaskType.class, payload.get("type"), "type"),
                null,
                optionalEnum(Priority.class, payload.get("priority"), "priority"),
                null,
                optional(payload, "description"),
                optionalDate(payload.get("dueDate"), "dueDate"),
                stringList(payload.get("labels"), "labels"),
                stringList(payload.get("componentIds"), "componentIds"),
                optionalUuid(payload.get("assigneeId"), "assigneeId")
        );
        return taskWrites.create(actor, projectId, action.idempotencyKey(), request);
    }

    private TaskReadResponse updateTask(
            SagaPrincipal actor, AgentApiResponses.PendingAction action
    ) {
        Map<String, Object> payload = payload(action, UPDATE_FIELDS);
        UUID projectId = uuid(payload.get("projectId"), "projectId");
        UUID taskId = uuid(payload.get("taskId"), "taskId");
        JiraTaskUpdateRequest request = new JiraTaskUpdateRequest(
                optional(payload, "title"),
                optional(payload, "description"),
                optionalEnum(TaskType.class, payload.get("type"), "type"),
                optionalEnum(Priority.class, payload.get("priority"), "priority"),
                null,
                optionalDate(payload.get("dueDate"), "dueDate"),
                stringList(payload.get("labels"), "labels"),
                stringList(payload.get("componentIds"), "componentIds")
        );
        return taskWrites.update(actor, projectId, taskId, action.idempotencyKey(), request);
    }

    private Map<String, Object> payload(
            AgentApiResponses.PendingAction action, Set<String> allowed
    ) {
        if (action.payload() == null || !allowed.containsAll(action.payload().keySet())) {
            throw IntegrationException.invalid(
                    "AI_AGENT_ACTION_PAYLOAD_INVALID", "The proposed action payload is invalid"
            );
        }
        return action.payload();
    }

    private void finalizeFailure(SagaPrincipal actor, UUID actionId, String safeCode) {
        try {
            ai.finalizeAction(actor, actionId, false, safeCode);
        } catch (RuntimeException ignored) {
            // Jira mutation ownership remains in JiraWriteOperation. Never retry the
            // business mutation merely because AI state finalization is unavailable.
        }
    }

    private String required(Map<String, Object> payload, String key) {
        String value = optional(payload, key);
        if (value == null) {
            throw invalidField(key);
        }
        return value;
    }

    private String optional(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalidField(key);
        }
        return text.trim();
    }

    private UUID uuid(Object value, String field) {
        if (!(value instanceof String text)) {
            throw invalidField(field);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw invalidField(field);
        }
    }

    private UUID optionalUuid(Object value, String field) {
        return value == null ? null : uuid(value, field);
    }

    private LocalDate optionalDate(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw invalidField(field);
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw invalidField(field);
        }
    }

    private List<String> stringList(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> values) || values.size() > 20
                || values.stream().anyMatch(item -> !(item instanceof String text)
                || text.isBlank() || text.length() > 255)) {
            throw invalidField(field);
        }
        return values.stream().map(item -> ((String) item).trim()).toList();
    }

    private <E extends Enum<E>> E enumValue(
            Class<E> type, Object value, String field
    ) {
        if (!(value instanceof String text)) {
            throw invalidField(field);
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw invalidField(field);
        }
    }

    private <E extends Enum<E>> E optionalEnum(
            Class<E> type, Object value, String field
    ) {
        return value == null ? null : enumValue(type, value, field);
    }

    private IntegrationException invalidField(String field) {
        return IntegrationException.invalid(
                "AI_AGENT_ACTION_PAYLOAD_INVALID", "The proposed action payload is invalid"
        );
    }

    public record DownloadedArtifact(String filename, String mediaType, byte[] content) {
    }
}

