package com.saga.be.service;

import com.saga.be.entity.JiraWriteOperation;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.JiraWriteOperationStatus;
import com.saga.be.entity.enums.JiraWriteOperationType;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.repository.JiraWriteOperationRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Produces only from an already durable COMPLETED SAGA-originated Jira write. */
@Service
public class JiraMutationNotificationProducer {
    private final JiraWriteOperationRepository operations;
    private final TaskRepository tasks;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final NotificationService notifications;

    public JiraMutationNotificationProducer(
            JiraWriteOperationRepository operations,
            TaskRepository tasks,
            TeamRepository teams,
            TeamMemberRepository members,
            NotificationService notifications
    ) {
        this.operations = operations;
        this.tasks = tasks;
        this.teams = teams;
        this.members = members;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void taskCompleted(UUID operationId, NotificationType type, SagaPrincipal actor) {
        JiraWriteOperation operation = completed(operationId);
        requireType(operation, type);
        Task task = tasks.findByProjectIdAndExternalId(
                        operation.getProject().getId(),
                        operation.getRemoteResourceId()
                )
                .orElse(null);
        if (task == null) {
            return;
        }
        notify(
                operation,
                type,
                taskRecipients(task, operation.getProject().getId()),
                excludedStudentActor(operation, actor),
                taskTitle(type),
                taskMessage(type, task)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sprintCompleted(
            UUID operationId,
            NotificationType type,
            SagaPrincipal actor,
            String sprintName
    ) {
        JiraWriteOperation operation = completed(operationId);
        requireType(operation, type);
        List<UUID> recipients = teams.findByProjectId(operation.getProject().getId())
                .map(team -> members.findDistinctStudentIdsByTeamId(team.getId()))
                .orElse(List.of());
        notify(
                operation,
                type,
                recipients,
                excludedStudentActor(operation, actor),
                sprintTitle(type),
                sprintMessage(type, sprintName)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deadline(Task task, NotificationType type) {
        List<UUID> recipients = taskRecipients(task, task.getProject().getId());
        String dueRevision = task.getDueDate().toLocalDate().toString();
        String eventKey = "task:" + task.getId() + ":due:" + dueRevision + ":type:" + type;
        recipients.stream()
                .filter(Objects::nonNull)
                .distinct()
                .forEach(id -> notifications.createOnceForEvent(
                        id,
                        ApplicationRole.STUDENT,
                        type,
                        deadlineTitle(type),
                        deadlineMessage(type, task),
                        eventKey
                ));
    }

    private JiraWriteOperation completed(UUID id) {
        JiraWriteOperation operation = operations.findById(id).orElseThrow();
        if (operation.getStatus() != JiraWriteOperationStatus.COMPLETED) {
            throw new IllegalStateException("write not completed");
        }
        return operation;
    }

    private List<UUID> taskRecipients(Task task, UUID projectId) {
        if (task.getAssignee() != null) {
            return List.of(task.getAssignee().getId());
        }
        return teams.findByProjectId(projectId)
                .map(team -> members.findDistinctStudentIdsByTeamId(team.getId()))
                .orElse(List.of());
    }

    private void notify(
            JiraWriteOperation operation,
            NotificationType type,
            List<UUID> recipients,
            UUID excludedStudentActor,
            String title,
            String message
    ) {
        String eventKey = "jira-write:" + operation.getId() + ":" + type;
        recipients.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(excludedStudentActor))
                .forEach(id -> notifications.createOnceForEvent(
                        id,
                        ApplicationRole.STUDENT,
                        type,
                        title,
                        message,
                        eventKey
                ));
    }

    private UUID excludedStudentActor(JiraWriteOperation operation, SagaPrincipal actor) {
        return actor != null && actor.applicationRole() == ApplicationRole.STUDENT
                ? operation.getActorProfileId()
                : null;
    }

    private void requireType(JiraWriteOperation operation, NotificationType type) {
        JiraWriteOperationType expected = switch (type) {
            case TASK_CREATED -> JiraWriteOperationType.TASK_CREATE;
            case TASK_UPDATED -> JiraWriteOperationType.TASK_UPDATE;
            case TASK_ASSIGNEE_CHANGED -> JiraWriteOperationType.TASK_ASSIGN;
            case TASK_SPRINT_CHANGED -> JiraWriteOperationType.TASK_SPRINT;
            case TASK_ESTIMATION_CHANGED -> JiraWriteOperationType.TASK_ESTIMATION;
            case TASK_STATUS_CHANGED -> JiraWriteOperationType.TASK_TRANSITION;
            case TASK_DELETED -> JiraWriteOperationType.TASK_DELETE;
            case SPRINT_CREATED -> JiraWriteOperationType.SPRINT_CREATE;
            case SPRINT_UPDATED -> JiraWriteOperationType.SPRINT_UPDATE;
            case SPRINT_STARTED -> JiraWriteOperationType.SPRINT_START;
            case SPRINT_CLOSED -> JiraWriteOperationType.SPRINT_CLOSE;
            case SPRINT_DELETED -> JiraWriteOperationType.SPRINT_DELETE;
            default -> throw new IllegalArgumentException("Unsupported Jira mutation notification type");
        };
        if (operation.getOperationType() != expected) {
            throw new IllegalArgumentException("Notification type does not match Jira write operation");
        }
    }

    private String taskTitle(NotificationType type) {
        return switch (type) {
            case TASK_CREATED -> "Task mới được tạo";
            case TASK_UPDATED -> "Task đã được cập nhật";
            case TASK_ASSIGNEE_CHANGED -> "Phân công Task thay đổi";
            case TASK_SPRINT_CHANGED -> "Sprint của Task đã thay đổi";
            case TASK_ESTIMATION_CHANGED -> "Estimation của Task đã thay đổi";
            case TASK_STATUS_CHANGED -> "Trạng thái Task đã thay đổi";
            case TASK_DELETED -> "Task đã bị xóa";
            default -> throw new IllegalArgumentException("Unsupported Task notification type");
        };
    }

    private String taskMessage(NotificationType type, Task task) {
        String label = taskLabel(task);
        return switch (type) {
            case TASK_CREATED -> label + " vừa được tạo.";
            case TASK_DELETED -> label + " vừa bị xóa.";
            default -> label + " vừa được cập nhật.";
        };
    }

    private String sprintTitle(NotificationType type) {
        return switch (type) {
            case SPRINT_CREATED -> "Sprint mới được tạo";
            case SPRINT_UPDATED -> "Sprint đã được cập nhật";
            case SPRINT_STARTED -> "Sprint đã bắt đầu";
            case SPRINT_CLOSED -> "Sprint đã đóng";
            case SPRINT_DELETED -> "Sprint đã bị xóa";
            default -> throw new IllegalArgumentException("Unsupported Sprint notification type");
        };
    }

    private String sprintMessage(NotificationType type, String name) {
        String normalizedName = boundedLabel(name);
        String label = normalizedName == null
                ? "Một Sprint"
                : normalizedName.regionMatches(true, 0, "Sprint", 0, "Sprint".length())
                        ? normalizedName
                        : "Sprint " + normalizedName;
        return switch (type) {
            case SPRINT_CREATED -> label + " vừa được tạo.";
            case SPRINT_STARTED -> label + " vừa bắt đầu.";
            case SPRINT_CLOSED -> label + " vừa đóng.";
            case SPRINT_DELETED -> label + " vừa bị xóa.";
            default -> label + " vừa được cập nhật.";
        };
    }

    private String deadlineTitle(NotificationType type) {
        return switch (type) {
            case TASK_DUE_TOMORROW -> "Task đến hạn vào ngày mai";
            case TASK_DUE_TODAY -> "Task đến hạn hôm nay";
            case TASK_OVERDUE -> "Task đã quá hạn";
            default -> throw new IllegalArgumentException("Unsupported deadline notification type");
        };
    }

    private String deadlineMessage(NotificationType type, Task task) {
        String label = taskLabel(task);
        return switch (type) {
            case TASK_DUE_TOMORROW -> label + " đến hạn vào ngày mai.";
            case TASK_DUE_TODAY -> label + " đến hạn hôm nay.";
            case TASK_OVERDUE -> label + " đã quá hạn.";
            default -> throw new IllegalArgumentException("Unsupported deadline notification type");
        };
    }

    private String taskLabel(Task task) {
        String key = boundedLabel(task.getExternalKey());
        String title = boundedLabel(task.getTitle());
        if (key != null && title != null) {
            return "Task " + key + ": " + title;
        }
        if (key != null) {
            return "Task " + key;
        }
        if (title != null) {
            return "Task " + title;
        }
        return "Một Task";
    }

    private String boundedLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 237) + "...";
    }
}
