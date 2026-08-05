package com.saga.be.dto.response;

import com.saga.be.entity.enums.TaskType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** DTO chỉ đọc cho Lecturer Analytics; không lộ credential hoặc provider identifier. */
public final class LecturerAnalyticsResponses {

    private LecturerAnalyticsResponses() {
    }

    public record TeamDetail(UUID courseId, UUID teamId, String teamName, ProjectSummary project,
                             Page<TeamMemberResponse> members) { }

    public record ProjectSummary(UUID id, String name) { }

    public record StudentProgress(UUID courseId, UUID studentId, UUID teamId, UUID projectId,
                                  long totalTasks, long completedTasks, double overallCompletionRate,
                                  long totalCommits, Map<TaskType, Long> taskDistribution,
                                  long unclassifiedTasks) { }

    public record StudentActivities(UUID courseId, UUID studentId, Page<Activity> activities) { }

    public record Activity(UUID sourceId, String type, LocalDateTime occurredAt, String title, UUID projectId,
                           UUID sprintId) { }

    /** Chỉ là aggregate hiện tại; không dựng lịch sử Sprint giả. */
    public record StudentContributionDetail(UUID courseId, UUID studentId, UUID teamId, UUID projectId,
                                            TeamContributionMemberResponse currentAggregate) { }

    public record EarlyWarnings(UUID courseId, List<EarlyWarning> warnings) { }

    public record EarlyWarning(UUID studentId, UUID teamId, String warningType, String severity,
                               LocalDateTime detectedAt, String message, UUID taskId,
                               LocalDateTime dueDate) { }

    public record InteractionGraph(List<InteractionNode> nodes, List<InteractionEdge> edges) { }

    public record InteractionNode(UUID studentId, String studentCode, String fullName) { }

    public record InteractionEdge(UUID fromStudentId, UUID toStudentId, String sourceType,
                                  long sourceCount, boolean directed) { }

    public record ActivityHeatmap(UUID courseId, UUID teamId, UUID studentId, LocalDate startDate,
                                  LocalDate endDate, List<HeatmapDay> days) { }

    public record HeatmapDay(LocalDate date, long commits, long totalActivities) { }

    public record SprintVelocity(UUID courseId, UUID teamId, List<SprintVelocityItem> sprints) { }

    /** Point là giá trị Task hiện tại, không phải snapshot cam kết đầu Sprint. */
    public record SprintVelocityItem(UUID sprintId, String sprintName, LocalDateTime startDate,
                                     LocalDateTime endDate, long totalTasks, long completedTasks,
                                     Integer currentPlannedPoints, Integer completedPoints,
                                     long tasksWithoutStoryPoints, long bugsCount) { }
}
