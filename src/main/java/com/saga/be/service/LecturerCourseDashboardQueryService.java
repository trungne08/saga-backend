package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.LecturerCourseDashboardResponses;
import com.saga.be.entity.Project;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.SagaPrincipal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LecturerCourseDashboardQueryService {

    private final LecturerAnalyticsAuthorizationService authorization;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final CommitDataRepository commitDataRepository;
    private final CourseEarlyWarningQueryService earlyWarningQueryService;

    @Transactional(readOnly = true)
    public LecturerCourseDashboardResponses.TeamsProgress teamsProgress(
            SagaPrincipal principal,
            UUID courseId
    ) {
        authorization.requireCourseAccess(principal, courseId);
        List<Team> teams = teams(courseId);
        Map<UUID, List<Sprint>> sprintsByProject = sprintsByProject(courseId);
        Map<UUID, List<Task>> tasksByProject = tasksByProject(courseId);
        Map<UUID, Long> commitsByProject = commitCountsByProject(courseId);
        List<LecturerCourseDashboardResponses.TeamProgress> rows = teams.stream()
                .map(team -> teamProgress(
                        team,
                        sprintsByProject,
                        tasksByProject,
                        commitsByProject
                ))
                .toList();
        return new LecturerCourseDashboardResponses.TeamsProgress(courseId, rows);
    }

    @Transactional(readOnly = true)
    public LecturerCourseDashboardResponses.ContributionSummary contributionSummary(
            SagaPrincipal principal,
            UUID courseId
    ) {
        authorization.requireCourseAccess(principal, courseId);
        List<Team> teams = teams(courseId);
        Set<UUID> studentIds = teamMemberRepository.findByTeamCourseId(courseId).stream()
                .map(TeamMember::getStudent)
                .filter(Objects::nonNull)
                .map(student -> student.getId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long membershipStudents = studentIds.size();
        return new LecturerCourseDashboardResponses.ContributionSummary(
                courseId,
                teams.size(),
                null,
                membershipStudents,
                null,
                null
        );
    }

    @Transactional(readOnly = true)
    public LecturerCourseDashboardResponses.Trends trends(
            SagaPrincipal principal,
            UUID courseId
    ) {
        authorization.requireCourseAccess(principal, courseId);
        Map<UUID, Team> teamByProject = teams(courseId).stream()
                .filter(team -> team.getProject() != null && team.getProject().getId() != null)
                .collect(Collectors.toMap(
                        team -> team.getProject().getId(),
                        team -> team,
                        (first, ignored) -> {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "Multiple Teams reference the same Project"
                            );
                        },
                        LinkedHashMap::new
                ));
        Map<UUID, List<Task>> tasksBySprint = taskRepository
                .findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId)
                .stream()
                .filter(task -> task.getSprint() != null && task.getSprint().getId() != null)
                .collect(Collectors.groupingBy(task -> task.getSprint().getId()));
        List<LecturerCourseDashboardResponses.SprintTrend> rows = sprintRepository
                .findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId)
                .stream()
                .filter(sprint -> projectId(sprint) != null && teamByProject.containsKey(projectId(sprint)))
                .sorted(sprintOrder())
                .map(sprint -> sprintTrend(
                        sprint,
                        teamByProject.get(projectId(sprint)),
                        tasksBySprint.getOrDefault(sprint.getId(), List.of()).stream()
                                .filter(task -> task.getProject() != null
                                        && Objects.equals(task.getProject().getId(), projectId(sprint)))
                                .toList()
                ))
                .toList();
        return new LecturerCourseDashboardResponses.Trends(courseId, rows);
    }

    @Transactional(readOnly = true)
    public LecturerCourseDashboardResponses.AtRiskSummary atRiskSummary(
            SagaPrincipal principal,
            UUID courseId
    ) {
        LecturerAnalyticsResponses.EarlyWarnings source = earlyWarningQueryService.get(principal, courseId);
        Map<String, Long> distribution = source.warnings().stream()
                .collect(Collectors.groupingBy(
                        LecturerAnalyticsResponses.EarlyWarning::warningType,
                        TreeMap::new,
                        Collectors.counting()
                ));
        Map<StudentTeamKey, List<LecturerAnalyticsResponses.EarlyWarning>> byStudent = source.warnings().stream()
                .filter(warning -> warning.studentId() != null && warning.teamId() != null)
                .collect(Collectors.groupingBy(
                        warning -> new StudentTeamKey(warning.studentId(), warning.teamId())
                ));
        List<LecturerCourseDashboardResponses.AtRiskStudent> students = byStudent.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> atRiskStudent(entry.getKey(), entry.getValue()))
                .toList();
        long affectedTeams = source.warnings().stream()
                .map(LecturerAnalyticsResponses.EarlyWarning::teamId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new LecturerCourseDashboardResponses.AtRiskSummary(
                courseId,
                source.warnings().size(),
                students.stream().map(LecturerCourseDashboardResponses.AtRiskStudent::studentId).distinct().count(),
                affectedTeams,
                distribution,
                students
        );
    }

    private LecturerCourseDashboardResponses.TeamProgress teamProgress(
            Team team,
            Map<UUID, List<Sprint>> sprintsByProject,
            Map<UUID, List<Task>> tasksByProject,
            Map<UUID, Long> commitsByProject
    ) {
        Project project = team.getProject();
        if (project == null || project.getId() == null) {
            return new LecturerCourseDashboardResponses.TeamProgress(
                    team.getId(), team.getName(), null, null,
                    0, 0, 0, 0, 0, 0, null
            );
        }
        UUID projectId = project.getId();
        Sprint current = currentSprint(sprintsByProject.getOrDefault(projectId, List.of()));
        List<Task> currentTasks = current == null
                ? List.of()
                : tasksByProject.getOrDefault(projectId, List.of()).stream()
                        .filter(task -> task.getSprint() != null && current.getId().equals(task.getSprint().getId()))
                        .toList();
        return new LecturerCourseDashboardResponses.TeamProgress(
                team.getId(),
                team.getName(),
                projectId,
                current == null ? null : currentSprint(current),
                currentTasks.size(),
                currentTasks.stream().filter(this::done).count(),
                storyPoints(currentTasks),
                storyPoints(currentTasks.stream().filter(this::done).toList()),
                currentTasks.stream().filter(task -> task.getStoryPoint() == null).count(),
                commitsByProject.getOrDefault(projectId, 0L),
                null
        );
    }

    private LecturerCourseDashboardResponses.SprintTrend sprintTrend(
            Sprint sprint,
            Team team,
            List<Task> tasks
    ) {
        return new LecturerCourseDashboardResponses.SprintTrend(
                sprint.getId(),
                sprint.getName(),
                sprint.getState(),
                sprint.getStartDate(),
                sprint.getEndDate(),
                team.getId(),
                team.getName(),
                tasks.size(),
                tasks.stream().filter(this::done).count(),
                storyPoints(tasks),
                storyPoints(tasks.stream().filter(this::done).toList()),
                tasks.stream().filter(task -> task.getStoryPoint() == null).count(),
                null
        );
    }

    private LecturerCourseDashboardResponses.AtRiskStudent atRiskStudent(
            StudentTeamKey key,
            List<LecturerAnalyticsResponses.EarlyWarning> warnings
    ) {
        Map<String, Long> distribution = warnings.stream().collect(Collectors.groupingBy(
                LecturerAnalyticsResponses.EarlyWarning::warningType,
                TreeMap::new,
                Collectors.counting()
        ));
        return new LecturerCourseDashboardResponses.AtRiskStudent(
                key.studentId(), key.teamId(), warnings.size(), distribution, null
        );
    }

    private List<Team> teams(UUID courseId) {
        return teamRepository.findByCourseIdOrderByNameAscIdAsc(courseId);
    }

    private Map<UUID, List<Sprint>> sprintsByProject(UUID courseId) {
        return sprintRepository.findByBoardProjectCourseIdAndDeletedAtIsNullOrderByStartDateAscIdAsc(courseId)
                .stream()
                .filter(sprint -> projectId(sprint) != null)
                .collect(Collectors.groupingBy(this::projectId));
    }

    private Map<UUID, List<Task>> tasksByProject(UUID courseId) {
        return taskRepository.findByProjectCourseIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(courseId)
                .stream()
                .filter(task -> task.getProject() != null && task.getProject().getId() != null)
                .collect(Collectors.groupingBy(task -> task.getProject().getId()));
    }

    private Map<UUID, Long> commitCountsByProject(UUID courseId) {
        Map<UUID, Long> counts = new LinkedHashMap<>();
        for (Object[] row : commitDataRepository.countByProjectForCourse(courseId)) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID projectId && row[1] instanceof Number count) {
                counts.put(projectId, count.longValue());
            }
        }
        return counts;
    }

    private Sprint currentSprint(List<Sprint> sprints) {
        List<Sprint> active = sprints.stream()
                .filter(sprint -> sprint.getState() != null && "active".equalsIgnoreCase(sprint.getState()))
                .toList();
        if (active.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Project has multiple active Sprints"
            );
        }
        return active.isEmpty() ? null : active.get(0);
    }

    private LecturerCourseDashboardResponses.CurrentSprint currentSprint(Sprint sprint) {
        return new LecturerCourseDashboardResponses.CurrentSprint(
                sprint.getId(), sprint.getName(), sprint.getState(), sprint.getStartDate(), sprint.getEndDate()
        );
    }

    private UUID projectId(Sprint sprint) {
        return sprint.getBoard() == null || sprint.getBoard().getProject() == null
                ? null
                : sprint.getBoard().getProject().getId();
    }

    private boolean done(Task task) {
        return task.getStatus() == TaskStatus.DONE;
    }

    private long storyPoints(List<Task> tasks) {
        return tasks.stream()
                .map(Task::getStoryPoint)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private Comparator<Sprint> sprintOrder() {
        return Comparator.comparing(
                        Sprint::getStartDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(Sprint::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private record StudentTeamKey(UUID studentId, UUID teamId) implements Comparable<StudentTeamKey> {
        @Override
        public int compareTo(StudentTeamKey other) {
            int studentOrder = studentId.compareTo(other.studentId);
            return studentOrder != 0 ? studentOrder : teamId.compareTo(other.teamId);
        }
    }
}
