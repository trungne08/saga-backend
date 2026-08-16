package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.dto.request.JiraTaskCreateRequest;
import com.saga.be.dto.request.JiraTaskSprintRequest;
import com.saga.be.dto.response.AgentApiResponses;
import com.saga.be.dto.response.TaskReadResponse;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.StudentRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AgentCompositeTaskCreateConfirmTest {

    private static final String CREATE_KEY = "saga-agent-stable";
    private static final String SPRINT_KEY = CREATE_KEY + ":sprint";

    @Test
    void confirmWithoutSprintIdCreatesOnlyAndLeavesBacklog() {
        Fixture fx = fixture();
        TaskReadResponse created = task(fx.taskId, null);
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any(JiraTaskCreateRequest.class)))
                .thenReturn(created);
        pendingThenClaim(fx, createPayload(fx.projectId, null));

        AgentApiResponses.ActionExecution result = fx.service.confirm(fx.actor, fx.actionId);

        assertEquals("COMPLETED", result.status());
        assertNull(result.task().sprint());
        verify(fx.writes).create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any());
        verify(fx.writes, never()).sprint(any(), any(), any(), any(), any());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, true, null);
        verify(fx.ai).claimAction(fx.actor, fx.actionId);
    }

    @Test
    void confirmWithSprintIdComposesCreateThenSprint() {
        Fixture fx = fixture();
        TaskReadResponse created = task(fx.taskId, null);
        TaskReadResponse moved = task(fx.taskId, fx.sprintId);
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(created);
        when(fx.writes.sprint(
                eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any(JiraTaskSprintRequest.class)
        )).thenReturn(moved);
        pendingThenClaim(fx, createPayload(fx.projectId, fx.sprintId));

        AgentApiResponses.ActionExecution result = fx.service.confirm(fx.actor, fx.actionId);

        assertEquals("COMPLETED", result.status());
        assertEquals(fx.sprintId, result.task().sprint().id());
        ArgumentCaptor<JiraTaskSprintRequest> sprint = ArgumentCaptor.forClass(JiraTaskSprintRequest.class);
        verify(fx.writes).sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), sprint.capture());
        assertEquals(fx.sprintId, sprint.getValue().sprintId());
        assertEquals(Boolean.FALSE, sprint.getValue().backlog());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void createPreRemoteFailureDoesNotCallSprint() {
        Fixture fx = fixture();
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenThrow(IntegrationException.conflict("JIRA_WRITE_FAILED", "create failed"));
        pendingThenClaim(fx, createPayload(fx.projectId, fx.sprintId));

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("JIRA_WRITE_FAILED", failure.getCode());
        verify(fx.writes, never()).sprint(any(), any(), any(), any(), any());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, false, "JIRA_WRITE_FAILED");
        verify(fx.ai, never()).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void sprintPreRemoteFailureFailsPendingAndDoesNotEmitCompleted() {
        Fixture fx = fixture();
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(task(fx.taskId, null));
        when(fx.writes.sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any()))
                .thenThrow(IntegrationException.conflict("JIRA_BOARD_NOT_CONFIGURED", "board"));
        pendingThenClaim(fx, createPayload(fx.projectId, fx.sprintId));

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("JIRA_BOARD_NOT_CONFIGURED", failure.getCode());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, false, "JIRA_BOARD_NOT_CONFIGURED");
        verify(fx.ai, never()).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void sprintRemoteSucceededKeepsExecutingAndDoesNotFinalizeFailure() {
        Fixture fx = fixture();
        stubOperation(fx, CREATE_KEY, JiraWriteOperationType.TASK_CREATE, JiraWriteOperationStatus.COMPLETED);
        stubOperation(fx, SPRINT_KEY, JiraWriteOperationType.TASK_SPRINT, JiraWriteOperationStatus.REMOTE_SUCCEEDED);
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(task(fx.taskId, null));
        when(fx.writes.sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any()))
                .thenThrow(IntegrationException.conflict(
                        "JIRA_WRITE_RECOVERY_REQUIRED", "The Jira write is awaiting local recovery"
                ));
        pendingThenClaim(fx, createPayload(fx.projectId, fx.sprintId));

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("JIRA_WRITE_RECOVERY_REQUIRED", failure.getCode());
        verify(fx.ai, never()).finalizeAction(eq(fx.actor), eq(fx.actionId), eq(false), any());
        verify(fx.ai, never()).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void recoveryConfirmReusesSameSprintKeyWithoutSecondClaim() {
        Fixture fx = fixture();
        stubOperation(fx, CREATE_KEY, JiraWriteOperationType.TASK_CREATE, JiraWriteOperationStatus.COMPLETED);
        stubOperation(fx, SPRINT_KEY, JiraWriteOperationType.TASK_SPRINT, JiraWriteOperationStatus.REMOTE_SUCCEEDED);
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(task(fx.taskId, null));
        when(fx.writes.sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any()))
                .thenReturn(task(fx.taskId, fx.sprintId));
        AgentApiResponses.PendingAction executing = action(
                fx, "EXECUTING", createPayload(fx.projectId, fx.sprintId)
        );
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(executing);

        AgentApiResponses.ActionExecution result = fx.service.confirm(fx.actor, fx.actionId);

        assertEquals("COMPLETED", result.status());
        assertEquals(fx.sprintId, result.task().sprint().id());
        verify(fx.ai, never()).claimAction(any(), any());
        verify(fx.writes).create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any());
        verify(fx.writes).sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void executingWithoutCreateOperationIsNotConfirmable() {
        Fixture fx = fixture();
        AgentApiResponses.PendingAction executing = action(
                fx, "EXECUTING", createPayload(fx.projectId, fx.sprintId)
        );
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(executing);

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("PENDING_ACTION_NOT_CONFIRMABLE", failure.getCode());
        verify(fx.writes, never()).create(any(), any(), any(), any());
        verify(fx.writes, never()).sprint(any(), any(), any(), any(), any());
        verify(fx.ai, never()).claimAction(any(), any());
    }

    @Test
    void executingWithInFlightSprintIsNotRecovery() {
        Fixture fx = fixture();
        stubOperation(fx, CREATE_KEY, JiraWriteOperationType.TASK_CREATE, JiraWriteOperationStatus.COMPLETED);
        stubOperation(fx, SPRINT_KEY, JiraWriteOperationType.TASK_SPRINT, JiraWriteOperationStatus.PENDING);
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(
                action(fx, "EXECUTING", createPayload(fx.projectId, fx.sprintId))
        );

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("PENDING_ACTION_NOT_CONFIRMABLE", failure.getCode());
        verify(fx.writes, never()).create(any(), any(), any(), any());
        verify(fx.writes, never()).sprint(any(), any(), any(), any(), any());
    }

    @Test
    void completedSprintReplayFinalizesWithoutSecondClaim() {
        Fixture fx = fixture();
        stubOperation(fx, CREATE_KEY, JiraWriteOperationType.TASK_CREATE, JiraWriteOperationStatus.COMPLETED);
        stubOperation(fx, SPRINT_KEY, JiraWriteOperationType.TASK_SPRINT, JiraWriteOperationStatus.COMPLETED);
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(task(fx.taskId, fx.sprintId));
        when(fx.writes.sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any()))
                .thenReturn(task(fx.taskId, fx.sprintId));
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(
                action(fx, "EXECUTING", createPayload(fx.projectId, fx.sprintId))
        );

        AgentApiResponses.ActionExecution result = fx.service.confirm(fx.actor, fx.actionId);

        assertEquals("COMPLETED", result.status());
        verify(fx.ai, never()).claimAction(any(), any());
        verify(fx.writes).sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any());
        verify(fx.ai).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void concurrentConfirmClaimsOnceAndWritesOnce() throws Exception {
        Fixture fx = fixture();
        AtomicInteger claims = new AtomicInteger();
        AgentApiResponses.PendingAction pending = action(
                fx, "PENDING", createPayload(fx.projectId, fx.sprintId)
        );
        AgentApiResponses.PendingAction executing = action(
                fx, "EXECUTING", createPayload(fx.projectId, fx.sprintId)
        );
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(pending);
        when(fx.ai.claimAction(fx.actor, fx.actionId)).thenAnswer(invocation -> {
            if (!claims.compareAndSet(0, 1)) {
                throw IntegrationException.conflict(
                        "PENDING_ACTION_NOT_CONFIRMABLE", "The pending action cannot be confirmed"
                );
            }
            return executing;
        });
        when(fx.writes.create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any()))
                .thenReturn(task(fx.taskId, null));
        when(fx.writes.sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any()))
                .thenReturn(task(fx.taskId, fx.sprintId));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        pool.submit(() -> {
            try {
                fx.service.confirm(fx.actor, fx.actionId);
                completed.incrementAndGet();
            } catch (IntegrationException exception) {
                rejected.incrementAndGet();
            } finally {
                done.countDown();
            }
        });
        pool.submit(() -> {
            try {
                fx.service.confirm(fx.actor, fx.actionId);
                completed.incrementAndGet();
            } catch (IntegrationException exception) {
                rejected.incrementAndGet();
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(1, claims.get());
        assertEquals(1, completed.get());
        assertEquals(1, rejected.get());
        verify(fx.writes, times(1)).create(eq(fx.actor), eq(fx.projectId), eq(CREATE_KEY), any());
        verify(fx.writes, times(1)).sprint(eq(fx.actor), eq(fx.projectId), eq(fx.taskId), eq(SPRINT_KEY), any());
        verify(fx.ai, times(1)).finalizeAction(fx.actor, fx.actionId, true, null);
    }

    @Test
    void confirmReauthorizesConversationCourseAndRejectsCrossCourseProject() {
        Fixture fx = fixture();
        UUID courseA = UUID.randomUUID();
        when(fx.scopes.courseIdFor(fx.conversationId)).thenReturn(Optional.of(courseA));
        when(fx.scopes.requireAccessibleCourse(fx.actor, courseA)).thenReturn(null);
        org.mockito.Mockito.doThrow(new IntegrationException(
                HttpStatus.FORBIDDEN,
                "AI_AGENT_RESOURCE_OUTSIDE_COURSE_SCOPE",
                "The requested resource does not belong to the active Course"
        )).when(fx.scopes).requireProjectInScope(courseA, fx.projectId);
        pendingThenClaim(fx, createPayload(fx.projectId, fx.sprintId));

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("AI_AGENT_RESOURCE_OUTSIDE_COURSE_SCOPE", failure.getCode());
        verify(fx.writes, never()).create(any(), any(), any(), any());
        verify(fx.writes, never()).sprint(any(), any(), any(), any(), any());
    }

    @Test
    void executingUpdateIsNotOpenedBySprintRecovery() {
        Fixture fx = fixture();
        Map<String, Object> payload = Map.of(
                "projectId", fx.projectId.toString(),
                "taskId", fx.taskId.toString(),
                "title", "Updated"
        );
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(
                new AgentApiResponses.PendingAction(
                        fx.actionId, fx.conversationId.toString(), "TASK_UPDATE", "EXECUTING",
                        "summary", CREATE_KEY, "2026-08-17T00:00:00Z", null, payload
                )
        );

        IntegrationException failure = assertThrows(
                IntegrationException.class, () -> fx.service.confirm(fx.actor, fx.actionId)
        );

        assertEquals("PENDING_ACTION_NOT_CONFIRMABLE", failure.getCode());
        verify(fx.writes, never()).update(any(), any(), any(), any(), any());
        verify(fx.ai, never()).claimAction(any(), any());
    }

    private void pendingThenClaim(Fixture fx, Map<String, Object> payload) {
        when(fx.ai.inspectAction(fx.actor, fx.actionId)).thenReturn(action(fx, "PENDING", payload));
        when(fx.ai.claimAction(fx.actor, fx.actionId)).thenReturn(action(fx, "EXECUTING", payload));
    }

    private void stubOperation(
            Fixture fx,
            String key,
            JiraWriteOperationType type,
            JiraWriteOperationStatus status
    ) {
        when(fx.operations.findByProjectIdAndIdempotencyKey(fx.projectId, key)).thenReturn(Optional.of(
                JiraWriteOperation.builder()
                        .operationType(type)
                        .idempotencyKey(key)
                        .status(status)
                        .requestFingerprint("fp")
                        .actorProfileId(fx.actor.localProfileId())
                        .build()
        ));
    }

    private Map<String, Object> createPayload(UUID projectId, UUID sprintId) {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("projectId", projectId.toString());
        payload.put("title", "Thiết kế Database PostgreSQL");
        payload.put("type", "TASK");
        if (sprintId != null) {
            payload.put("sprintId", sprintId.toString());
        }
        return payload;
    }

    private AgentApiResponses.PendingAction action(Fixture fx, String status, Map<String, Object> payload) {
        return new AgentApiResponses.PendingAction(
                fx.actionId, fx.conversationId.toString(), "TASK_CREATE", status, "summary",
                CREATE_KEY, "2026-08-17T00:00:00Z", null, payload
        );
    }

    private TaskReadResponse task(UUID taskId, UUID sprintId) {
        return new TaskReadResponse(
                taskId,
                UUID.randomUUID(),
                "10026",
                "DEMO-41",
                "Thiết kế Database PostgreSQL",
                TaskType.TASK,
                TaskStatus.TODO,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                sprintId == null ? null : new TaskReadResponse.SprintReference(sprintId, "Sprint 1", "10001"),
                null,
                null,
                null
        );
    }

    private Fixture fixture() {
        AgentAiClient ai = mock(AgentAiClient.class);
        JiraTaskWriteService writes = mock(JiraTaskWriteService.class);
        AgentConversationScopeService scopes = mock(AgentConversationScopeService.class);
        JiraWriteOperationRepository operations = mock(JiraWriteOperationRepository.class);
        when(operations.findByProjectIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(scopes.courseIdFor(any())).thenReturn(Optional.empty());
        AgentGatewayService service = new AgentGatewayService(
                ai,
                mock(AgentDelegationService.class),
                writes,
                mock(ProjectDetailService.class),
                mock(StudentRepository.class),
                mock(LecturerAnalyticsAuthorizationService.class),
                mock(TeamMemberRepository.class),
                scopes,
                new AgentTaskCreateSprintRecoveryGate(operations)
        );
        return new Fixture(service, ai, writes, scopes, operations);
    }

    private record Fixture(
            AgentGatewayService service,
            AgentAiClient ai,
            JiraTaskWriteService writes,
            AgentConversationScopeService scopes,
            JiraWriteOperationRepository operations,
            SagaPrincipal actor,
            UUID actionId,
            UUID projectId,
            UUID taskId,
            UUID sprintId,
            UUID conversationId
    ) {
        Fixture(
                AgentGatewayService service,
                AgentAiClient ai,
                JiraTaskWriteService writes,
                AgentConversationScopeService scopes,
                JiraWriteOperationRepository operations
        ) {
            this(
                    service, ai, writes, scopes, operations,
                    new SagaPrincipal(
                            "student-sub", "leader@example.test", "Leader",
                            ApplicationRole.STUDENT, UUID.randomUUID(), AccountStatus.ACTIVE
                    ),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID()
            );
        }
    }
}
