package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskUpdateRequest;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentGatewayServiceTest {

    @Test
    void confirmCreateCallsExistingJiraServiceExactlyOnceWithStableIdempotency() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID actionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.claimAction(actor, actionId)).thenReturn(action(
                actionId,
                "TASK_CREATE",
                Map.of(
                        "projectId", projectId.toString(),
                        "title", "Fix login",
                        "type", "BUG",
                        "priority", "HIGH"
                )
        ));

        service.confirm(actor, actionId);

        ArgumentCaptor<JiraTaskCreateRequest> request = ArgumentCaptor.forClass(JiraTaskCreateRequest.class);
        verify(writes).create(eq(actor), eq(projectId), eq("saga-agent-stable"), request.capture());
        assertEquals("Fix login", request.getValue().title());
        assertEquals(TaskType.BUG, request.getValue().type());
        assertEquals(Priority.HIGH, request.getValue().priority());
        assertNull(request.getValue().issueTypeId());
        assertNull(request.getValue().priorityId());
        verify(ai).finalizeAction(actor, actionId, true, null);
    }

    @Test
    void confirmSparseUpdateCannotSmuggleDedicatedOperationFields() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.LECTURER);
        UUID actionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(ai.claimAction(actor, actionId)).thenReturn(action(
                actionId,
                "TASK_UPDATE",
                Map.of(
                        "projectId", projectId.toString(),
                        "taskId", taskId.toString(),
                        "title", "Updated title",
                        "type", "REQUEST"
                )
        ));

        service.confirm(actor, actionId);

        ArgumentCaptor<JiraTaskUpdateRequest> request = ArgumentCaptor.forClass(JiraTaskUpdateRequest.class);
        verify(writes).update(eq(actor), eq(projectId), eq(taskId), eq("saga-agent-stable"), request.capture());
        assertEquals("Updated title", request.getValue().title());
        assertEquals(TaskType.REQUEST, request.getValue().type());
        assertNull(request.getValue().priorityId());
    }

    @Test
    void rejectNeverCallsJiraWriteService() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentGatewayService service = service(ai, writes);
        SagaPrincipal actor = actor(ApplicationRole.ADMIN);
        UUID actionId = UUID.randomUUID();
        when(ai.rejectAction(actor, actionId)).thenReturn(action(actionId, "TASK_CREATE", null));

        service.reject(actor, actionId);

        verifyNoInteractions(writes);
        verify(ai, never()).claimAction(any(), any());
    }

    @Test
    void artifactDownloadReauthorizesProjectBeforeFetchingContent() {
        AgentAiClient ai = mock(AgentAiClient.class);
        ProjectDetailService projects = mock(ProjectDetailService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), projects
        );
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID artifactId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.artifact(actor, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "SRS_DOCX", "PROJECT",
                projectId.toString(), "SRS-project.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));
        when(ai.artifactContent(actor, artifactId)).thenReturn(new byte[]{1, 2, 3});

        AgentGatewayService.DownloadedArtifact result = service.download(actor, artifactId);

        verify(projects).get(actor, projectId);
        verify(ai).artifactContent(actor, artifactId);
        assertEquals("SRS-project.docx", result.filename());
    }

    @Test
    void unsafeArtifactFilenameIsRejectedBeforeContentFetch() {
        AgentAiClient ai = mock(AgentAiClient.class);
        ProjectDetailService projects = mock(ProjectDetailService.class);
        AgentGatewayService service = new AgentGatewayService(
                ai, mock(AgentDelegationService.class),
                mock(JiraTaskWriteService.class), projects
        );
        SagaPrincipal actor = actor(ApplicationRole.STUDENT);
        UUID artifactId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(ai.artifact(actor, artifactId)).thenReturn(new AgentApiResponses.GeneratedArtifact(
                artifactId, UUID.randomUUID().toString(), "SRS_DOCX", "PROJECT",
                projectId.toString(), "../secret.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        ));

        assertThrows(IntegrationException.class, () -> service.download(actor, artifactId));

        verify(projects).get(actor, projectId);
        verify(ai, never()).artifactContent(any(), any());
    }

    private AgentGatewayService service(AgentAiClient ai, JiraTaskWriteService writes) {
        return new AgentGatewayService(
                ai,
                mock(AgentDelegationService.class),
                writes,
                mock(ProjectDetailService.class)
        );
    }

    private AgentApiResponses.PendingAction action(
            UUID id, String type, Map<String, Object> payload
    ) {
        return new AgentApiResponses.PendingAction(
                id, UUID.randomUUID().toString(), type, "EXECUTING", "summary",
                "saga-agent-stable", "2026-08-14T12:00:00Z", null, payload
        );
    }

    private SagaPrincipal actor(ApplicationRole role) {
        return new SagaPrincipal(
                role.name().toLowerCase() + "-sub",
                role.name().toLowerCase() + "@example.test",
                role.name(), role, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
