package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Project;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class JiraMutationNotificationProducerTest {
    private final JiraWriteOperationRepository operations = Mockito.mock(JiraWriteOperationRepository.class);
    private final TaskRepository tasks = Mockito.mock(TaskRepository.class);
    private final TeamRepository teams = Mockito.mock(TeamRepository.class);
    private final TeamMemberRepository members = Mockito.mock(TeamMemberRepository.class);
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final JiraMutationNotificationProducer producer = new JiraMutationNotificationProducer(
            operations, tasks, teams, members, notifications
    );
    private final UUID projectId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    private final Project project = new Project();

    @BeforeEach
    void setUpProject() {
        project.setId(projectId);
    }

    @Test
    void completedAssignedTaskNotifiesOnlyCanonicalAssignee() {
        UUID assigneeId = UUID.randomUUID();
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_UPDATE, JiraWriteOperationStatus.COMPLETED);
        Task task = task(assigneeId);
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(tasks.findByProjectIdAndExternalId(projectId, "100")).thenReturn(Optional.of(task));

        producer.taskCompleted(operationId, NotificationType.TASK_UPDATED, actor(ApplicationRole.ADMIN, operation.getActorProfileId()));

        verify(notifications).createOnceForEvent(
                assigneeId,
                ApplicationRole.STUDENT,
                NotificationType.TASK_UPDATED,
                "Task đã được cập nhật",
                "Task SAGA-1: Canonical title vừa được cập nhật.",
                "jira-write:" + operationId + ":TASK_UPDATED"
        );
        verifyNoTeamResolution();
    }

    @Test
    void unassignedTaskUsesOwningTeamAndExcludesOnlyStudentActor() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_ASSIGN, JiraWriteOperationStatus.COMPLETED);
        operation.setActorProfileId(actorId);
        Task task = task(null);
        Team team = team();
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(tasks.findByProjectIdAndExternalId(projectId, "100")).thenReturn(Optional.of(task));
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(members.findDistinctStudentIdsByTeamId(team.getId()))
                .thenReturn(List.of(actorId, recipientId, recipientId));

        producer.taskCompleted(
                operationId,
                NotificationType.TASK_ASSIGNEE_CHANGED,
                actor(ApplicationRole.STUDENT, actorId)
        );

        verify(notifications, never()).createOnceForEvent(
                eq(actorId), any(), any(), any(), any(), any());
        verify(notifications, times(1)).createOnceForEvent(
                eq(recipientId),
                eq(ApplicationRole.STUDENT),
                eq(NotificationType.TASK_ASSIGNEE_CHANGED),
                any(), any(),
                eq("jira-write:" + operationId + ":TASK_ASSIGNEE_CHANGED")
        );
    }

    @Test
    void lecturerOrAdminActorDoesNotExcludeSameUuidFromStudentRecipientSpace() {
        UUID recipientId = UUID.randomUUID();
        JiraWriteOperation operation = operation(JiraWriteOperationType.TASK_CREATE, JiraWriteOperationStatus.COMPLETED);
        operation.setActorProfileId(recipientId);
        Task task = task(recipientId);
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(tasks.findByProjectIdAndExternalId(projectId, "100")).thenReturn(Optional.of(task));

        producer.taskCompleted(
                operationId,
                NotificationType.TASK_CREATED,
                actor(ApplicationRole.ADMIN, recipientId)
        );

        verify(notifications).createOnceForEvent(
                eq(recipientId), eq(ApplicationRole.STUDENT), eq(NotificationType.TASK_CREATED),
                any(), any(), eq("jira-write:" + operationId + ":TASK_CREATED")
        );
    }

    @Test
    void sprintUsesOwningTeamAndExcludesStudentActor() {
        UUID actorId = UUID.randomUUID();
        UUID teammateId = UUID.randomUUID();
        JiraWriteOperation operation = operation(JiraWriteOperationType.SPRINT_START, JiraWriteOperationStatus.COMPLETED);
        operation.setActorProfileId(actorId);
        Team team = team();
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(teams.findByProjectId(projectId)).thenReturn(Optional.of(team));
        when(members.findDistinctStudentIdsByTeamId(team.getId())).thenReturn(List.of(actorId, teammateId));

        producer.sprintCompleted(
                operationId,
                NotificationType.SPRINT_STARTED,
                actor(ApplicationRole.STUDENT, actorId),
                "Sprint 5"
        );

        verify(notifications, never()).createOnceForEvent(eq(actorId), any(), any(), any(), any(), any());
        verify(notifications).createOnceForEvent(
                teammateId,
                ApplicationRole.STUDENT,
                NotificationType.SPRINT_STARTED,
                "Sprint đã bắt đầu",
                "Sprint 5 vừa bắt đầu.",
                "jira-write:" + operationId + ":SPRINT_STARTED"
        );
    }

    @Test
    void remoteSucceededOrMismatchedTypeCannotProduceSuccessNotification() {
        JiraWriteOperation remoteSucceeded = operation(
                JiraWriteOperationType.TASK_UPDATE,
                JiraWriteOperationStatus.REMOTE_SUCCEEDED
        );
        when(operations.findById(operationId)).thenReturn(Optional.of(remoteSucceeded));
        assertThrows(IllegalStateException.class, () -> producer.taskCompleted(
                operationId, NotificationType.TASK_UPDATED, actor(ApplicationRole.ADMIN, remoteSucceeded.getActorProfileId())
        ));

        JiraWriteOperation completed = operation(JiraWriteOperationType.TASK_UPDATE, JiraWriteOperationStatus.COMPLETED);
        when(operations.findById(operationId)).thenReturn(Optional.of(completed));
        assertThrows(IllegalArgumentException.class, () -> producer.taskCompleted(
                operationId, NotificationType.TASK_CREATED, actor(ApplicationRole.ADMIN, completed.getActorProfileId())
        ));
        verify(notifications, never()).createOnceForEvent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deadlineEventIdentityIncludesCanonicalDueRevisionAndUsesAssignee() {
        UUID assigneeId = UUID.randomUUID();
        Task task = task(assigneeId);
        task.setDueDate(LocalDateTime.of(2026, 8, 12, 0, 0));

        producer.deadline(task, NotificationType.TASK_DUE_TOMORROW);

        verify(notifications).createOnceForEvent(
                assigneeId,
                ApplicationRole.STUDENT,
                NotificationType.TASK_DUE_TOMORROW,
                "Task đến hạn vào ngày mai",
                "Task SAGA-1: Canonical title đến hạn vào ngày mai.",
                "task:" + task.getId() + ":due:2026-08-12:type:TASK_DUE_TOMORROW"
        );
        verifyNoTeamResolution();
    }

    private JiraWriteOperation operation(JiraWriteOperationType type, JiraWriteOperationStatus status) {
        JiraWriteOperation operation = JiraWriteOperation.builder()
                .project(project)
                .operationType(type)
                .status(status)
                .remoteResourceId("100")
                .actorProfileId(UUID.randomUUID())
                .build();
        operation.setId(operationId);
        return operation;
    }

    private Task task(UUID assigneeId) {
        Task task = Task.builder()
                .project(project)
                .externalId("100")
                .externalKey("SAGA-1")
                .title("Canonical title")
                .build();
        task.setId(UUID.randomUUID());
        if (assigneeId != null) {
            Student assignee = new Student();
            assignee.setId(assigneeId);
            task.setAssignee(assignee);
        }
        return task;
    }

    private Team team() {
        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setProject(project);
        return team;
    }

    private SagaPrincipal actor(ApplicationRole role, UUID id) {
        return new SagaPrincipal("sub", "safe@test.invalid", "Actor", role, id, AccountStatus.ACTIVE);
    }

    private void verifyNoTeamResolution() {
        verify(teams, never()).findByProjectId(any());
        verify(members, never()).findDistinctStudentIdsByTeamId(any());
    }
}
