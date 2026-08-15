package com.saga.be.service;

import com.saga.be.entity.CommitReviewResult;
import com.saga.be.entity.IdentityMap;
import com.saga.be.entity.Sprint;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.BusinessWarningCategory;
import com.saga.be.entity.enums.BusinessWarningSeverity;
import com.saga.be.entity.enums.IdentityMappingStatus;
import com.saga.be.entity.enums.IntegrationProvider;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.entity.enums.SprintProgressMode;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.CommitDataRepository;
import com.saga.be.repository.CommitReviewResultRepository;
import com.saga.be.repository.DocumentRepository;
import com.saga.be.repository.IdentityMapRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EarlyWarningV2Service {

    private static final Logger log = LoggerFactory.getLogger(EarlyWarningV2Service.class);

    private final TeamRepository teams;
    private final TeamMemberRepository teamMembers;
    private final IdentityMapRepository identityMaps;
    private final CommitDataRepository commits;
    private final DocumentRepository documents;
    private final SprintRepository sprints;
    private final TaskRepository tasks;
    private final CommitReviewResultRepository reviewResults;
    private final BusinessWarningService warnings;

    public EarlyWarningV2Service(
            TeamRepository teams,
            TeamMemberRepository teamMembers,
            IdentityMapRepository identityMaps,
            CommitDataRepository commits,
            DocumentRepository documents,
            SprintRepository sprints,
            TaskRepository tasks,
            CommitReviewResultRepository reviewResults,
            BusinessWarningService warnings
    ) {
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.identityMaps = identityMaps;
        this.commits = commits;
        this.documents = documents;
        this.sprints = sprints;
        this.tasks = tasks;
        this.reviewResults = reviewResults;
        this.warnings = warnings;
    }

    @Transactional
    public void scanBounded() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Team> allTeams = teams.findAll();
        int evaluated = 0;
        for (Team team : allTeams) {
            if (evaluated >= 50) {
                break;
            }
            try {
                evaluateTeam(team, now);
                evaluated++;
            } catch (RuntimeException exception) {
                log.warn("early-warning v2 team scan failed type={}", exception.getClass().getSimpleName());
            }
        }
    }

    private void evaluateTeam(Team team, LocalDateTime now) {
        if (team == null || team.getId() == null || team.getProject() == null) {
            return;
        }
        List<TeamMember> students = studentPopulation(team);
        List<MemberActivity> activities = new ArrayList<>();
        boolean allEvaluable = true;
        for (TeamMember member : students) {
            Optional<MemberActivity> activity = memberActivity(member, now);
            if (activity.isEmpty()) {
                allEvaluable = false;
                continue;
            }
            MemberActivity value = activity.get();
            activities.add(value);
            if (value.inactive()) {
                emitMemberInactivity(team, member, now);
            }
        }
        if (allEvaluable && !students.isEmpty() && activities.stream().allMatch(MemberActivity::inactive)) {
            emitTeamInactivity(team, now);
        }
        evaluateSprint(team, now);
        evaluateRepeatedCommitIssues(team);
    }

    private List<TeamMember> studentPopulation(Team team) {
        return teamMembers.findByTeamId(team.getId()).stream()
                .filter(member -> member.getRoleInTeam() == RoleInTeam.LEADER
                        || member.getRoleInTeam() == RoleInTeam.MEMBER)
                .filter(member -> member.getStudent() != null)
                .toList();
    }

    private Optional<MemberActivity> memberActivity(TeamMember member, LocalDateTime now) {
        if (member.getCreatedAt() == null) {
            return Optional.empty();
        }
        if (member.getCreatedAt().isAfter(now.minus(EarlyWarningPolicy.ACTIVITY_WINDOW))) {
            return Optional.empty();
        }
        java.util.UUID projectId = member.getTeam().getProject().getId();
        java.util.UUID studentId = member.getStudent().getId();
        LocalDateTime latestDocument = documents.findLatestCreatedAtByProjectAndAuthor(projectId, studentId);
        LocalDateTime latestCommit = null;
        Optional<IdentityMap> mapping = uniqueActiveGithub(studentId);
        if (mapping.isPresent()) {
            latestCommit = commits.findLatestMappedTimestamp(
                    projectId, studentId, mapping.get().getExternalAccountId()
            );
        }
        LocalDateTime last = max(latestCommit, latestDocument);
        boolean inactive = last == null || last.isBefore(now.minus(EarlyWarningPolicy.ACTIVITY_WINDOW));
        return Optional.of(new MemberActivity(inactive, last));
    }

    private Optional<IdentityMap> uniqueActiveGithub(java.util.UUID studentId) {
        List<IdentityMap> active = identityMaps.findByStudentIdOrderByProvider(studentId).stream()
                .filter(mapping -> mapping.getProvider() == IntegrationProvider.GITHUB
                        && mapping.getMappingStatus() == IdentityMappingStatus.ACTIVE)
                .toList();
        if (active.size() != 1 || active.get(0).getExternalAccountId() == null
                || active.get(0).getExternalAccountId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(active.get(0));
    }

    private void emitMemberInactivity(Team team, TeamMember member, LocalDateTime now) {
        String day = now.toLocalDate().toString();
        String eventKey = "inactivity:member:" + team.getId() + ":"
                + member.getStudent().getId() + ":MEMBER_NO_RECENT_ACTIVITY_3D:" + day;
        List<BusinessWarningService.Recipient> recipients = new ArrayList<>(warnings.leadersOfTeam(team.getId()));
        recipients.add(new BusinessWarningService.Recipient(
                member.getStudent().getId(), com.saga.be.security.ApplicationRole.STUDENT
        ));
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.MEMBER_NO_RECENT_ACTIVITY_3D,
                BusinessWarningCategory.CONFIRMED,
                eventKey,
                "Không có hoạt động accepted trong 72 giờ",
                "Không có accepted activity (canonical mapped commit timestamp hoặc document createdAt) trong cửa sổ detector 72h.",
                null,
                team.getId(),
                team.getProject().getId(),
                null,
                member.getStudent().getId(),
                null,
                null,
                List.copyOf(recipients)
        ));
    }

    private void emitTeamInactivity(Team team, LocalDateTime now) {
        String day = now.toLocalDate().toString();
        String eventKey = "inactivity:team:" + team.getId() + ":TEAM_NO_RECENT_ACTIVITY_3D:" + day;
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.TEAM_NO_RECENT_ACTIVITY_3D,
                BusinessWarningCategory.CONFIRMED,
                eventKey,
                "Nhóm không có hoạt động accepted trong 72 giờ",
                "Không có accepted activity trong cửa sổ detector 72h cho population TeamMember hiện tại.",
                null,
                team.getId(),
                team.getProject().getId(),
                null,
                null,
                null,
                null,
                warnings.leadersOfTeam(team.getId())
        ));
    }

    private void evaluateSprint(Team team, LocalDateTime now) {
        List<Sprint> active = sprints.findByBoardProjectIdAndDeletedAtIsNull(team.getProject().getId()).stream()
                .filter(sprint -> sprint.getState() != null && "active".equalsIgnoreCase(sprint.getState()))
                .toList();
        if (active.size() != 1) {
            return;
        }
        Sprint sprint = active.get(0);
        if (sprint.getStartDate() == null || sprint.getEndDate() == null
                || !sprint.getEndDate().isAfter(sprint.getStartDate())) {
            return;
        }
        long total = ChronoUnit.SECONDS.between(sprint.getStartDate(), sprint.getEndDate());
        if (total <= 0) {
            return;
        }
        double elapsedRatio = Math.max(0d, (double) ChronoUnit.SECONDS.between(sprint.getStartDate(), now) / total);
        if (elapsedRatio < EarlyWarningPolicy.SPRINT_START_EVALUATION) {
            return;
        }
        List<Task> sprintTasks = tasks.findBySprintIdAndDeletedAtIsNull(sprint.getId()).stream()
                .filter(task -> task.getStatus() != TaskStatus.CANCELLED)
                .toList();
        if (sprintTasks.isEmpty()) {
            return;
        }
        boolean storyPointsComplete = sprintTasks.stream().allMatch(task -> task.getStoryPoint() != null);
        SprintProgressMode mode;
        double progressRatio;
        if (storyPointsComplete) {
            double planned = sprintTasks.stream()
                    .mapToDouble(task -> task.getStoryPoint().doubleValue())
                    .sum();
            if (planned <= 0d) {
                return;
            }
            double completed = sprintTasks.stream()
                    .filter(task -> task.getStatus() == TaskStatus.DONE)
                    .mapToDouble(task -> task.getStoryPoint().doubleValue())
                    .sum();
            mode = SprintProgressMode.STORY_POINTS;
            progressRatio = completed / planned;
        } else {
            long planned = sprintTasks.size();
            long completed = sprintTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
            mode = SprintProgressMode.TASK_COUNT;
            progressRatio = (double) completed / planned;
        }
        double gap = elapsedRatio - progressRatio;
        BusinessWarningSeverity severity = null;
        if (gap >= EarlyWarningPolicy.SPRINT_CRITICAL_GAP) {
            severity = BusinessWarningSeverity.CRITICAL;
        } else if (gap >= EarlyWarningPolicy.SPRINT_WARNING_GAP) {
            severity = BusinessWarningSeverity.WARNING;
        }
        if (severity == null) {
            return;
        }
        String eventKey = "sprint:" + sprint.getId() + ":SPRINT_PROGRESS_BEHIND:"
                + severity.name() + ":" + now.toLocalDate();
        warnings.emit(new BusinessWarningService.WarningDraft(
                NotificationType.SPRINT_PROGRESS_BEHIND,
                BusinessWarningCategory.CONFIRMED,
                eventKey,
                "Tiến độ sprint chậm hơn thời gian đã trôi",
                "Sprint progress mode=" + mode.name()
                        + ", elapsedRatio=" + round(elapsedRatio)
                        + ", progressRatio=" + round(progressRatio)
                        + ", gap=" + round(gap)
                        + ", severity=" + severity.name() + ".",
                severity,
                team.getId(),
                team.getProject().getId(),
                sprint.getId(),
                null,
                null,
                mode,
                warnings.leadersOfTeam(team.getId())
        ));
    }

    private void evaluateRepeatedCommitIssues(Team team) {
        java.util.UUID projectId = team.getProject().getId();
        for (TeamMember member : studentPopulation(team)) {
            Optional<IdentityMap> mapping = uniqueActiveGithub(member.getStudent().getId());
            if (mapping.isEmpty()) {
                continue;
            }
            String externalId = mapping.get().getExternalAccountId();
            List<CommitReviewResult> eligible = reviewResults.findEligibleLiveLinkedByProject(
                    projectId, PageRequest.of(0, 50)
            ).stream()
                    .filter(result -> result.getCommit() != null
                            && externalId.equals(result.getCommit().getAuthorExternalId()))
                    .limit(EarlyWarningPolicy.REPEATED_WINDOW)
                    .toList();
            if (eligible.size() < EarlyWarningPolicy.REPEATED_WINDOW) {
                continue;
            }
            long needsChanges = eligible.stream().filter(result -> "NEEDS_CHANGES".equals(result.getVerdict())).count();
            if (needsChanges < EarlyWarningPolicy.REPEATED_THRESHOLD) {
                continue;
            }
            String windowIdentity = eligible.get(0).getId() + ":" + eligible.get(eligible.size() - 1).getId();
            String eventKey = "review:repeated:" + projectId + ":"
                    + member.getStudent().getId() + ":" + windowIdentity;
            List<BusinessWarningService.Recipient> recipients = new ArrayList<>(warnings.leadersOfTeam(team.getId()));
            recipients.add(new BusinessWarningService.Recipient(
                    member.getStudent().getId(), com.saga.be.security.ApplicationRole.STUDENT
            ));
            warnings.emit(new BusinessWarningService.WarningDraft(
                    NotificationType.REPEATED_COMMIT_ISSUES,
                    BusinessWarningCategory.CONFIRMED,
                    eventKey,
                    "Nhiều review gần đây có NEEDS_CHANGES",
                    EarlyWarningPolicy.REPEATED_THRESHOLD + "/" + EarlyWarningPolicy.REPEATED_WINDOW
                            + " eligible recent LIVE TASK_LINKED terminal reviews có NEEDS_CHANGES.",
                    null,
                    team.getId(),
                    projectId,
                    null,
                    member.getStudent().getId(),
                    null,
                    null,
                    List.copyOf(recipients)
            ));
        }
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private record MemberActivity(boolean inactive, LocalDateTime lastActivity) {
    }
}
