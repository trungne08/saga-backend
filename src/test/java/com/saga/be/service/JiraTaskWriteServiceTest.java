package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraCreateField;
import com.saga.be.integration.provider.JiraCreateIssueType;
import com.saga.be.integration.provider.JiraIssueReference;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JiraTaskWriteServiceTest {

    @Test
    void createsRemotelyThenFetchesAndUpsertsCanonicalSnapshot() {
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraIssueUpsertService upserts = mock(JiraIssueUpsertService.class);
        JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        TaskRepository tasks = mock(TaskRepository.class);
        UUID projectId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        Project project = Project.builder().name("Project").build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").jiraProjectId("10000")
                .connectionStatus(IntegrationStatus.ACTIVE).grantedScopes("write:jira-work").build(); board.setId(boardId);
        JiraWriteOperation operation = JiraWriteOperation.builder().project(project).status(JiraWriteOperationStatus.PENDING).build(); operation.setId(operationId);
        Task task = Task.builder().project(project).externalId("101").externalKey("P-1").title("Task").build(); task.setId(UUID.randomUUID());
        JiraTaskCreateRequest request = new JiraTaskCreateRequest("Task", "3", null, null, null, null, null, null);
        SagaPrincipal principal = new SagaPrincipal("sub", "a@b.test", "User", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE);
        when(authorization.requireProjectManager(principal, projectId)).thenReturn(project);
        when(operations.fingerprint(any())).thenReturn("fingerprint");
        when(operations.claim(eq(project), eq(principal), eq(JiraWriteOperationType.TASK_CREATE), any(), eq("fingerprint"))).thenReturn(operation);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
        when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(new JiraCreateIssueType("3", "Task", false, null)));
        when(provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(
                new JiraCreateField("summary", "Summary", true, "string", null, List.of()),
                new JiraCreateField("issuetype", "Issue type", true, "string", null, List.of())
        ));
        when(provider.createIssue(eq("token"), eq("cloud"), any())).thenReturn(new JiraIssueReference("101", "P-1"));
        JiraIssueSnapshot canonical = snapshot();
        when(provider.getIssue("token", "cloud", "101")).thenReturn(canonical);
        when(tasks.findByProjectIdAndExternalId(projectId, "101")).thenReturn(Optional.of(task));

        assertEquals(task.getId(), new JiraTaskWriteService(authorization, boards, credentials, provider, upserts, operations, tasks,
                mock(IdentityMapRepository.class), mock(SprintRepository.class), mock(JiraSprintUpsertService.class)).create(principal, projectId, "key", request).id());

        InOrder ordered = inOrder(authorization, provider, upserts);
        ordered.verify(authorization).requireProjectManager(principal, projectId);
        ordered.verify(provider).createIssue(eq("token"), eq("cloud"), any());
        ordered.verify(provider).getIssue("token", "cloud", "101");
        ordered.verify(upserts).upsert(boardId, canonical);
        verify(operations).markRemoteSucceeded(operationId, "101", "P-1");
        verify(operations).complete(operationId);
    }

    @Test
    void rejectsMetadataDisallowedOptionalFieldBeforeRemoteCreate() {
        ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        JiraBoardRepository boards = mock(JiraBoardRepository.class);
        JiraCredentialService credentials = mock(JiraCredentialService.class);
        JiraProviderClient provider = mock(JiraProviderClient.class);
        JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        UUID projectId = UUID.randomUUID(); Project project = Project.builder().build(); project.setId(projectId);
        JiraBoard board = JiraBoard.builder().project(project).cloudId("cloud").jiraProjectId("10000")
                .connectionStatus(IntegrationStatus.ACTIVE).grantedScopes("write:jira-work").build();
        JiraWriteOperation operation = JiraWriteOperation.builder().status(JiraWriteOperationStatus.PENDING).build(); operation.setId(UUID.randomUUID());
        SagaPrincipal principal = new SagaPrincipal("sub", "a", "u", ApplicationRole.ADMIN, UUID.randomUUID(), AccountStatus.ACTIVE);
        when(authorization.requireProjectManager(principal, projectId)).thenReturn(project);
        when(operations.fingerprint(any())).thenReturn("f"); when(operations.claim(any(), any(), any(), any(), any())).thenReturn(operation);
        when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board)); when(credentials.validAccessToken(board)).thenReturn("token");
        when(provider.getCreateIssueTypes("token", "cloud", "10000")).thenReturn(List.of(new JiraCreateIssueType("3", "Task", false, null)));
        when(provider.getCreateFields("token", "cloud", "10000", "3")).thenReturn(List.of(new JiraCreateField("summary", "Summary", true, "string", null, List.of()), new JiraCreateField("issuetype", "Type", true, "string", null, List.of())));

        assertEquals("JIRA_CREATE_FIELD_NOT_ALLOWED", assertThrows(IntegrationException.class, () -> new JiraTaskWriteService(authorization, boards, credentials, provider,
                mock(JiraIssueUpsertService.class), operations, mock(TaskRepository.class), mock(IdentityMapRepository.class), mock(SprintRepository.class), mock(JiraSprintUpsertService.class)).create(principal, projectId, "key", new JiraTaskCreateRequest("Task", "3", "desc", null, null, null, null, null))).getCode());
        verify(provider, never()).createIssue(any(), any(), any());
    }

    private JiraIssueSnapshot snapshot() { return new JiraIssueSnapshot("101", "P-1", "Task", "Task", "To Do", null, null, null, null, null, null, LocalDateTime.now(), null, null, null, null, null); }
}
