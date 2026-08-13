package com.saga.be.integration.write;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.service.JiraMutationNotificationProducer;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recovers only persisted remote success; it never replays a Jira mutation. */
@Service
public class JiraWriteRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(JiraWriteRecoveryService.class);
    private final JiraWriteOperationRepository operations;
    private final JiraBoardRepository boards;
    private final JiraCredentialService credentials;
    private final JiraProviderClient provider;
    private final JiraIssueUpsertService issueUpserts;
    private final JiraSprintUpsertService sprintUpserts;
    private final TaskRepository tasks;
    private final SprintRepository sprints;
    private final JiraWriteOperationService operationService;
    private final JiraCanonicalTaskReadService canonicalTaskReadService;
    private final JiraMutationNotificationProducer notificationProducer;

    public JiraWriteRecoveryService(JiraWriteOperationRepository operations, JiraBoardRepository boards,
            JiraCredentialService credentials, JiraProviderClient provider, JiraIssueUpsertService issueUpserts,
            JiraSprintUpsertService sprintUpserts, TaskRepository tasks, SprintRepository sprints,
            JiraWriteOperationService operationService, JiraCanonicalTaskReadService canonicalTaskReadService,
            JiraMutationNotificationProducer notificationProducer) {
        this.operations = operations; this.boards = boards; this.credentials = credentials; this.provider = provider;
        this.issueUpserts = issueUpserts; this.sprintUpserts = sprintUpserts; this.tasks = tasks; this.sprints = sprints;
        this.operationService = operationService;
        this.canonicalTaskReadService = canonicalTaskReadService;
        this.notificationProducer = notificationProducer;
    }

    @Transactional
    public void recoverRemoteSuccesses() {
        for (JiraWriteOperation operation : operations.findByStatusIn(List.of(JiraWriteOperationStatus.REMOTE_SUCCEEDED))) {
            recover(operation);
        }
    }

    private void recover(JiraWriteOperation operation) {
        if (operation.getRemoteResourceId() == null || operation.getProject() == null) return;
        JiraBoard board = boards.findByProjectId(operation.getProject().getId()).orElse(null);
        if (board == null) return;
        String sprintName = null;
        if (operation.getOperationType() == JiraWriteOperationType.TASK_DELETE) {
            tasks.findByProjectIdAndExternalId(operation.getProject().getId(), operation.getRemoteResourceId())
                    .ifPresent(task -> { task.setDeletedAt(LocalDateTime.now()); tasks.saveAndFlush(task); });
        } else if (operation.getOperationType() == JiraWriteOperationType.SPRINT_DELETE) {
            Sprint sprint = sprints.findByBoardProjectIdAndDeletedAtIsNull(operation.getProject().getId()).stream()
                    .filter(candidate -> operation.getRemoteResourceId().equals(candidate.getExternalSprintId()))
                    .findFirst().orElse(null);
            if (sprint != null) {
                sprintName = sprint.getName();
                tombstoneSprint(operation.getProject().getId(), sprint);
            }
        } else if (operation.getOperationType() == JiraWriteOperationType.TASK_UPDATE
                || operation.getOperationType() == JiraWriteOperationType.TASK_SPRINT
                || operation.getOperationType() == JiraWriteOperationType.TASK_ESTIMATION) {
            log.warn("Jira write recovery remains pending: stage=TARGET_INTENT writeOperationStatus=REMOTE_SUCCEEDED operationType={}",
                    operation.getOperationType());
            return;
        } else if (operation.getOperationType().name().startsWith("TASK_")) {
            String token = credentials.validAccessToken(board);
            issueUpserts.upsert(board.getId(), provider.getIssue(token, board.getCloudId(), operation.getRemoteResourceId()));
            if (!canonicalTaskReadService.exists(operation.getProject().getId(), operation.getRemoteResourceId())) {
                log.warn("Jira write recovery remains pending: stage=CANONICAL_ISSUE_FETCH writeOperationStatus=REMOTE_SUCCEEDED operationType={}",
                        operation.getOperationType());
                return;
            }
        } else if (operation.getOperationType().name().startsWith("SPRINT_")) {
            String token = credentials.validAccessToken(board);
            var snapshot = provider.getSprint(token, board.getCloudId(), operation.getRemoteResourceId());
            sprintUpserts.upsert(board.getId(), snapshot);
            sprintName = snapshot.name();
        } else return;
        operationService.complete(operation.getId());
        emitCompleted(operation, sprintName);
    }

    private void emitCompleted(JiraWriteOperation operation, String sprintName) {
        try {
            switch (operation.getOperationType()) {
                case TASK_CREATE -> notificationProducer.taskCompleted(operation.getId(), NotificationType.TASK_CREATED, null);
                case TASK_ASSIGN -> notificationProducer.taskCompleted(operation.getId(), NotificationType.TASK_ASSIGNEE_CHANGED, null);
                case TASK_TRANSITION -> notificationProducer.taskCompleted(operation.getId(), NotificationType.TASK_STATUS_CHANGED, null);
                case TASK_DELETE -> notificationProducer.taskCompleted(operation.getId(), NotificationType.TASK_DELETED, null);
                case SPRINT_CREATE -> notificationProducer.sprintCompleted(operation.getId(), NotificationType.SPRINT_CREATED, null, sprintName);
                case SPRINT_UPDATE -> notificationProducer.sprintCompleted(operation.getId(), NotificationType.SPRINT_UPDATED, null, sprintName);
                case SPRINT_START -> notificationProducer.sprintCompleted(operation.getId(), NotificationType.SPRINT_STARTED, null, sprintName);
                case SPRINT_CLOSE -> notificationProducer.sprintCompleted(operation.getId(), NotificationType.SPRINT_CLOSED, null, sprintName);
                case SPRINT_DELETE -> notificationProducer.sprintCompleted(operation.getId(), NotificationType.SPRINT_DELETED, null, sprintName);
                case TASK_UPDATE, TASK_SPRINT, TASK_ESTIMATION -> throw new IllegalStateException("Target-aware recovery must not complete in background");
            }
        } catch (RuntimeException exception) {
            log.warn("Jira recovery notification failed after durable completion: operationType={}",
                    operation.getOperationType(), exception);
        }
    }

    private void tombstoneSprint(java.util.UUID projectId, Sprint sprint) {
        for (Task task : tasks.findByProjectId(projectId)) {
            if (task.getSprint() != null && sprint.getId().equals(task.getSprint().getId())) task.setSprint(null);
        }
        tasks.flush(); sprint.setDeletedAt(LocalDateTime.now()); sprints.saveAndFlush(sprint);
    }
}
