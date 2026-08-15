package com.saga.be.integration.webhook;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Task;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraWebhookTaskDeleteService {

    private final JiraBoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final Clock clock;

    @Autowired
    public JiraWebhookTaskDeleteService(
            JiraBoardRepository boardRepository,
            TaskRepository taskRepository
    ) {
        this(boardRepository, taskRepository, Clock.systemUTC());
    }

    JiraWebhookTaskDeleteService(
            JiraBoardRepository boardRepository,
            TaskRepository taskRepository,
            Clock clock
    ) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    @Transactional
    public DeleteResult tombstone(UUID boardId, String externalIssueId, String externalIssueKey) {
        JiraBoard board = boardRepository.findById(boardId).orElseThrow(() ->
                IntegrationException.invalid(
                        "JIRA_LINK_NOT_FOUND",
                        "The Jira project link does not exist"
                ));
        UUID projectId = board.getProject().getId();
        Task task = normalized(externalIssueId) == null
                ? taskRepository.findByProjectIdAndExternalKey(
                        projectId,
                        normalized(externalIssueKey)
                ).orElse(null)
                : taskRepository.findByProjectIdAndExternalId(
                        projectId,
                        normalized(externalIssueId)
                ).orElse(null);
        if (task == null) {
            return DeleteResult.UNKNOWN_TASK;
        }
        if (task.getDeletedAt() != null) {
            return DeleteResult.ALREADY_TOMBSTONED;
        }
        task.setDeletedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        taskRepository.saveAndFlush(task);
        return DeleteResult.TOMBSTONED;
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public enum DeleteResult {
        TOMBSTONED,
        ALREADY_TOMBSTONED,
        UNKNOWN_TASK
    }
}
