package com.saga.be.service;

import com.saga.be.dto.request.ProjectUpdateRequest;
import com.saga.be.dto.response.ProjectDetailResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDetailService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectIntegrationAuthorizationService managerAuthorization;

    public ProjectDetailService(
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            ProjectIntegrationAuthorizationService managerAuthorization
    ) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.managerAuthorization = managerAuthorization;
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse get(SagaPrincipal principal, UUID projectId) {
        Project project = projectRepository.findWithCourseAndInstructorById(projectId)
                .orElseThrow(() -> new IntegrationException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "The project does not exist"
                ));
        Team team = requireConsistentOwningTeam(project);
        requireProjectReadAccess(principal, project, team);
        return ProjectDetailResponse.from(project, team);
    }

    @Transactional
    public ProjectDetailResponse update(
            SagaPrincipal principal,
            UUID projectId,
            ProjectUpdateRequest request
    ) {
        Project project = managerAuthorization.requireProjectManager(principal, projectId);
        Team team = requireConsistentOwningTeam(project);
        project.setName(request.name().trim());
        return ProjectDetailResponse.from(project, team);
    }

    private Team requireConsistentOwningTeam(Project project) {
        Team team = teamRepository.findWithCourseAndInstructorByProjectId(project.getId())
                .orElseThrow(() -> IntegrationException.conflict(
                        "PROJECT_TEAM_MISSING",
                        "The project is not assigned to a team"
                ));
        if (project.getCourse() == null
                || team.getCourse() == null
                || !Objects.equals(project.getCourse().getId(), team.getCourse().getId())) {
            throw IntegrationException.conflict(
                    "PROJECT_TEAM_COURSE_MISMATCH",
                    "The project and owning team have an inconsistent course association"
            );
        }
        return team;
    }

    private void requireProjectReadAccess(
            SagaPrincipal principal,
            Project project,
            Team owningTeam
    ) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && project.getCourse().getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        project.getCourse().getInstructor().getId()
                )) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.STUDENT
                && principal.localProfileId() != null
                && teamMemberRepository.existsByTeamIdAndStudentId(
                        owningTeam.getId(),
                        principal.localProfileId()
                )) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this project");
    }
}
