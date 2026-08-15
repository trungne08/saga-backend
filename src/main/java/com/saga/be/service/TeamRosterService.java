package com.saga.be.service;

import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.dto.response.MyCourseTeamMembersResponse;
import com.saga.be.entity.Team;
import com.saga.be.entity.TeamMember;
import com.saga.be.exception.IdentityConflictException;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TeamRosterService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public Page<TeamMemberResponse> getMembers(
            SagaPrincipal principal,
            UUID courseId,
            UUID teamId,
            Pageable pageable
    ) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(this::teamNotFound);
        if (team.getCourse() == null || !courseId.equals(team.getCourse().getId())) {
            throw teamNotFound();
        }

        requireRosterAccess(principal, team);
        return memberPage(teamId, pageable);
    }

    @Transactional(readOnly = true)
    public MyCourseTeamMembersResponse getCurrentStudentTeamMembers(
            SagaPrincipal principal,
            UUID courseId,
            Pageable pageable
    ) {
        requireStudent(principal);
        if (!courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }

        List<TeamMember> memberships = teamMemberRepository
                .findByStudentIdAndTeamCourseId(principal.localProfileId(), courseId);
        if (memberships.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student has no team in this course");
        }
        if (memberships.size() > 1) {
            throw new IdentityConflictException(
                    "Student has multiple team memberships in this course"
            );
        }

        TeamMember membership = memberships.get(0);
        return MyCourseTeamMembersResponse.from(
                courseId,
                membership,
                memberPage(membership.getTeam().getId(), pageable)
        );
    }

    private Page<TeamMemberResponse> memberPage(UUID teamId, Pageable pageable) {
        return teamMemberRepository.findByTeamId(teamId, pageable)
                .map(TeamMemberResponse::from);
    }

    private void requireRosterAccess(SagaPrincipal principal, Team team) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && team.getCourse().getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        team.getCourse().getInstructor().getId()
                )) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && teamMemberRepository.existsByTeamIdAndStudentId(
                        team.getId(),
                        principal.localProfileId()
                )) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this team roster");
    }

    private void requireStudent(SagaPrincipal principal) {
        if (principal.applicationRole() != ApplicationRole.STUDENT || principal.localProfileId() == null) {
            throw new AccessDeniedException("Only a Student profile may access its course team");
        }
    }

    private ResponseStatusException teamNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found for course");
    }
}
