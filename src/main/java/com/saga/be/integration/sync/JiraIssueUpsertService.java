package com.saga.be.integration.sync;

import com.saga.be.entity.JiraBoard;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.identity.IdentityMappingService;
import com.saga.be.integration.provider.JiraIssueSnapshot;
import com.saga.be.repository.JiraBoardRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.service.TeamContributionRefreshService;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JiraIssueUpsertService {

    private final JiraBoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final SprintRepository sprintRepository;
    private final IdentityMappingService identityMappingService;
    private final TeamContributionRefreshService teamContributionRefreshService;

    public JiraIssueUpsertService(
            JiraBoardRepository boardRepository,
            TaskRepository taskRepository,
            SprintRepository sprintRepository,
            IdentityMappingService identityMappingService,
            TeamContributionRefreshService teamContributionRefreshService
    ) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.sprintRepository = sprintRepository;
        this.identityMappingService = identityMappingService;
        this.teamContributionRefreshService = teamContributionRefreshService;
    }

    @Transactional
    public boolean upsert(UUID boardId, JiraIssueSnapshot issue) {
        JiraBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "JIRA_LINK_NOT_FOUND",
                        "The Jira project link does not exist"
                ));
        Task task = taskRepository
                .findByProjectIdAndExternalId(
                        board.getProject().getId(),
                        issue.id()
                )
                .orElseGet(Task::new);
        if (
            task.getExternalUpdatedAt() != null
            && issue.updatedAt() != null
            && task.getExternalUpdatedAt().isAfter(issue.updatedAt())
        ) {
            return false;
        }

        task.setProject(board.getProject());
        task.setExternalId(issue.id());
        task.setExternalKey(issue.key());
        task.setTitle(issue.title());
        task.setType(taskType(issue.issueType()));
        task.setStatus(taskStatus(issue.status()));
        task.setPriority(priority(issue.priority()));
        task.setStoryPoint(issue.storyPoints());
        task.setDueDate(issue.dueDate());
        task.setExternalUpdatedAt(issue.updatedAt());
        task.setResolvedAt(issue.resolvedAt());
        task.setResolution(issue.resolution());
        task.setLabels(issue.labels());
        task.setDescription(issue.description());
        task.setComponents(issue.components());

        task.setAssignee(attribution(
                task.getAssignee(),
                task.getAssigneeExternalId(),
                issue.assigneeAccountId()
        ));
        task.setAssigneeExternalId(issue.assigneeAccountId());
        task.setReporter(attribution(
                task.getReporter(),
                task.getReporterExternalId(),
                issue.reporterAccountId()
        ));
        task.setReporterExternalId(issue.reporterAccountId());
        task.setSprint(sprint(board, issue));
        taskRepository.saveAndFlush(task);
        teamContributionRefreshService.refreshFromBoard(boardId);
        return true;
    }

    private Sprint sprint(JiraBoard board, JiraIssueSnapshot issue) {
        if (issue.sprintId() == null) {
            return null;
        }
        Sprint sprint = sprintRepository
                .findByBoardIdAndExternalSprintId(
                        board.getId(),
                        issue.sprintId()
                )
                .orElseGet(() -> Sprint.builder()
                        .board(board)
                        .externalSprintId(issue.sprintId())
                        .build());
        sprint.setName(issue.sprintName());
        return sprintRepository.save(sprint);
    }

    private Student attribution(
            Student current,
            String currentExternalId,
            String incomingExternalId
    ) {
        if (Objects.equals(currentExternalId, incomingExternalId)) {
            return current;
        }
        if (incomingExternalId == null) {
            return null;
        }
        return identityMappingService.findActiveStudent(
                IntegrationProvider.JIRA,
                incomingExternalId
        ).orElse(null);
    }

    private TaskType taskType(String value) {
        String normalized = normalize(value);
        return switch (normalized) {
            case "BUG" -> TaskType.BUG;
            case "FEATURE", "NEW_FEATURE" -> TaskType.FEATURE;
            case "STORY", "USER_STORY" -> TaskType.STORY;
            case "EPIC" -> TaskType.EPIC;
            case "SUBTASK", "SUB_TASK" -> TaskType.SUBTASK;
            default -> TaskType.TASK;
        };
    }

    private TaskStatus taskStatus(String value) {
        String normalized = normalize(value);
        if (normalized.contains("DONE") || normalized.contains("RESOLVED")) {
            return TaskStatus.DONE;
        }
        if (normalized.contains("REVIEW")) {
            return TaskStatus.IN_REVIEW;
        }
        if (normalized.contains("PROGRESS")) {
            return TaskStatus.IN_PROGRESS;
        }
        if (normalized.contains("CANCEL")) {
            return TaskStatus.CANCELLED;
        }
        return TaskStatus.TODO;
    }

    private Priority priority(String value) {
        String normalized = normalize(value);
        if (normalized.contains("HIGHEST") || normalized.contains("CRITICAL")) {
            return Priority.CRITICAL;
        }
        if (normalized.contains("HIGH")) {
            return Priority.HIGH;
        }
        if (normalized.contains("LOW")) {
            return Priority.LOW;
        }
        return Priority.MEDIUM;
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                        .toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]+", "_");
    }
}
