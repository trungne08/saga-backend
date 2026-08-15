package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraSprintCreateRequest;
import com.saga.be.dto.request.JiraSprintUpdateRequest;
import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.IntegrationStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.project.JiraBoardResolutionService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.provider.JiraSprintSnapshot;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.integration.write.JiraWriteOperationService;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class JiraSprintWriteServiceTest {
    @Test
    void detailReturnsCanonicalSprintDates() {
        Fixture f = new Fixture();
        Sprint sprint = f.sprint("active");
        f.stubSprint(sprint);

        var response = f.service.detail(f.actor, f.projectId, sprint.getId());

        assertEquals(
                Instant.parse("2026-08-01T02:00:00Z"),
                response.startDate()
        );
        assertEquals(
                Instant.parse("2026-08-08T02:00:00Z"),
                response.endDate()
        );
    }

    @Test
    void createsRemotelyUsingResolvedSprintCapableBoard35ThenCanonicalizes() {
        Fixture f = new Fixture();
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_CREATE, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("future", "Sprint");
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_CREATE), eq("key"), eq("f"))).thenReturn(op);
        when(f.boardResolver.resolve(f.board)).thenReturn("35");
        when(f.provider.createSprint(eq("token"), eq("cloud"), eq("35"), eq("Sprint"), any(), any(), any())).thenReturn(snapshot);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenReturn(f.sprint("future"));

        f.service.create(f.actor, f.projectId, "key", new JiraSprintCreateRequest("Sprint", null, null, null));

        InOrder order = inOrder(f.authorization, f.provider, f.upserts);
        order.verify(f.authorization).requireProjectManager(f.actor, f.projectId);
        order.verify(f.provider).createSprint(eq("token"), eq("cloud"), eq("35"), eq("Sprint"), any(), any(), any());
        order.verify(f.provider).getSprint("token", "cloud", "42");
        order.verify(f.upserts).upsert(f.board.getId(), snapshot);
        verify(f.operations).complete(op.getId());
        verify(f.notifications).sprintCompleted(
                op.getId(), NotificationType.SPRINT_CREATED, f.actor, "Sprint"
        );
    }

    @Test
    void partialUpdateSendsOnlyPresentFieldsThenCanonicalizes() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_UPDATE, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("future", "Renamed");
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_UPDATE), eq("key"), eq("f"))).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenReturn(existing);

        f.service.update(f.actor, f.projectId, existing.getId(), "key", new JiraSprintUpdateRequest("Renamed", null, null, null));

        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        InOrder order = inOrder(f.provider, f.upserts);
        order.verify(f.provider).updateSprint(eq("token"), eq("cloud"), eq("42"), changes.capture());
        order.verify(f.provider).getSprint("token", "cloud", "42");
        order.verify(f.upserts).upsert(f.board.getId(), snapshot);
        assertEquals(Map.of("name", "Renamed"), changes.getValue());
        verify(f.operations).complete(op.getId());
        verify(f.notifications).sprintCompleted(
                op.getId(), NotificationType.SPRINT_UPDATED, f.actor, existing.getName()
        );
    }

    @Test
    void startsOnlyDatedFutureSprintAndCanonicalizesActiveState() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("active", "Sprint");
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_START), eq("key"), eq("f"))).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenAnswer(invocation -> { existing.setState("active"); return existing; });

        assertEquals("active", f.service.start(f.actor, f.projectId, existing.getId(), "key").state());
        InOrder order = inOrder(f.provider, f.upserts);
        order.verify(f.provider).updateSprint("token", "cloud", "42", Map.of("state", "active"));
        order.verify(f.provider).getSprint("token", "cloud", "42");
        order.verify(f.upserts).upsert(f.board.getId(), snapshot);
        verify(f.notifications).sprintCompleted(
                op.getId(), NotificationType.SPRINT_STARTED, f.actor, existing.getName()
        );
    }

    @Test
    void startRemainsRemoteSucceededUntilCanonicalStateIsActive() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("future", "Sprint");
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_START), eq("key"), eq("f")))
                .thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenReturn(existing);

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", assertThrows(
                IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")
        ).getCode());

        verify(f.operations, never()).complete(op.getId());
        verifyNoInteractions(f.notifications);
    }

    @Test
    void rejectsStartForNonFutureSprintWithoutProviderCall() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("active"); f.stubSprint(existing);
        assertEquals("JIRA_SPRINT_STATE_INVALID", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verifyNoInteractions(f.provider);
    }

    @Test
    void closesOnlyActiveSprintAndCanonicalizesClosedState() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("active"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_CLOSE, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("closed", "Sprint");
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_CLOSE), eq("key"), eq("f"))).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenAnswer(invocation -> { existing.setState("closed"); return existing; });

        assertEquals("closed", f.service.close(f.actor, f.projectId, existing.getId(), "key").state());
        verify(f.provider).updateSprint("token", "cloud", "42", Map.of("state", "closed"));
        verify(f.operations).complete(op.getId());
        verify(f.notifications).sprintCompleted(
                op.getId(), NotificationType.SPRINT_CLOSED, f.actor, existing.getName()
        );
    }

    @Test
    void completedLifecycleOperationDoesNotMutateRemoteAgain() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), eq("key"), any()))
                .thenReturn(f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.COMPLETED));
        assertEquals(existing.getId(), f.service.start(f.actor, f.projectId, existing.getId(), "key").id());
        verifyNoInteractions(f.provider);
        verifyNoInteractions(f.notifications);
    }

    @Test
    void deleteRunsRemoteFirstThenClearsTaskAndTombstonesSprint() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        Task task = Task.builder().project(f.project).sprint(existing).build();
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_DELETE, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_DELETE), eq("key"), eq("f"))).thenReturn(op);
        when(f.tasks.findByProjectId(f.projectId)).thenReturn(List.of(task));
        when(f.sprints.saveAndFlush(existing)).thenReturn(existing);

        f.service.delete(f.actor, f.projectId, existing.getId(), "key");

        InOrder order = inOrder(f.provider, f.tasks, f.sprints);
        order.verify(f.provider).deleteSprint("token", "cloud", "42");
        order.verify(f.tasks).flush();
        order.verify(f.sprints).saveAndFlush(existing);
        assertNull(task.getSprint());
        assertNotNull(existing.getDeletedAt());
        verify(f.operations).complete(op.getId());
        verify(f.notifications).sprintCompleted(
                op.getId(), NotificationType.SPRINT_DELETED, f.actor, existing.getName()
        );
    }

    @Test
    void completedDeleteReplayDoesNotDeleteOrNotifyAgain() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_DELETE, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(eq(f.project), eq(f.actor), eq(JiraWriteOperationType.SPRINT_DELETE), eq("key"), eq("f")))
                .thenReturn(op);
        when(f.tasks.findByProjectId(f.projectId)).thenReturn(List.of());

        f.service.delete(f.actor, f.projectId, existing.getId(), "key");
        op.setStatus(JiraWriteOperationStatus.COMPLETED);
        f.service.delete(f.actor, f.projectId, existing.getId(), "key");

        verify(f.provider, times(1)).deleteSprint("token", "cloud", "42");
        verify(f.notifications, times(1)).sprintCompleted(
                op.getId(), NotificationType.SPRINT_DELETED, f.actor, existing.getName()
        );
    }

    @Test
    void remoteDeleteFailureDoesNotTouchLocalSprint() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_DELETE, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_DELETE), eq("key"), any())).thenReturn(op);
        org.mockito.Mockito.doThrow(IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE"))
                .when(f.provider).deleteSprint("token", "cloud", "42");

        assertThrows(IntegrationException.class, () -> f.service.delete(f.actor, f.projectId, existing.getId(), "key"));
        assertNull(existing.getDeletedAt());
        verify(f.tasks, never()).flush();
        verify(f.sprints, never()).saveAndFlush(existing);
        verifyNoInteractions(f.notifications);
    }

    @Test
    void missingScopeStopsBeforeProviderMutation() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        f.board.setGrantedScopes("read:sprint:jira-software");
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any()))
                .thenReturn(f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING));
        assertEquals("JIRA_SCOPE_INSUFFICIENT", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verifyNoInteractions(f.provider);
        verify(f.credentials, never()).validAccessToken(f.board);
        verify(f.operations).failed(org.mockito.ArgumentMatchers.any(), eq("JIRA_SCOPE_INSUFFICIENT"));
    }

    @Test
    void providerStart401IsMappedAndMarkedAsKnownRemoteFailure() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        org.mockito.Mockito.doThrow(IntegrationException.conflict("JIRA_ACCESS_REVOKED", "safe"))
                .when(f.provider).updateSprint("token", "cloud", "42", Map.of("state", "active"));

        assertEquals("JIRA_ACCESS_REVOKED", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verify(f.operations).failed(op.getId(), "JIRA_ACCESS_REVOKED");
        verifyNoInteractions(f.upserts);
    }

    @Test
    void providerStart403IsMappedAndMarkedAsKnownRemoteFailure() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        org.mockito.Mockito.doThrow(new IntegrationException(HttpStatus.FORBIDDEN, "JIRA_ACCESS_FORBIDDEN", "safe"))
                .when(f.provider).updateSprint("token", "cloud", "42", Map.of("state", "active"));

        assertEquals("JIRA_ACCESS_FORBIDDEN", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verify(f.operations).failed(op.getId(), "JIRA_ACCESS_FORBIDDEN");
        verifyNoInteractions(f.upserts);
    }

    @Test
    void credentialRefreshFailureStopsStartBeforeRemoteMutation() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        when(f.credentials.validAccessToken(f.board)).thenThrow(IntegrationException.conflict(
                "JIRA_REFRESH_TOKEN_MISSING", "safe"));

        assertEquals("JIRA_REFRESH_TOKEN_MISSING", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verify(f.operations).failed(op.getId(), "JIRA_REFRESH_TOKEN_MISSING");
        verifyNoInteractions(f.provider);
    }

    @Test
    void providerDomainFailuresDoNotCanonicalizeOrModifyLocalSprint() {
        List<IntegrationException> failures = List.of(
                IntegrationException.invalid("JIRA_REQUEST_REJECTED", "Rejected"),
                new IntegrationException(HttpStatus.FORBIDDEN, "JIRA_ACCESS_FORBIDDEN", "Forbidden"),
                new IntegrationException(HttpStatus.NOT_FOUND, "JIRA_RESOURCE_NOT_FOUND", "Missing"),
                new IntegrationException(HttpStatus.TOO_MANY_REQUESTS, "JIRA_RATE_LIMITED", "Rate limited"),
                IntegrationException.unavailable("JIRA_PROVIDER_UNAVAILABLE")
        );
        for (IntegrationException failure : failures) {
            Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
            JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_UPDATE, JiraWriteOperationStatus.PENDING);
            when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_UPDATE), any(), any())).thenReturn(op);
            org.mockito.Mockito.doThrow(failure).when(f.provider)
                    .updateSprint(eq("token"), eq("cloud"), eq("42"), any());

            IntegrationException thrown = assertThrows(IntegrationException.class, () -> f.service.update(
                    f.actor, f.projectId, existing.getId(), "key", new JiraSprintUpdateRequest("Renamed", null, null, null)));

            assertEquals(failure.getCode(), thrown.getCode());
            assertEquals("Sprint", existing.getName());
            verifyNoInteractions(f.upserts);
            verify(f.operations).failed(op.getId(), failure.getCode());
        }
    }

    @Test
    void networkOutcomeBecomesUnknownWithoutCanonicalizingLocalSprint() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        org.mockito.Mockito.doThrow(new RuntimeException("timeout")).when(f.provider)
                .updateSprint("token", "cloud", "42", Map.of("state", "active"));

        assertThrows(RuntimeException.class, () -> f.service.start(f.actor, f.projectId, existing.getId(), "key"));
        assertEquals("future", existing.getState());
        verify(f.operations).unknown(op.getId(), "JIRA_WRITE_OUTCOME_UNKNOWN");
        verifyNoInteractions(f.upserts);
    }

    @Test
    void canonicalFailureAfterRemoteSuccessDoesNotCompleteOperation() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42"))
                .thenThrow(IntegrationException.unavailable("JIRA_RESPONSE_INVALID"));

        assertThrows(IntegrationException.class, () -> f.service.start(f.actor, f.projectId, existing.getId(), "key"));
        verify(f.operations).markRemoteSucceeded(op.getId(), "42", null);
        verify(f.operations, never()).complete(op.getId());
        assertEquals("future", existing.getState());
    }

    @Test
    void canonicalGet401AfterStartKeepsRemoteSuccessAndDoesNotReplayMutation() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.PENDING);
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42"))
                .thenThrow(IntegrationException.conflict("JIRA_ACCESS_REVOKED", "safe"));

        assertEquals("JIRA_ACCESS_REVOKED", assertThrows(IntegrationException.class,
                () -> f.service.start(f.actor, f.projectId, existing.getId(), "key")).getCode());
        verify(f.operations).markRemoteSucceeded(op.getId(), "42", null);
        verify(f.operations, never()).complete(op.getId());
        verify(f.provider, times(1)).updateSprint("token", "cloud", "42", Map.of("state", "active"));
        assertEquals("future", existing.getState());
    }

    @Test
    void retryOfRemoteSucceededStartUsesCanonicalRepairOnly() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("future"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.REMOTE_SUCCEEDED);
        op.setRemoteResourceId("42");
        JiraSprintSnapshot snapshot = f.snapshot("active", "Sprint");
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_START), any(), any())).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenAnswer(invocation -> {
            existing.setState("active"); return existing;
        });

        assertEquals("active", f.service.start(f.actor, f.projectId, existing.getId(), "key").state());
        verify(f.provider, never()).updateSprint(any(), any(), any(), any());
        verify(f.operations).complete(op.getId());
    }

    @Test
    void localUpsertFailureAfterRemoteSuccessLeavesOperationRecoverable() {
        Fixture f = new Fixture(); Sprint existing = f.sprint("active"); f.stubSprint(existing);
        JiraWriteOperation op = f.operation(JiraWriteOperationType.SPRINT_CLOSE, JiraWriteOperationStatus.PENDING);
        JiraSprintSnapshot snapshot = f.snapshot("closed", "Sprint");
        when(f.operations.claim(any(), any(), eq(JiraWriteOperationType.SPRINT_CLOSE), any(), any())).thenReturn(op);
        when(f.provider.getSprint("token", "cloud", "42")).thenReturn(snapshot);
        when(f.upserts.upsert(f.board.getId(), snapshot)).thenThrow(new RuntimeException("local failure"));

        assertThrows(RuntimeException.class, () -> f.service.close(f.actor, f.projectId, existing.getId(), "key"));
        verify(f.operations).markRemoteSucceeded(op.getId(), "42", null);
        verify(f.operations, never()).complete(op.getId());
        assertEquals("active", existing.getState());
    }

    private static final class Fixture {
        final ProjectIntegrationAuthorizationService authorization = mock(ProjectIntegrationAuthorizationService.class);
        final JiraBoardRepository boards = mock(JiraBoardRepository.class);
        final JiraCredentialService credentials = mock(JiraCredentialService.class);
        final JiraBoardResolutionService boardResolver = mock(JiraBoardResolutionService.class);
        final JiraProviderClient provider = mock(JiraProviderClient.class);
        final JiraSprintUpsertService upserts = mock(JiraSprintUpsertService.class);
        final JiraWriteOperationService operations = mock(JiraWriteOperationService.class);
        final SprintRepository sprints = mock(SprintRepository.class);
        final TaskRepository tasks = mock(TaskRepository.class);
        final JiraMutationNotificationProducer notifications = mock(JiraMutationNotificationProducer.class);
        final UUID projectId = UUID.randomUUID();
        final Project project = Project.builder().build();
        final JiraBoard board;
        final SagaPrincipal actor = new SagaPrincipal("sub", "a@test", "A", ApplicationRole.ADMIN,
                UUID.randomUUID(), AccountStatus.ACTIVE);
        final JiraSprintWriteService service;

        Fixture() {
            project.setId(projectId);
            board = JiraBoard.builder().project(project).cloudId("cloud").jiraBoardId("99")
                    .connectionStatus(IntegrationStatus.ACTIVE)
                    .grantedScopes("write:sprint:jira-software read:sprint:jira-software delete:sprint:jira-software").build();
            board.setId(UUID.randomUUID());
            when(authorization.requireProjectManager(actor, projectId)).thenReturn(project);
            when(operations.fingerprint(any())).thenReturn("f");
            when(boards.findByProjectId(projectId)).thenReturn(Optional.of(board));
            when(credentials.validAccessToken(board)).thenReturn("token");
            when(boardResolver.resolve(board)).thenReturn("99");
            service = new JiraSprintWriteService(
                    authorization,
                    boards,
                    credentials,
                    boardResolver,
                    provider,
                    upserts,
                    operations,
                    sprints,
                    tasks,
                    notifications
            );
        }

        Sprint sprint(String state) {
            Sprint sprint = Sprint.builder().board(board).externalSprintId("42").name("Sprint").state(state)
                    .startDate(LocalDateTime.parse("2026-08-01T02:00:00"))
                    .endDate(LocalDateTime.parse("2026-08-08T02:00:00")).build();
            sprint.setId(UUID.randomUUID()); return sprint;
        }

        void stubSprint(Sprint sprint) {
            when(sprints.findByIdAndBoardProjectIdAndDeletedAtIsNull(sprint.getId(), projectId)).thenReturn(Optional.of(sprint));
            when(sprints.findByIdAndBoardProjectId(sprint.getId(), projectId)).thenReturn(Optional.of(sprint));
        }

        JiraWriteOperation operation(JiraWriteOperationType type, JiraWriteOperationStatus status) {
            JiraWriteOperation op = JiraWriteOperation.builder().project(project).operationType(type).status(status).build();
            op.setId(UUID.randomUUID()); return op;
        }

        JiraSprintSnapshot snapshot(String state, String name) {
            return new JiraSprintSnapshot("42", name, state, null, null, null, null, "99");
        }
    }
}
