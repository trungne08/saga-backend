package com.saga.be.integration.write;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.integration.project.JiraCredentialService;
import com.saga.be.integration.provider.JiraProviderClient;
import com.saga.be.integration.sync.JiraIssueUpsertService;
import com.saga.be.integration.sync.JiraSprintUpsertService;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
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

    public JiraWriteRecoveryService(JiraWriteOperationRepository operations, JiraBoardRepository boards,
            JiraCredentialService credentials, JiraProviderClient provider, JiraIssueUpsertService issueUpserts,
            JiraSprintUpsertService sprintUpserts, TaskRepository tasks, SprintRepository sprints,
            JiraWriteOperationService operationService, JiraCanonicalTaskReadService canonicalTaskReadService) {
        this.operations = operations; this.boards = boards; this.credentials = credentials; this.provider = provider;
        this.issueUpserts = issueUpserts; this.sprintUpserts = sprintUpserts; this.tasks = tasks; this.sprints = sprints;
        this.operationService = operationService;
        this.canonicalTaskReadService = canonicalTaskReadService;
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
        if (operation.getOperationType() == JiraWriteOperationType.TASK_DELETE) {
            tasks.findByProjectIdAndExternalId(operation.getProject().getId(), operation.getRemoteResourceId())
                    .ifPresent(task -> { task.setDeletedAt(LocalDateTime.now()); tasks.saveAndFlush(task); });
        } else if (operation.getOperationType() == JiraWriteOperationType.SPRINT_DELETE) {
            sprints.findByBoardProjectIdAndDeletedAtIsNull(operation.getProject().getId()).stream()
                    .filter(sprint -> operation.getRemoteResourceId().equals(sprint.getExternalSprintId()))
                    .findFirst().ifPresent(sprint -> tombstoneSprint(operation.getProject().getId(), sprint));
        } else if (operation.getOperationType() == JiraWriteOperationType.TASK_SPRINT
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
            sprintUpserts.upsert(board.getId(), provider.getSprint(token, board.getCloudId(), operation.getRemoteResourceId()));
        } else return;
        operationService.complete(operation.getId());
    }

    private void tombstoneSprint(java.util.UUID projectId, Sprint sprint) {
        for (Task task : tasks.findByProjectId(projectId)) {
            if (task.getSprint() != null && sprint.getId().equals(task.getSprint().getId())) task.setSprint(null);
        }
        tasks.flush(); sprint.setDeletedAt(LocalDateTime.now()); sprints.saveAndFlush(sprint);
    }
}
