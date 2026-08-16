package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.InternalAgentToolRequests;
import com.saga.be.dto.response.InternalAgentToolResponses;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.SprintRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Optional;
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
                authorization, tasks, mock(com.saga.be.repository.SprintRepository.class)
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        InternalAgentToolResponses.ActionValidation result = service.validateCreate(
                actor,
                new InternalAgentToolRequests.TaskCreate(
                        UUID.randomUUID(), projectId, " Fix login ", TaskType.BUG,
                        Priority.HIGH, null, null, List.of("auth"), null, null, null
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
                authorization, tasks, mock(com.saga.be.repository.SprintRepository.class)
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
                authorization, tasks, mock(com.saga.be.repository.SprintRepository.class)
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

    @Test
    void createProposalWithSprintIdUsesCanonicalSprintName() {
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        ProjectTaskReadService tasks = mock(ProjectTaskReadService.class);
        SprintRepository sprints = mock(SprintRepository.class);
        AgentTaskProposalValidationService service = new AgentTaskProposalValidationService(
                authorization, tasks, sprints
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = Sprint.builder().name("Sprint 1").build();
        when(sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintId, projectId))
                .thenReturn(Optional.of(sprint));

        InternalAgentToolResponses.ActionValidation result = service.validateCreate(
                actor,
                new InternalAgentToolRequests.TaskCreate(
                        UUID.randomUUID(), projectId, "Thiết kế Database PostgreSQL", TaskType.TASK,
                        null, null, null, null, null, null, sprintId
                )
        );

        verify(authorization).requireProjectManager(actor, projectId);
        assertEquals(sprintId.toString(), result.normalizedPayload().get("sprintId"));
        assertEquals(
                "Create Task 'Thiết kế Database PostgreSQL' as TASK, sprint 'Sprint 1'",
                result.summary()
        );
    }

    @Test
    void createProposalRejectsSprintFromAnotherProject() {
        ProjectIntegrationAuthorizationService authorization = mock(
                ProjectIntegrationAuthorizationService.class
        );
        SprintRepository sprints = mock(SprintRepository.class);
        AgentTaskProposalValidationService service = new AgentTaskProposalValidationService(
                authorization, mock(ProjectTaskReadService.class), sprints
        );
        SagaPrincipal actor = actor();
        UUID projectId = UUID.randomUUID();
        UUID sprintId = UUID.randomUUID();
        when(sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprintId, projectId))
                .thenReturn(Optional.empty());

        IntegrationException failure = assertThrows(
                IntegrationException.class,
                () -> service.validateCreate(
                        actor,
                        new InternalAgentToolRequests.TaskCreate(
                                UUID.randomUUID(), projectId, "Task", TaskType.TASK,
                                null, null, null, null, null, null, sprintId
                        )
                )
        );

        assertEquals("JIRA_RESOURCE_NOT_FOUND", failure.getCode());
        assertTrue(failure.getStatus().is4xxClientError());
        verify(authorization).requireProjectManager(actor, projectId);
    }

    private SagaPrincipal actor() {
        return new SagaPrincipal(
                "student-sub", "student@example.test", "Student",
                ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
        );
    }
}
