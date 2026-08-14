package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.saga.be.dto.request.InternalAgentToolRequests;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentTaskProposalValidationServiceTest {

    @Test
    void createProposalUsesExactProjectManagerAuthorityAndBusinessFieldsOnly() {
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        AgentTaskProposalValidationService service = new AgentTaskProposalValidationService(
                authorization, tasks
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        InternalAgentToolResponses.ActionValidation result = service.validateCreate(
                actor,
                new InternalAgentToolRequests.TaskCreate(
                        UUID.randomUUID(), projectId, " Fix login ", TaskType.BUG,
                        Priority.HIGH, null, null, List.of("auth"), null, null
                )
        );

        verify(authorization).requireProjectManager(actor, projectId);
        assertEquals("Fix login", result.normalizedPayload().get("title"));
        assertEquals("BUG", result.normalizedPayload().get("type"));
        assertFalse(result.normalizedPayload().containsKey("status"));
        assertFalse(result.normalizedPayload().containsKey("sprintId"));
    }

    @Test
    void sparseUpdateReauthorizesAndRequiresExistingTask() {
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        AgentTaskProposalValidationService service = new AgentTaskProposalValidationService(
                authorization, tasks
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        InternalAgentToolResponses.ActionValidation result = service.validateUpdate(
                actor,
                new InternalAgentToolRequests.TaskUpdate(
                        UUID.randomUUID(), projectId, taskId, null, null, null,
                        Priority.CRITICAL, null, null, null
                )
        );

        verify(authorization).requireProjectManager(actor, projectId);
        verify(tasks).getTask(actor, projectId, taskId);
        assertEquals(3, result.normalizedPayload().size());
        assertEquals("CRITICAL", result.normalizedPayload().get("priority"));
    }

    @Test
    void emptyUpdateFailsAfterCurrentAuthorizationAndTargetCheck() {
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        AgentTaskProposalValidationService service = new AgentTaskProposalValidationService(
                authorization, tasks
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.validateUpdate(
                        actor,
                        new InternalAgentToolRequests.TaskUpdate(
                                UUID.randomUUID(), projectId, taskId, null, null,
                                null, null, null, null, null
                        )
                )
        );

        assertEquals("AGENT_TASK_UPDATE_EMPTY", failure.getCode());
        verify(authorization).requireProjectManager(actor, projectId);
        verify(tasks).getTask(actor, projectId, taskId);
    }

    private SagaPrincipal actor() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
