package com.saga.be.service;

import com.saga.be.dto.response.LecturerAnalyticsResponses;
import com.saga.be.dto.response.TeamContributionEvaluationResponse;
import com.saga.be.dto.response.TeamContributionMemberResponse;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.security.SagaPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LecturerContributionQueryService {
    private final LecturerAnalyticsAuthorizationService authorization;
    private final TeamContributionService teamContributionService;

    @Transactional(readOnly = true)
    public LecturerAnalyticsResponses.StudentContributionDetail get(SagaPrincipal principal, UUID courseId,
            UUID studentId) {
        TeamMember membership = authorization.requireStudentInCourse(principal, courseId, studentId);
        Team team = membership.getTeam();
        TeamContributionEvaluationResponse evaluation = teamContributionService.evaluate(team.getId());
        TeamContributionMemberResponse member = evaluation.members().stream()
                .filter(item -> studentId.equals(item.studentId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student contribution not found"));
        return new LecturerAnalyticsResponses.StudentContributionDetail(courseId, studentId, team.getId(),
                team.getProject() == null ? null : team.getProject().getId(), member);
    }
}
