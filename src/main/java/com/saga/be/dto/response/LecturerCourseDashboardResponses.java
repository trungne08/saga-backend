package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Source-backed Course dashboard DTOs. No provider or persistence entity is exposed. */
public final class LecturerCourseDashboardResponses {

    private LecturerCourseDashboardResponses() {
    }

    public record TeamsProgress(UUID courseId, List<TeamProgress> teams) {
        public TeamsProgress {
            teams = teams == null ? List.of() : List.copyOf(teams);
        }
    }

    public record TeamProgress(
            UUID teamId,
            String teamName,
            UUID projectId,
            CurrentSprint currentSprint,
            List<ActiveSprint> activeSprints,
            long currentSprintTaskCount,
            long currentSprintDoneTaskCount,
            long currentSprintPlannedStoryPoints,
            long currentSprintCompletedStoryPoints,
            long currentSprintTasksWithoutStoryPoints,
            long projectCommitCount,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Always null until an accepted deterministic health rule exists.",
                    nullable = true
            )
            String healthStatus
    ) {
        public TeamProgress {
            activeSprints = activeSprints == null ? List.of() : List.copyOf(activeSprints);
        }
    }

    public record CurrentSprint(
            UUID sprintId,
            String sprintName,
            String state,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) { }

    /**
     * One active Sprint and its current Task snapshot. Ordering is deterministic
     * (startDate, then id) and does not imply a primary Sprint.
     */
    public record ActiveSprint(
            UUID sprintId,
            String sprintName,
            String state,
            LocalDateTime startDate,
            LocalDateTime endDate,
            long taskCount,
            long doneTaskCount,
            long plannedStoryPoints,
            long completedStoryPoints,
            long tasksWithoutStoryPoints
    ) { }

    public record ContributionSummary(
            UUID courseId,
            long teamCount,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Null because TeamMember proves only Students with a Team, not the total Course population.",
                    nullable = true
            )
            Long totalStudents,
            long totalStudentsWithTeam,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Null because SAGA has no authoritative Course enrollment without TeamMember.",
                    nullable = true
            )
            Long totalStudentsWithoutTeam,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Null because no authoritative course-level generated-slice aggregate exists.",
                    nullable = true
            )
            Long totalSlicesGenerated
    ) { }

    @io.swagger.v3.oas.annotations.media.Schema(
            description = "PARTIAL_CURRENT_TASK_SPRINT_SNAPSHOT: current persisted Task-to-Sprint associations, "
                    + "not immutable historical velocity or Contribution history."
    )
    public record Trends(UUID courseId, List<SprintTrend> sprints) {
        public Trends {
            sprints = sprints == null ? List.of() : List.copyOf(sprints);
        }
    }

    /** Current Task-to-Sprint snapshot, not a committed planning or Contribution history. */
    public record SprintTrend(
            UUID sprintId,
            String sprintName,
            String sprintState,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID teamId,
            String teamName,
            long totalTasks,
            long completedTasks,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Current sum from Tasks presently associated with this Sprint; not committed scope history."
            )
            long currentPlannedStoryPoints,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Current sum from DONE Tasks presently associated with this Sprint."
            )
            long currentCompletedStoryPoints,
            long tasksWithoutStoryPoints,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Always null because no authoritative Sprint-scoped Contribution/Slice history exists.",
                    nullable = true
            )
            Long totalSlicesGenerated
    ) { }

    public record AtRiskSummary(
            UUID courseId,
            long totalWarnings,
            long affectedStudents,
            long affectedTeams,
            Map<String, Long> warningDistribution,
            List<AtRiskStudent> students
    ) {
        public AtRiskSummary {
            warningDistribution = warningDistribution == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(warningDistribution));
            students = students == null ? List.of() : List.copyOf(students);
        }
    }

    public record AtRiskStudent(
            UUID studentId,
            UUID teamId,
            long warningCount,
            Map<String, Long> warningDistribution,
            @io.swagger.v3.oas.annotations.media.Schema(
                    description = "Always null while the existing warning source has no accepted severity rule.",
                    nullable = true
            )
            String riskLevel
    ) {
        public AtRiskStudent {
            warningDistribution = warningDistribution == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(warningDistribution));
        }
    }
}
