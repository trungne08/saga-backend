package com.saga.be.dto.request;

import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InternalAgentToolRequests {

    private InternalAgentToolRequests() {
    }

    public record Context(@NotNull UUID conversationId) {
    }

    public record Project(@NotNull UUID conversationId, @NotNull UUID projectId) {
    }

    public record ProjectTasks(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @Min(0) int page,
            @Min(1) @Max(50) int size
    ) {
    }

    public record Task(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @NotNull UUID taskId
    ) {
    }

    public record Team(@NotNull UUID conversationId, @NotNull UUID teamId) {
    }

    public record AssigneeResolve(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @Size(max = 255) String fullName,
            @Size(max = 32) String studentCode
    ) {
    }

    public record Course(@NotNull UUID conversationId, @NotNull UUID courseId) {
    }

    public record OptionalCourse(@NotNull UUID conversationId, UUID courseId) {
    }

    public record OptionalTeam(@NotNull UUID conversationId, UUID teamId) {
    }

    public record OptionalProject(
            @NotNull UUID conversationId,
            UUID projectId,
            UUID courseId
    ) {
    }

    public record CommitReview(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @Positive long repositoryId,
            @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{40}$") String commitSha
    ) {
    }

    public record TaskCreate(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @NotBlank @Size(max = 255) String title,
            @NotNull TaskType type,
            Priority priority,
            @Size(max = 8000) String description,
            LocalDate dueDate,
            @Size(max = 20) List<@Size(max = 255) String> labels,
            @Size(max = 20) List<@Size(max = 255) String> componentIds,
            UUID assigneeId
    ) {
    }

    public record TaskUpdate(
            @NotNull UUID conversationId,
            @NotNull UUID projectId,
            @NotNull UUID taskId,
            @Size(min = 1, max = 255) String title,
            @Size(max = 8000) String description,
            TaskType type,
            Priority priority,
            LocalDate dueDate,
            @Size(max = 20) List<@Size(max = 255) String> labels,
            @Size(max = 20) List<@Size(max = 255) String> componentIds
    ) {
    }
}
