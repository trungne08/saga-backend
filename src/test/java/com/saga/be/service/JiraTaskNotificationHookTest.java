package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraTaskAssigneeRequest;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraCanonicalTaskReadService;
import com.saga.be.integration.write.JiraTaskSprintFinalizationService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraTaskNotificationHookTest {
    private final ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
    private final JiraBoardRepository boards = mock(JiraBoardRepository.class);
    private final JiraCredentialService credentials = mock(JiraCredentialService.class);
    private final JiraProviderClient provider = mock(JiraProviderClient.class);
    private final JiraIssueUpsertService upserts = mock(JiraIssueUpsertService.class);
    private final JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
    private final JiraCanonicalTaskReadService canonicalReads = mock(JiraCanonicalTaskReadService.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final JiraMutationNotificationProducer notifications = mock(JiraMutationNotificationProducer.class);
    private final UUID projectId = UUID.randomUUID();
    private final Project project = Project.builder().name("Project").build();
    private final JiraBoard board;
    private final Task task;
    private final SagaPrincipal actor = new SagaPrincipal(
            "sub", "actor@test.invalid", "Actor", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE
    );
    private final JiraTaskWriteService service;

    JiraTaskNotificationHookTest() {
        project.setId(projectId);
        board = JiraBoard.builder()
                .project(project)
                .cloudId("cloud")
                .connectionStatus(IntegrationStatus.ACTIVE)
                .grantedScopes("write:jira-work")
                .build();
        board.setId(UUID.randomUUID());
        task = Task.builder()
                .project(project)
                .externalId("100")
                .externalKey("SAGA-1")
                .title("Task")
                .build();
        task.setId(UUID.randomUUID());
        service = new JiraTaskWriteService(
                authorization,
                boards,
                credentials,
                provider,
                upserts,
                operations,
                canonicalReads,
                mock(JiraTaskSprintFinalizationService.class),
                tasks,
                mock(IdentityMapRepository.class),
                mock(SprintRepository.class),
                mock(JiraSprintUpsertService.class),
                notifications
        );
    }

    @BeforeEach
    void setUpCanonicalFlow() {
        when(authorization.requireProjectManager(actor, projectId)).thenReturn(project);
        when(tasks.findByIdAndProjectId(task.getId(), projectId)).thenReturn(Optional.of(task));
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(operations.fingerprint(any())).thenReturn("fingerprint");
        when(provider.getIssue("token", "cloud", "100")).thenReturn(snapshot());
        when(canonicalReads.findResponse(projectId, "100"))
                .thenReturn(Optional.of(com.saga.be.dto.response.TaskReadResponse.from(task)));
    }

    @Test
    void transitionEmitsStatusNotificationOnlyAfterCanonicalCompletion() {
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_TRANSITION);
        when(operations.claim(project, actor, JiraWriteOperationType.TASK_TRANSITION, "key", "fingerprint"))
                .thenReturn(operation);

        service.transition(actor, projectId, task.getId(), "key", "31");

        verify(provider).transitionIssue("token", "cloud", "100", "31");
        verify(canonicalReads).findResponse(projectId, "100");
        verify(operations).complete(operation.getId());
        verify(notifications).taskCompleted(
                operation.getId(), NotificationType.TASK_STATUS_CHANGED, actor
        );
    }

    @Test
    void unassignEmitsAssigneeChangedNotificationAfterCompletion() {
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_ASSIGN);
        when(operations.claim(project, actor, JiraWriteOperationType.TASK_ASSIGN, "key", "fingerprint"))
                .thenReturn(operation);

        service.assign(actor, projectId, task.getId(), "key", new JiraTaskAssigneeRequest(null, true));

        verify(provider).assignIssue("token", "cloud", "100", null);
        verify(operations).complete(operation.getId());
        verify(notifications).taskCompleted(
                operation.getId(), NotificationType.TASK_ASSIGNEE_CHANGED, actor
        );
    }

    @Test
    void deleteEmitsOnlyAfterRemoteDeleteAndLocalTombstoneComplete() {
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_DELETE);
        when(operations.claim(project, actor, JiraWriteOperationType.TASK_DELETE, "key", "fingerprint"))
                .thenReturn(operation);

        service.delete(actor, projectId, task.getId(), "key");

        verify(provider).deleteIssue("token", "cloud", "100");
        verify(tasks).saveAndFlush(task);
        verify(operations).complete(operation.getId());
        verify(notifications).taskCompleted(
                operation.getId(), NotificationType.TASK_DELETED, actor
        );

        operation.setStatus(JiraWriteOperationStatus.COMPLETED);
        service.delete(actor, projectId, task.getId(), "key");
        verify(provider, org.mockito.Mockito.times(1)).deleteIssue("token", "cloud", "100");
        verify(notifications, org.mockito.Mockito.times(1)).taskCompleted(
                operation.getId(), NotificationType.TASK_DELETED, actor
        );
    }

    @Test
    void providerFailureDoesNotEmitAndProducerFailureDoesNotRollbackCompletedWrite() {
        JiraWriteOperation failed = operation(JiraWriteOperationType.TASK_TRANSITION);
        when(operations.claim(project, actor, JiraWriteOperationType.TASK_TRANSITION, "failed-key", "fingerprint"))
                .thenReturn(failed);
        doThrow(IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE"))
                .when(provider).transitionIssue("token", "cloud", "100", "31");

        assertThrows(IntegrationException.class, () -> service.transition(
                actor, projectId, task.getId(), "failed-key", "31"
        ));
        verify(operations, never()).complete(failed.getId());
        verifyNoInteractions(notifications);

        org.mockito.Mockito.reset(provider, notifications);
        when(provider.getIssue("token", "cloud", "100")).thenReturn(snapshot());
        JiraWriteOperation completed = operation(JiraWriteOperationType.TASK_TRANSITION);
        when(operations.claim(project, actor, JiraWriteOperationType.TASK_TRANSITION, "success-key", "fingerprint"))
                .thenReturn(completed);
        doThrow(new IllegalStateException("notification unavailable")).when(notifications)
                .taskCompleted(completed.getId(), NotificationType.TASK_STATUS_CHANGED, actor);

        assertDoesNotThrow(() -> service.transition(actor, projectId, task.getId(), "success-key", "31"));
        verify(operations).complete(completed.getId());
    }

    private JiraWriteOperation operation(JiraWriteOperationType type) {
        JiraWriteOperation operation = JiraWriteOperation.builder()
                .project(project)
                .operationType(type)
                .status(JiraWriteOperationStatus.PENDING)
                .build();
        operation.setId(UUID.randomUUID());
        return operation;
    }

    private JiraIssueSnapshot snapshot() {
        return new JiraIssueSnapshot(
                "100", "SAGA-1", "Task", "Task", "To Do", null,
                null, null, null, null, null, LocalDateTime.now(),
                null, null, null, null, null
        );
    }
}
