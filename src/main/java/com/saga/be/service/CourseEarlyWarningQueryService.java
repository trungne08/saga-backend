package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.entity.Task;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.repository.TaskRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseEarlyWarningQueryService {
    private final LecturerAnalyticsAuthorizationService authorization;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.EarlyWarnings get(SagaPrincipal principal, UUID courseId) {
        authorization.requireCourseAccess(principal, courseId);
        LocalDateTime nowUtc = LocalDateTime.now(Clock.systemUTC());
        List<LecturerAnalyticsResponses.EarlyWarning> warnings = new ArrayList<>();
        Map<String, TeamMember> membershipByProjectAndStudent = teamMemberRepository.findByTeamCourseId(courseId)
                .stream().filter(member -> member.getTeam().getProject() != null && member.getStudent() != null)
                .collect(Collectors.toMap(member -> member.getTeam().getProject().getId() + ":"
                        + member.getStudent().getId(), member -> member, (first, ignored) -> first));
        for (Task task : taskRepository.findByProjectCourseId(courseId)) {
            if (task.getProject() == null || task.getAssignee() == null) {
                continue;
            }
            TeamMember membership = membershipByProjectAndStudent.get(
                    task.getProject().getId() + ":" + task.getAssignee().getId());
            if (membership != null && task.getDueDate() != null && task.getDueDate().isBefore(nowUtc)
                    && task.getStatus() != TaskStatus.DONE) {
                warnings.add(new LecturerAnalyticsResponses.EarlyWarning(membership.getStudent().getId(),
                        membership.getTeam().getId(), "OVERDUE_TASK", null, nowUtc,
                        "Nhiệm vụ đã quá hạn và chưa hoàn thành", task.getId(), task.getDueDate()));
            }
        }
        return new LecturerAnalyticsResponses.EarlyWarnings(courseId, List.copyOf(warnings));
    }
}
