package com.saga.be.service;

import com.saga.be.dto.response.SprintListResponse;
import com.saga.be.dto.response.SprintSummaryResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.SprintRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectSprintService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final SprintRepository sprintRepository;

    @Transactional(readOnly = true)
    public SprintListResponse getByProject(SagaPrincipal principal, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        requireProjectAccess(principal, project);
        return SprintListResponse.from(
                projectId,
                resolveTeamId(projectId),
                sprintRepository.findByBoardProjectIdOrderByStartDateAsc(projectId)
                        .stream()
                        .filter(sprint -> sprint.getDeletedAt() == null)
                        .map(SprintSummaryResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public SprintListResponse getByTeam(SagaPrincipal principal, UUID teamId) {
        Team team = teamRepository.findWithCourseAndInstructorById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (team.getProject() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team has no project");
        }
        requireTeamAccess(principal, team);
        return SprintListResponse.from(
                team.getProject().getId(),
                teamId,
                sprintRepository.findByBoardProjectIdOrderByStartDateAsc(team.getProject().getId())
                        .stream()
                        .filter(sprint -> sprint.getDeletedAt() == null)
                        .map(SprintSummaryResponse::from)
                        .toList()
        );
    }

    private UUID resolveTeamId(UUID projectId) {
        return teamRepository.findByProjectId(projectId)
                .map(Team::getId)
                .orElse(null);
    }

    private void requireProjectAccess(SagaPrincipal principal, Project project) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && project.getCourse() != null
                && project.getCourse().getInstructor() != null
                && Objects.equals(principal.localProfileId(), project.getCourse().getInstructor().getId())) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && principal.localProfileId() != null) {
            Team owningTeam = teamRepository.findByProjectId(project.getId()).orElse(null);
            if (owningTeam != null && teamMemberRepository.existsByTeamIdAndStudentId(
                    owningTeam.getId(),
                    principal.localProfileId()
            )) {
                return;
            }
        }
        throw new AccessDeniedException("You do not have access to this project sprint list");
    }

    private void requireTeamAccess(SagaPrincipal principal, Team team) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (team.getCourse() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team course not found");
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && team.getCourse().getInstructor() != null
                && Objects.equals(principal.localProfileId(), team.getCourse().getInstructor().getId())) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && principal.localProfileId() != null
                && teamMemberRepository.existsByTeamIdAndStudentId(team.getId(), principal.localProfileId())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this team sprint list");
    }
}
