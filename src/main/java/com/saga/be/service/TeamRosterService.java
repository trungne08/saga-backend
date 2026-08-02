package com.saga.be.service;

import com.saga.be.dto.response.TeamMemberResponse;
import com.saga.be.entity.Team;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Objects;
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

    private ResponseStatusException teamNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found for course");
    }
}
