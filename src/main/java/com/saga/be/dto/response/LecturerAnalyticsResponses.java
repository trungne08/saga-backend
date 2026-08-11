package com.saga.be.dto.response;

import com.saga.be.entity.enums.TaskType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** DTO chỉ đọc cho Lecturer Analytics; không lộ credential hoặc provider identity nhạy cảm. */
public final class LecturerAnalyticsResponses {

    private LecturerAnalyticsResponses() {
    }

    public record TeamDetail(
            UUID courseId,
            UUID teamId,
            String teamName,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Project của Team; null khi Team chưa có Project."
            )
            ProjectSummary project,
            Page<TeamMemberResponse> members
    ) { }

    public record ProjectSummary(
            UUID id,
            String name,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Các GitHub repository local có provider repository ID dùng trực tiếp cho GitHub read API."
            )
            List<TeamGitHubRepositoryReference> repositories
    ) {
        public ProjectSummary {
            repositories = repositories == null ? List.of() : List.copyOf(repositories);
        }
    }

    public record TeamGitHubRepositoryReference(
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "GitHub repository ID kiểu số dùng làm path/query parameter repositoryId."
            )
            long repositoryId,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Tên repository an toàn dạng owner/name khi đã được persist."
            )
            String repositoryName
    ) { }

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
