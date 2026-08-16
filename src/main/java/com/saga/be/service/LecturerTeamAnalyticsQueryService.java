package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.entity.Comment;
import com.saga.be.entity.CommitData;
import com.saga.be.entity.PeerReview;
import com.saga.be.entity.GitRepo;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.GraphProcessingKind;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommentRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.GitRepoRepository;
import com.saga.be.repository.PeerReviewRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final GitRepoRepository gitRepoRepository;
    private final TaskRepository taskRepository;
    private final CommitDataRepository commitDataRepository;
    private final DocumentRepository documentRepository;
    private final CommentRepository commentRepository;
    private final SprintRepository sprintRepository;
    private final PeerReviewRepository peerReviewRepository;
    private final GraphProcessingRunRecorder graphProcessingRunRecorder;

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.TeamDetail detail(SagaPrincipal principal, UUID courseId,
            UUID teamId, Pageable pageable) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        Page<TeamMemberResponse> members = teamMemberRepository.findByTeamId(teamId, pageable)
                .map(TeamMemberResponse::from);
        return new LecturerAnalyticsResponses.TeamDetail(
                courseId,
                teamId,
                team.getName(),
                projectSummary(team),
                members
        );
    }

    private LecturerAnalyticsResponses.ProjectSummary projectSummary(Team team) {
        if (team.getProject() == null) {
            return null;
        }
        List<LecturerAnalyticsResponses.TeamGitHubRepositoryReference> repositories = gitRepoRepository
                .findByProjectIdAndRepositoryIdIsNotNullOrderByFullNameAscRepositoryIdAsc(
                        team.getProject().getId()
                )
                .stream()
                .map(this::repositoryReference)
                .toList();
        return new LecturerAnalyticsResponses.ProjectSummary(
                team.getProject().getId(),
                team.getProject().getName(),
                repositories
        );
    }

    private LecturerAnalyticsResponses.TeamGitHubRepositoryReference repositoryReference(GitRepo repository) {
        String repositoryName = repository.getFullName() == null
                ? repository.getName()
                : repository.getFullName();
        return new LecturerAnalyticsResponses.TeamGitHubRepositoryReference(
                repository.getRepositoryId(),
                repositoryName
        );
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.InteractionGraph interactions(SagaPrincipal principal, UUID courseId,
            UUID teamId) {
        Team team = authorization.requireTeam(principal, courseId, teamId);
        List<TeamMember> members = new ArrayList<>(teamMemberRepository.findByTeamId(teamId));
        List<LecturerAnalyticsResponses.InteractionNode> nodes = members.stream().map(member ->
                new LecturerAnalyticsResponses.InteractionNode(member.getStudent().getId(),
                        member.getStudent().getStudentCode(), member.getStudent().getFullName())).toList();
        if (team.getProject() == null) {
            LecturerAnalyticsResponses.InteractionGraph response = new LecturerAnalyticsResponses.InteractionGraph(nodes, List.of());
            graphProcessingRunRecorder.record(GraphProcessingKind.INTERACTION, courseId, teamId, null,
                    response.nodes().size(), response.edges().size());
            return response;
        }
        List<UUID> memberIds = members.stream().map(member -> member.getStudent().getId()).toList();
        Map<String, Long> counts = new HashMap<>();
        for (PeerReview review : peerReviewRepository.findByRevieweeIdInAndSprintBoardProjectId(
                memberIds, team.getProject().getId())) {
            if (review.getReviewer() != null && review.getReviewee() != null
                    && memberIds.contains(review.getReviewer().getId())) {
                String key = review.getReviewer().getId() + ":" + review.getReviewee().getId();
                counts.put(key, counts.getOrDefault(key, 0L) + 1L);
            }
        }
        List<LecturerAnalyticsResponses.InteractionEdge> edges = counts.entrySet().stream().map(entry -> {
            String[] pair = entry.getKey().split(":");
            return new LecturerAnalyticsResponses.InteractionEdge(UUID.fromString(pair[0]), UUID.fromString(pair[1]),
                    "PEER_REVIEW", entry.getValue(), true);
        }).toList();
        LecturerAnalyticsResponses.InteractionGraph response = new LecturerAnalyticsResponses.InteractionGraph(nodes, edges);
        graphProcessingRunRecorder.record(GraphProcessingKind.INTERACTION, courseId, teamId, null,
                response.nodes().size(), response.edges().size());
        return response;
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.StudentInteractionGraph studentInteractions(SagaPrincipal principal,
            UUID courseId, UUID teamId, UUID studentId) {
        Team team = authorization.requireGraphReadAccess(principal, courseId, teamId);
        teamMemberRepository.findByTeamIdAndStudentId(teamId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Team"));

        List<TeamMember> members = new ArrayList<>(teamMemberRepository.findByTeamId(teamId));
        members.sort(Comparator.comparing(
                (TeamMember member) -> !member.getStudent().getId().equals(studentId))
                .thenComparing(member -> member.getStudent().getStudentCode(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(member -> member.getStudent().getFullName(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(member -> member.getStudent().getId()));

        Map<UUID, StudentInteractionAccumulator> accumulators = new LinkedHashMap<>();
        for (TeamMember member : members) {
            accumulators.put(member.getStudent().getId(), new StudentInteractionAccumulator(
                    member.getStudent().getStudentCode(), member.getStudent().getFullName()));
        }

        Map<InteractionKey, Long> edgeCounts = new HashMap<>();
        if (team.getProject() != null && !accumulators.isEmpty()) {
            UUID projectId = team.getProject().getId();
            List<UUID> memberIds = new ArrayList<>(accumulators.keySet());
            collectPeerReviewInteractions(edgeCounts, accumulators,
                    peerReviewRepository.findBySprintBoardProjectIdOrderByCreatedAtAscIdAsc(projectId), memberIds);
            collectCommentInteractions(edgeCounts, accumulators, loadProjectComments(projectId), memberIds);
            collectTaskInteractions(edgeCounts, accumulators, taskRepository.findByProjectId(projectId), memberIds);
            collectCommitInteractions(edgeCounts, accumulators, commitDataRepository.findByProjectId(projectId), memberIds);
        }

        List<LecturerAnalyticsResponses.StudentInteractionNode> nodes = accumulators.entrySet().stream()
                .map(entry -> new LecturerAnalyticsResponses.StudentInteractionNode(
                        entry.getKey(),
                        entry.getValue().studentCode,
                        entry.getValue().fullName,
                        entry.getValue().degree()))
                .toList();
        List<LecturerAnalyticsResponses.StudentInteractionEdge> edges = edgeCounts.entrySet().stream()
                .sorted(Map.Entry.<InteractionKey, Long>comparingByKey(
                        Comparator.comparing((InteractionKey key) -> key.sourceType())
                                .thenComparing((InteractionKey key) -> key.fromStudentId())
                                .thenComparing((InteractionKey key) -> key.toStudentId())))
                .map(entry -> new LecturerAnalyticsResponses.StudentInteractionEdge(
                        entry.getKey().fromStudentId(),
                        entry.getKey().toStudentId(),
                        entry.getKey().sourceType(),
                        entry.getValue(),
                        true))
                .toList();
        LecturerAnalyticsResponses.StudentInteractionGraph response = new LecturerAnalyticsResponses.StudentInteractionGraph(
                courseId, teamId, studentId, nodes, edges);
        graphProcessingRunRecorder.record(GraphProcessingKind.INTERACTION, courseId, teamId, studentId,
                response.nodes().size(), response.edges().size());
        return response;
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.ActivityOverview overview(SagaPrincipal principal, UUID courseId,
            UUID teamId, LocalDate startDate, LocalDate endDate) {
        Team team = authorization.requireGraphReadAccess(principal, courseId, teamId);
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate không được sau endDate");
        }
        Map<LocalDate, HeatmapBucket> dayBuckets = new HashMap<>();
        if (team.getProject() != null) {
            UUID projectId = team.getProject().getId();
            List<UUID> studentIds = teamMemberRepository.findDistinctStudentIdsByTeamId(teamId);
            if (!studentIds.isEmpty()) {
                LocalDateTime startAt = startDate.atStartOfDay();
                LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
                merge(dayBuckets, commitDataRepository.aggregateDailyCountsByProjectAndAuthorIds(
                        projectId, studentIds, startAt, endExclusive), HeatmapActivity.COMMIT);
                merge(dayBuckets, peerReviewRepository.aggregateDailyCountsByProjectAndReviewerIds(
                        projectId, studentIds, startAt, endExclusive), HeatmapActivity.PEER_REVIEW);
                merge(dayBuckets, commentRepository.aggregateDailyCountsByProjectAndAuthorIds(
                        projectId, studentIds, startAt, endExclusive), HeatmapActivity.COMMENT);
                merge(dayBuckets, documentRepository.aggregateDailyCountsByProjectAndAuthorIds(
                        projectId, studentIds, startAt, endExclusive), HeatmapActivity.DOCUMENT);
                merge(dayBuckets, taskRepository.aggregateDailyCountsByProjectAndAssigneeIds(
                        projectId, studentIds, startAt, endExclusive), HeatmapActivity.TASK);
            }
        }
        List<LecturerAnalyticsResponses.OverviewDay> days = new ArrayList<>();
        HeatmapBucket totals = HeatmapBucket.empty();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            HeatmapBucket bucket = dayBuckets.getOrDefault(day, HeatmapBucket.empty());
            totals.add(bucket);
            days.add(new LecturerAnalyticsResponses.OverviewDay(day, bucket.commits, bucket.peerReviews,
                    bucket.comments, bucket.documents, bucket.tasks, bucket.totalActivities(), bucket.totalScore()));
        }
        return new LecturerAnalyticsResponses.ActivityOverview(
                courseId,
                teamId,
                startDate,
                endDate,
                List.copyOf(days),
                new LecturerAnalyticsResponses.ActivityTotals(totals.commits, totals.peerReviews, totals.comments,
                        totals.documents, totals.tasks, totals.totalActivities(), totals.totalScore())
        );
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.ActivityHeatmap heatmap(SagaPrincipal principal, UUID courseId,
            UUID teamId, UUID studentId, LocalDate startDate, LocalDate endDate) {
        Team team = authorization.requireGraphReadAccess(principal, courseId, teamId);
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate không được sau endDate");
        }
        List<TeamMember> members = studentId == null
                ? new ArrayList<>(teamMemberRepository.findByTeamId(teamId))
                : teamMemberRepository.findByTeamIdAndStudentId(teamId, studentId)
                .map(member -> new ArrayList<>(List.of(member)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Team"));
        if (studentId != null && members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Student trong Team");
        }
        members.sort(Comparator.comparing(
                (TeamMember member) -> member.getStudent().getStudentCode(),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        ).thenComparing(
                (TeamMember member) -> member.getStudent().getFullName(),
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
        ).thenComparing(member -> member.getStudent().getId()));
        Map<UUID, StudentHeatmapAccumulator> studentBuckets = new LinkedHashMap<>();
        for (TeamMember member : members) {
            studentBuckets.put(member.getStudent().getId(), new StudentHeatmapAccumulator(member.getStudent().getStudentCode(),
                    member.getStudent().getFullName()));
        }
        Map<LocalDate, HeatmapBucket> dayBuckets = new HashMap<>();
        if (team.getProject() != null && !studentBuckets.isEmpty()) {
            UUID projectId = team.getProject().getId();
            List<UUID> studentIds = new ArrayList<>(studentBuckets.keySet());
            LocalDateTime startAt = startDate.atStartOfDay();
            LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
            merge(studentBuckets, dayBuckets, commitDataRepository.aggregateDailyCountsByProjectAndAuthorIds(
                    projectId, studentIds, startAt, endExclusive), HeatmapActivity.COMMIT);
            merge(studentBuckets, dayBuckets, peerReviewRepository.aggregateDailyCountsByProjectAndReviewerIds(
                    projectId, studentIds, startAt, endExclusive), HeatmapActivity.PEER_REVIEW);
            merge(studentBuckets, dayBuckets, commentRepository.aggregateDailyCountsByProjectAndAuthorIds(
                    projectId, studentIds, startAt, endExclusive), HeatmapActivity.COMMENT);
            merge(studentBuckets, dayBuckets, documentRepository.aggregateDailyCountsByProjectAndAuthorIds(
                    projectId, studentIds, startAt, endExclusive), HeatmapActivity.DOCUMENT);
            merge(studentBuckets, dayBuckets, taskRepository.aggregateDailyCountsByProjectAndAssigneeIds(
                    projectId, studentIds, startAt, endExclusive), HeatmapActivity.TASK);
        }
        List<LecturerAnalyticsResponses.HeatmapStudentRow> rows = studentBuckets.entrySet().stream()
                .map(entry -> toRow(entry.getKey(), entry.getValue(), startDate, endDate))
                .toList();
        List<LecturerAnalyticsResponses.HeatmapDay> days = new ArrayList<>();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            HeatmapBucket bucket = dayBuckets.getOrDefault(day, HeatmapBucket.empty());
            days.add(new LecturerAnalyticsResponses.HeatmapDay(day, bucket.commits, bucket.peerReviews,
                    bucket.comments, bucket.documents, bucket.tasks, bucket.totalActivities(), bucket.totalScore()));
        }
        return new LecturerAnalyticsResponses.ActivityHeatmap(courseId, teamId, studentId,
                startDate, endDate, rows, List.copyOf(days));
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
                    .mapToInt(task -> task.getStoryPoint()).sum();
            int completed = sprintTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE)
                    .filter(task -> task.getStoryPoint() != null).mapToInt(task -> task.getStoryPoint()).sum();
            long withoutPoints = sprintTasks.stream().filter(task -> task.getStoryPoint() == null).count();
            long bugs = sprintTasks.stream().filter(task -> task.getType() == TaskType.BUG).count();
            rows.add(new LecturerAnalyticsResponses.SprintVelocityItem(sprint.getId(), sprint.getName(),
                    sprint.getStartDate(), sprint.getEndDate(), sprintTasks.size(), done, planned, completed,
                    withoutPoints, bugs));
        }
        return new LecturerAnalyticsResponses.SprintVelocity(courseId, teamId, List.copyOf(rows));
    }

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.BurndownChart burndown(SagaPrincipal principal, UUID courseId,
            UUID teamId, UUID sprintId) {
        Team team = authorization.requireGraphReadAccess(principal, courseId, teamId);
        if (team.getProject() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team chưa có Project");
        }
        Sprint sprint = sprintRepository.findByIdAndBoardProjectIdAndDeletedAtIsNull(
                sprintId, team.getProject().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy Sprint"));
        if (sprint.getStartDate() == null || sprint.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint thiếu startDate hoặc endDate");
        }
        LocalDate startDate = sprint.getStartDate().toLocalDate();
        LocalDate endDate = sprint.getEndDate().toLocalDate();
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint endDate không được trước startDate");
        }
        List<Task> tasks = taskRepository.findByProjectId(team.getProject().getId()).stream()
                .filter(task -> task != null
                        && task.getDeletedAt() == null
                        && task.getSprint() != null
                        && sprintId.equals(task.getSprint().getId()))
                .toList();
        List<LecturerAnalyticsResponses.BurndownPoint> points = new ArrayList<>();
        long totalScope = tasks.size();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            LocalDateTime endExclusive = day.plusDays(1).atStartOfDay();
            long actualRemaining = tasks.stream().filter(task -> isOpenAt(task, endExclusive)).count();
            long doneCount = tasks.stream().filter(task -> isDoneBy(task, endExclusive)).count();
            points.add(new LecturerAnalyticsResponses.BurndownPoint(
                    day,
                    idealRemaining(totalScope, startDate, endDate, day),
                    actualRemaining,
                    doneCount
            ));
        }
        return new LecturerAnalyticsResponses.BurndownChart(
                courseId,
                teamId,
                sprintId,
                sprint.getName(),
                startDate,
                endDate,
                totalScope,
                List.copyOf(points)
        );
    }

    private LecturerAnalyticsResponses.HeatmapStudentRow toRow(UUID studentId, StudentHeatmapAccumulator accumulator,
            LocalDate startDate, LocalDate endDate) {
        List<LecturerAnalyticsResponses.HeatmapCell> cells = new ArrayList<>();
        HeatmapBucket zero = HeatmapBucket.empty();
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            HeatmapBucket bucket = accumulator.byDate.getOrDefault(day, zero);
            cells.add(new LecturerAnalyticsResponses.HeatmapCell(day, bucket.commits, bucket.peerReviews,
                    bucket.comments, bucket.documents, bucket.tasks, bucket.totalActivities(), bucket.totalScore()));
        }
        return new LecturerAnalyticsResponses.HeatmapStudentRow(
                studentId,
                accumulator.studentCode,
                accumulator.fullName,
                accumulator.commits,
                accumulator.peerReviews,
                accumulator.comments,
                accumulator.documents,
                accumulator.tasks,
                accumulator.totalActivities(),
                accumulator.totalScore(),
                List.copyOf(cells)
        );
    }

    private void merge(Map<UUID, StudentHeatmapAccumulator> studentBuckets, Map<LocalDate, HeatmapBucket> dayBuckets,
            List<Object[]> rows, HeatmapActivity activity) {
        for (Object[] row : rows) {
            UUID studentId = (UUID) row[0];
            LocalDate date = toLocalDate(row[1]);
            long count = ((Number) row[2]).longValue();
            StudentHeatmapAccumulator studentBucket = studentBuckets.get(studentId);
            if (studentBucket == null) {
                continue;
            }
            studentBucket.add(date, activity, count);
            dayBuckets.computeIfAbsent(date, ignored -> HeatmapBucket.empty()).add(activity, count);
        }
    }

    private void merge(Map<LocalDate, HeatmapBucket> dayBuckets, List<Object[]> rows, HeatmapActivity activity) {
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[1]);
            long count = ((Number) row[2]).longValue();
            dayBuckets.computeIfAbsent(date, ignored -> HeatmapBucket.empty()).add(activity, count);
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        throw new IllegalArgumentException("Không thể chuyển giá trị ngày: " + value);
    }

    private boolean isOpenAt(Task task, LocalDateTime endExclusive) {
        LocalDateTime createdAt = task.getCreatedAt();
        if (createdAt != null && !createdAt.isBefore(endExclusive)) {
            return false;
        }
        LocalDateTime completedAt = completedAt(task);
        return completedAt == null || !completedAt.isBefore(endExclusive);
    }

    private boolean isDoneBy(Task task, LocalDateTime endExclusive) {
        LocalDateTime completedAt = completedAt(task);
        return completedAt != null && completedAt.isBefore(endExclusive);
    }

    private LocalDateTime completedAt(Task task) {
        if (task.getResolvedAt() != null) {
            return task.getResolvedAt();
        }
        if (task.getStatus() == TaskStatus.DONE) {
            return task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        }
        return null;
    }

    private long idealRemaining(long totalScope, LocalDate startDate, LocalDate endDate, LocalDate day) {
        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (totalDays <= 1) {
            return 0L;
        }
        long dayIndex = java.time.temporal.ChronoUnit.DAYS.between(startDate, day);
        double remaining = totalScope * (double) (totalDays - 1 - dayIndex) / (double) (totalDays - 1);
        return Math.max(0L, Math.round(remaining));
    }

    private List<Comment> loadProjectComments(UUID projectId) {
        List<Comment> comments = new ArrayList<>();
        comments.addAll(commentRepository.findByTaskProjectIdOrderByCreatedAtAscIdAsc(projectId));
        comments.addAll(commentRepository.findByPullRequestRepoProjectIdOrderByCreatedAtAscIdAsc(projectId));
        comments.addAll(commentRepository.findByGitIssueRepoProjectIdOrderByCreatedAtAscIdAsc(projectId));
        return comments;
    }

    private void collectPeerReviewInteractions(Map<InteractionKey, Long> edgeCounts,
            Map<UUID, StudentInteractionAccumulator> accumulators, List<PeerReview> reviews,
            List<UUID> memberIds) {
        for (PeerReview review : reviews) {
            if (review.getReviewer() == null || review.getReviewee() == null) {
                continue;
            }
            UUID from = review.getReviewer().getId();
            UUID to = review.getReviewee().getId();
            if (!memberIds.contains(from) || !memberIds.contains(to) || from.equals(to)) {
                continue;
            }
            addInteraction(edgeCounts, accumulators, from, to, "REVIEWED");
        }
    }

    private void collectCommentInteractions(Map<InteractionKey, Long> edgeCounts,
            Map<UUID, StudentInteractionAccumulator> accumulators, List<Comment> comments,
            List<UUID> memberIds) {
        for (Comment comment : comments) {
            if (comment.getAuthor() == null || comment.getParentComment() == null
                    || comment.getParentComment().getAuthor() == null) {
                continue;
            }
            UUID from = comment.getAuthor().getId();
            UUID to = comment.getParentComment().getAuthor().getId();
            if (!memberIds.contains(from) || !memberIds.contains(to) || from.equals(to)) {
                continue;
            }
            addInteraction(edgeCounts, accumulators, from, to, "COMMENTED_ON");
        }
    }

    private void collectTaskInteractions(Map<InteractionKey, Long> edgeCounts,
            Map<UUID, StudentInteractionAccumulator> accumulators, List<Task> tasks,
            List<UUID> memberIds) {
        for (Task task : tasks) {
            if (task.getReporter() == null || task.getAssignee() == null) {
                continue;
            }
            UUID from = task.getReporter().getId();
            UUID to = task.getAssignee().getId();
            if (!memberIds.contains(from) || !memberIds.contains(to) || from.equals(to)) {
                continue;
            }
            addInteraction(edgeCounts, accumulators, from, to, "ASSIGNED_TO");
        }
    }

    private void collectCommitInteractions(Map<InteractionKey, Long> edgeCounts,
            Map<UUID, StudentInteractionAccumulator> accumulators, List<CommitData> commits,
            List<UUID> memberIds) {
        for (CommitData commit : commits) {
            if (commit.getTask() == null || commit.getAuthor() == null || commit.getTask().getAssignee() == null) {
                continue;
            }
            UUID from = commit.getAuthor().getId();
            UUID to = commit.getTask().getAssignee().getId();
            if (!memberIds.contains(from) || !memberIds.contains(to) || from.equals(to)) {
                continue;
            }
            addInteraction(edgeCounts, accumulators, from, to, "COLLABORATED_WITH");
        }
    }

    private void addInteraction(Map<InteractionKey, Long> edgeCounts,
            Map<UUID, StudentInteractionAccumulator> accumulators, UUID from, UUID to, String sourceType) {
        InteractionKey key = new InteractionKey(sourceType, from, to);
        edgeCounts.put(key, edgeCounts.getOrDefault(key, 0L) + 1L);
        StudentInteractionAccumulator fromAccumulator = accumulators.get(from);
        if (fromAccumulator != null) {
            fromAccumulator.degree++;
        }
        StudentInteractionAccumulator toAccumulator = accumulators.get(to);
        if (toAccumulator != null) {
            toAccumulator.degree++;
        }
    }

    private enum HeatmapActivity {
        COMMIT,
        PEER_REVIEW,
        COMMENT,
        DOCUMENT,
        TASK
    }

    private static final class StudentHeatmapAccumulator {
        private final String studentCode;
        private final String fullName;
        private final Map<LocalDate, HeatmapBucket> byDate = new HashMap<>();
        private long commits;
        private long peerReviews;
        private long comments;
        private long documents;
        private long tasks;

        private StudentHeatmapAccumulator(String studentCode, String fullName) {
            this.studentCode = studentCode;
            this.fullName = fullName;
        }

        private void add(LocalDate date, HeatmapActivity activity, long count) {
            HeatmapBucket bucket = byDate.computeIfAbsent(date, ignored -> HeatmapBucket.empty());
            bucket.add(activity, count);
            switch (activity) {
                case COMMIT -> commits += count;
                case PEER_REVIEW -> peerReviews += count;
                case COMMENT -> comments += count;
                case DOCUMENT -> documents += count;
                case TASK -> tasks += count;
            }
        }

        private long totalActivities() {
            return commits + peerReviews + comments + documents + tasks;
        }

        private long totalScore() {
            return commits * 3 + peerReviews * 2 + comments + documents + tasks * 2;
        }
    }

    private static final class HeatmapBucket {
        private long commits;
        private long peerReviews;
        private long comments;
        private long documents;
        private long tasks;

        private static HeatmapBucket empty() {
            return new HeatmapBucket();
        }

        private void add(HeatmapActivity activity, long count) {
            switch (activity) {
                case COMMIT -> commits += count;
                case PEER_REVIEW -> peerReviews += count;
                case COMMENT -> comments += count;
                case DOCUMENT -> documents += count;
                case TASK -> tasks += count;
            }
        }

        private long totalActivities() {
            return commits + peerReviews + comments + documents + tasks;
        }

        private long totalScore() {
            return commits * 3 + peerReviews * 2 + comments + documents + tasks * 2;
        }

        private void add(HeatmapBucket other) {
            commits += other.commits;
            peerReviews += other.peerReviews;
            comments += other.comments;
            documents += other.documents;
            tasks += other.tasks;
        }
    }

    private record InteractionKey(String sourceType, UUID fromStudentId, UUID toStudentId) { }

    private static final class StudentInteractionAccumulator {
        private final String studentCode;
        private final String fullName;
        private long degree;

        private StudentInteractionAccumulator(String studentCode, String fullName) {
            this.studentCode = studentCode;
            this.fullName = fullName;
        }

        private long degree() {
            return degree;
        }
    }
}
