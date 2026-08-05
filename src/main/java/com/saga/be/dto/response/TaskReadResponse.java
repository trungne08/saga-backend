package com.saga.be.dto.response;

import com.saga.be.entity.Sprint;
import com.saga.be.entity.Student;
import com.saga.be.entity.Task;
import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TaskReadResponse(
        UUID id,
        UUID projectId,
        String externalId,
        String externalKey,
        String title,
        TaskType type,
        TaskStatus status,
        Priority priority,
        Integer storyPoint,
        LocalDateTime dueDate,
        LocalDateTime externalUpdatedAt,
        LocalDateTime resolvedAt,
        String resolution,
        String description,
        List<String> labels,
        List<Component> components,
        SprintReference sprint,
        StudentReference assignee,
        StudentReference reporter,
        UUID blocksTaskId
) {

    public static TaskReadResponse from(Task task) {
        return new TaskReadResponse(
                task.getId(),
                task.getProject().getId(),
                task.getExternalId(),
                task.getExternalKey(),
                task.getTitle(),
                task.getType(),
                task.getStatus(),
                task.getPriority(),
                task.getStoryPoint(),
                task.getDueDate(),
                task.getExternalUpdatedAt(),
                task.getResolvedAt(),
                task.getResolution(),
                task.getDescription(),
                task.getLabels(),
                task.getComponents().stream()
                        .map(component -> new Component(component.id(), component.name()))
                        .toList(),
                SprintReference.from(task.getSprint()),
                StudentReference.from(task.getAssignee()),
                StudentReference.from(task.getReporter()),
                task.getBlocksTask() == null ? null : task.getBlocksTask().getId()
        );
    }

    public record Component(String id, String name) {
    }

    public record SprintReference(UUID id, String name, String externalSprintId) {
        private static SprintReference from(Sprint sprint) {
            return sprint == null
                    ? null
                    : new SprintReference(
                            sprint.getId(),
                            sprint.getName(),
                            sprint.getExternalSprintId()
                    );
        }
    }

    public record StudentReference(UUID id, String fullName, String studentCode) {
        private static StudentReference from(Student student) {
            return student == null
                    ? null
                    : new StudentReference(
                            student.getId(),
                            student.getFullName(),
                            student.getStudentCode()
                    );
        }
    }
}
