package com.saga.be.integration.write;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraSprintSnapshot;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JiraWriteRecoveryServiceTest {
    @Test
    void remoteSucceededTaskUsesOnlyCanonicalGetThenCompletes() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraIssueUpsertService issueUpserts = mock(JiraIssueUpsertService.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").build(); board.setId(UUID.randomUUID());
        JiraWriteOperation operation = JiraWriteOperation.builder().project(project)
                .operationType(JiraWriteOperationType.TASK_UPDATE).status(JiraWriteOperationStatus.REMOTE_SUCCEEDED)
                .remoteResourceId("101").remoteResourceKey("P-1").build(); operation.setId(UUID.randomUUID());
        JiraIssueSnapshot snapshot = new JiraIssueSnapshot("101", "P-1", "Task", "Task", "To Do", null,
                null, null, null, null, null, LocalDateTime.now(), null, null, null, null, null);
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getIssue("token", "cloud", "101")).thenReturn(snapshot);

        new JiraWriteRecoveryService(operations, boards, credentials, provider, issueUpserts,
                mock(JiraSprintUpsertService.class), mock(TaskRepository.class), mock(SprintRepository.class), operationService)
                .recoverRemoteSuccesses();

        verify(issueUpserts).upsert(board.getId(), snapshot);
        verify(operationService).complete(operation.getId());
        verify(provider, never()).createIssue(any(), any(), any());
        verify(provider, never()).updateIssue(any(), any(), any(), any());
        verify(provider, never()).deleteIssue(any(), any(), any());
    }

    @Test
    void remoteSucceededTaskDeleteFinishesLocalTombstoneWithoutRemoteDelete() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        TaskRepository tasks = mock(TaskRepository.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).build();
        Task task = Task.builder().project(project).externalId("101").build();
        JiraWriteOperation operation = operation(project, JiraWriteOperationType.TASK_DELETE, "101");
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(tasks.findByProjectIdAndExternalId(project.getId(), "101")).thenReturn(Optional.of(task));

        recovery(operations, boards, mock(JiraCredentialService.class), provider,
                mock(JiraIssueUpsertService.class), mock(JiraSprintUpsertService.class), tasks,
                mock(SprintRepository.class), operationService).recoverRemoteSuccesses();

        assertNotNull(task.getDeletedAt());
        verify(tasks).saveAndFlush(task);
        verify(operationService).complete(operation.getId());
        verify(provider, never()).deleteIssue(any(), any(), any());
    }

    @Test
    void remoteSucceededSprintMutationUsesCanonicalGetThenCompletes() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraSprintUpsertService upserts = mock(JiraSprintUpsertService.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").build(); board.setId(UUID.randomUUID());
        JiraWriteOperation operation = operation(project, JiraWriteOperationType.SPRINT_START, "42");
        JiraSprintSnapshot snapshot = new JiraSprintSnapshot("42", "Sprint", "active", null, null, null, null, "99");
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);

        recovery(operations, boards, credentials, provider, mock(JiraIssueUpsertService.class), upserts,
                mock(TaskRepository.class), mock(SprintRepository.class), operationService).recoverRemoteSuccesses();

        verify(upserts).upsert(board.getId(), snapshot);
        verify(operationService).complete(operation.getId());
        verify(provider, never()).updateSprint(any(), any(), any(), any());
        verify(provider, never()).deleteSprint(any(), any(), any());
    }

    @Test
    void remoteSucceededSprintDeleteClearsTaskReferenceAndTombstonesWithoutRemoteCall() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        TaskRepository tasks = mock(TaskRepository.class);
        SprintRepository sprints = mock(SprintRepository.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).build();
        Sprint sprint = Sprint.builder().board(board).externalSprintId("42").build(); sprint.setId(UUID.randomUUID());
        Task task = Task.builder().project(project).sprint(sprint).build();
        JiraWriteOperation operation = operation(project, JiraWriteOperationType.SPRINT_DELETE, "42");
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(sprints.findByBoardProjectIdAndDeletedAtIsNull(project.getId())).thenReturn(List.of(sprint));
        when(tasks.findByProjectId(project.getId())).thenReturn(List.of(task));

        recovery(operations, boards, mock(JiraCredentialService.class), provider,
                mock(JiraIssueUpsertService.class), mock(JiraSprintUpsertService.class), tasks, sprints,
                operationService).recoverRemoteSuccesses();

        assertNull(task.getSprint());
        assertNotNull(sprint.getDeletedAt());
        verify(tasks).flush();
        verify(sprints).saveAndFlush(sprint);
        verify(operationService).complete(operation.getId());
        verifyNoInteractions(provider);
    }

    @Test
    void canonicalFailureKeepsRecoveryDataAndDoesNotCompleteOrMutate() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").build();
        JiraWriteOperation operation = operation(project, JiraWriteOperationType.TASK_UPDATE, "101");
        operation.setRemoteResourceKey("P-1");
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getIssue("token", "cloud", "101")).thenThrow(IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE"));

        assertThrows(IntegrationException.class, () -> recovery(operations, boards, credentials, provider,
                mock(JiraIssueUpsertService.class), mock(JiraSprintUpsertService.class), mock(TaskRepository.class),
                mock(SprintRepository.class), operationService).recoverRemoteSuccesses());

        assertEquals("101", operation.getRemoteResourceId());
        assertEquals("P-1", operation.getRemoteResourceKey());
        verify(operationService, never()).complete(operation.getId());
        verify(provider, never()).updateIssue(any(), any(), any(), any());
    }

    @Test
    void remoteSucceededStartWithCanonical401IsRetainedForCanonicalRepairWithoutReplay() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraWriteOperationService operationService = mock(JiraWriteOperationService.class);
        Project project = Project.builder().build(); project.setId(UUID.randomUUID());
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").build(); board.setId(UUID.randomUUID());
        JiraWriteOperation operation = operation(project, JiraWriteOperationType.SPRINT_START, "42");
        when(operations.findByStatusIn(any())).thenReturn(List.of(operation));
        when(boards.findByProjectId(project.getId())).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getSprint("token", "cloud", "42"))
                .thenThrow(IntegrationException.conflict("JIRA_ACCESS_REVOKED", "safe"));

        assertThrows(IntegrationException.class, () -> recovery(operations, boards, credentials, provider,
                mock(JiraIssueUpsertService.class), mock(JiraSprintUpsertService.class), mock(TaskRepository.class),
                mock(SprintRepository.class), operationService).recoverRemoteSuccesses());

        assertEquals(JiraWriteOperationStatus.REMOTE_SUCCEEDED, operation.getStatus());
        verify(operationService, never()).complete(operation.getId());
        verify(provider, never()).updateSprint(any(), any(), any(), any());
    }

    @Test
    void pendingAndUnknownAreNotSelectedForBlindRecovery() {
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        when(operations.findByStatusIn(eq(List.of(JiraWriteOperationStatus.REMOTE_SUCCEEDED)))).thenReturn(List.of());
        recovery(operations, mock(JiraBoardRepository.class), mock(JiraCredentialService.class), provider,
                mock(JiraIssueUpsertService.class), mock(JiraSprintUpsertService.class), mock(TaskRepository.class),
                mock(SprintRepository.class), mock(JiraWriteOperationService.class)).recoverRemoteSuccesses();
        verifyNoInteractions(provider);
    }

    private JiraWriteOperation operation(Project project, JiraWriteOperationType type, String remoteId) {
        JiraWriteOperation operation = JiraWriteOperation.builder().project(project).operationType(type)
                .status(JiraWriteOperationStatus.REMOTE_SUCCEEDED).remoteResourceId(remoteId).build();
        operation.setId(UUID.randomUUID()); return operation;
    }

    private JiraWriteRecoveryService recovery(JiraWriteOperationRepository operations, JiraBoardRepository boards,
            JiraCredentialService credentials, JiraProviderClient provider, JiraIssueUpsertService issueUpserts,
            JiraSprintUpsertService sprintUpserts, TaskRepository tasks, SprintRepository sprints,
            JiraWriteOperationService operationService) {
        return new JiraWriteRecoveryService(operations, boards, credentials, provider, issueUpserts, sprintUpserts,
                tasks, sprints, operationService);
    }
}
