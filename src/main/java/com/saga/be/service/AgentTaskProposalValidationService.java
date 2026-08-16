package com.saga.be.service;

import com.saga.be.dto.request.InternalAgentToolRequests;
import com.saga.be.dto.response.InternalAgentToolResponses.ActionValidation;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.security.SagaPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentTaskProposalValidationService {

    private final ProjectIntegrationAuthorizationService authorization;
    private final ProjectTaskReadService taskReads;

    public AgentTaskProposalValidationService(
            ProjectIntegrationAuthorizationService authorization,
            ProjectTaskReadService taskReads
    ) {
        this.authorization = authorization;
        this.taskReads = taskReads;
    }

    public ActionValidation validateCreate(
            SagaPrincipal actor, InternalAgentToolRequests.TaskCreate request
    ) {
        authorization.requireProjectManager(actor, request.projectId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", request.projectId().toString());
        payload.put("title", request.title().trim());
        payload.put("type", request.type().name());
        put(payload, "priority", request.priority() == null ? null : request.priority().name());
        put(payload, "description", normalize(request.description()));
        put(payload, "dueDate", request.dueDate() == null ? null : request.dueDate().toString());
        put(payload, "labels", request.labels());
        put(payload, "componentIds", request.componentIds());
        put(payload, "assigneeId", request.assigneeId() == null ? null : request.assigneeId().toString());
        return new ActionValidation(
                true,
                Map.copyOf(payload),
                createSummary(request, payload)
        );
    }

    public ActionValidation validateUpdate(
            SagaPrincipal actor, InternalAgentToolRequests.TaskUpdate request
    ) {
        authorization.requireProjectManager(actor, request.projectId());
        taskReads.getTask(actor, request.projectId(), request.taskId());
        if (request.title() == null
                && request.description() == null
                && request.type() == null
                && request.priority() == null
                && request.dueDate() == null
                && request.labels() == null
                && request.componentIds() == null) {
            throw IntegrationException.invalid(
                    "AGENT_TASK_UPDATE_EMPTY", "At least one supported Task field is required"
            );
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", request.projectId().toString());
        payload.put("taskId", request.taskId().toString());
        put(payload, "title", normalize(request.title()));
        put(payload, "description", normalize(request.description()));
        put(payload, "type", request.type() == null ? null : request.type().name());
        put(payload, "priority", request.priority() == null ? null : request.priority().name());
        put(payload, "dueDate", request.dueDate() == null ? null : request.dueDate().toString());
        put(payload, "labels", request.labels());
        put(payload, "componentIds", request.componentIds());
        return new ActionValidation(
                true,
                Map.copyOf(payload),
                "Update Task " + request.taskId() + " with " + (payload.size() - 2) + " field(s)"
        );
    }

    private String createSummary(
            InternalAgentToolRequests.TaskCreate request, Map<String, Object> payload
    ) {
        StringBuilder summary = new StringBuilder("Create Task '")
                .append(request.title().trim())
                .append("' as ")
                .append(request.type().name());
        if (payload.containsKey("priority")) {
            summary.append(", priority ").append(payload.get("priority"));
        }
        if (payload.containsKey("assigneeId")) {
            summary.append(", assignee resolved");
        }
        if (payload.containsKey("dueDate")) {
            summary.append(", due ").append(payload.get("dueDate"));
        }
        if (payload.containsKey("description")) {
            summary.append(", description prepared");
        }
        return summary.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void put(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}

