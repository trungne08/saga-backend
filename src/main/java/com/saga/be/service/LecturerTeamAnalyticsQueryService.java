package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LecturerTeamAnalyticsQueryService {
    private final LecturerAnalyticsAuthorizationService authorization;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;
    private final CommitDataRepository commitDataRepository;
    private final SprintRepository sprintRepository;
    private final PeerReviewRepository peerReviewRepository;

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.TeamDetail detail(SagaPrincipal principal, UUID courseId,
            UUID teamId, Pageable pageable) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        Page<TeamMemberResponse> members = teamMemberRepository.findByTeamId(teamId, pageable)
                .map(TeamMemberResponse::from);
        return new LecturerAnalyticsResponses.TeamDetail(courseId, teamId, team.getName(),
                team.getProject() == null ? null : new LecturerAnalyticsResponses.ProjectSummary(
                        team.getProject().getId(), team.getProject().getName()), members);
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.InteractionGraph interactions(SagaPrincipal principal, UUID courseId,
            UUID teamId) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        List<LecturerAnalyticsResponses.InteractionNode> nodes = members.stream().map(member ->
                new LecturerAnalyticsResponses.InteractionNode(member.getStudent().getId(),
                        member.getStudent().getStudentCode(), member.getStudent().getFullName())).toList();
        if (team.getProject() == null) {
            return new LecturerAnalyticsResponses.InteractionGraph(nodes, List.of());
        }
        List<UUID> memberIds = members.stream().map(member -> member.getStudent().getId()).toList();
        Map<String, Long> counts = new HashMap<>();
        for (PeerReview review : peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                memberIds, team.getProject().getId())) {
            if (review.getReviewer() != null && review.getReviewee() != null
                    && memberIds.contains(review.getReviewer().getId())) {
                counts.merge(review.getReviewer().getId() + ":" + review.getReviewee().getId(), 1L, Long::sum);
            }
        }
        List<LecturerAnalyticsResponses.InteractionEdge> edges = counts.entrySet().stream().map(entry -> {
            String[] pair = entry.getKey().split(":");
            return new LecturerAnalyticsResponses.InteractionEdge(UUID.fromString(pair[0]), UUID.fromString(pair[1]),
                    "PEER_REVIEW", entry.getValue(), true);
        }).toList();
        return new LecturerAnalyticsResponses.InteractionGraph(nodes, edges);
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.ActivityHeatmap heatmap(SagaPrincipal principal, UUID courseId,
            UUID teamId, UUID studentId, LocalDate startDate, LocalDate endDate) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate không được sau endDate");
        }
        if (studentId != null && !teamMemberRepository.existsByTeamIdAndStudentId(teamId, studentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Team");
        }
        Map<LocalDate, Long> counts = new HashMap<>();
        if (team.getProject() != null) {
            for (Object[] row : commitDataRepository.aggregateDailyCounts(team.getProject().getId(), studentId,
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())) {
                LocalDate day = row[0] instanceof LocalDate localDate
                        ? localDate : ((java.sql.Date) row[0]).toLocalDate();
                counts.put(day, ((Number) row[1]).longValue());
            }
        }
        List<LecturerAnalyticsResponses.HeatmapDay> days = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            long count = counts.getOrDefault(day, 0L);
            days.add(new LecturerAnalyticsResponses.HeatmapDay(day, count, count));
        }
        return new LecturerAnalyticsResponses.ActivityHeatmap(courseId, teamId, studentId,
                startDate, endDate, List.copyOf(days));
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.SprintVelocity velocity(SagaPrincipal principal, UUID courseId,
            UUID teamId) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        if (team.getProject() == null) {
            return new LecturerAnalyticsResponses.SprintVelocity(courseId, teamId, List.of());
        }
        List<Task> tasks = taskRepository.findByProjectId(team.getProject().getId());
        List<LecturerAnalyticsResponses.SprintVelocityItem> rows = new ArrayList<>();
        for (Sprint sprint : sprintRepository.findByBoardProjectIdOrderByStartDateAsc(team.getProject().getId())) {
            List<Task> sprintTasks = tasks.stream().filter(task -> task.getSprint() != null
                    && sprint.getId().equals(task.getSprint().getId())).toList();
            long done = sprintTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
            int planned = sprintTasks.stream().filter(task -> task.getStoryPoint() != null)
                    .mapToInt(Task::getStoryPoint).sum();
            int completed = sprintTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                    .filter(task -> task.getStoryPoint() != null).mapToInt(Task::getStoryPoint).sum();
            long withoutPoints = sprintTasks.stream().filter(task -> task.getStoryPoint() == null).count();
            long bugs = sprintTasks.stream().filter(task -> task.getType() == TaskType.BUG).count();
            rows.add(new LecturerAnalyticsResponses.SprintVelocityItem(sprint.getId(), sprint.getName(),
                    sprint.getStartDate(), sprint.getEndDate(), sprintTasks.size(), done, planned, completed,
                    withoutPoints, bugs));
        }
        return new LecturerAnalyticsResponses.SprintVelocity(courseId, teamId, List.copyOf(rows));
    }
}
