package com.saga.be.integration.security;

import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.RoleInTeam;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamMemberRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectIntegrationAuthorizationService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuthenticationAuditService auditService;

    public ProjectIntegrationAuthorizationService(
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            AuthenticationAuditService auditService
    ) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Project requireProjectManager(SagaPrincipal principal, UUID projectId) {
        return requireProjectAccess(principal, projectId, true);
    }

    @Transactional(readOnly = true)
    public Project requireProjectContributor(SagaPrincipal principal, UUID projectId) {
        return requireProjectAccess(principal, projectId, false);
    }

    @Transactional(readOnly = true)
    public Team requireTeamManager(SagaPrincipal principal, UUID teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> IntegrationException.invalid(
                        "TEAM_NOT_FOUND",
                        "The team does not exist"
                ));
        requireTeamAccess(principal, team, true);
        return team;
    }

    private Project requireProjectAccess(SagaPrincipal principal, UUID projectId, boolean managerOnly) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IntegrationException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "The project does not exist"
                ));
        Team team = teamRepository.findByProjectId(projectId)
                .orElseThrow(() -> IntegrationException.conflict(
                        "PROJECT_TEAM_MISSING",
                        "The project is not assigned to a team"
                ));
        requireTeamAccess(principal, team, managerOnly);
        return project;
    }

    private void requireTeamAccess(SagaPrincipal principal, Team team, boolean managerOnly) {
        if (!managerOnly) {
            if (
                principal.applicationRole() == ApplicationRole.STUDENT
                && principal.localProfileId() != null
                && teamMemberRepository.existsByTeamIdAndStudentId(
                        team.getId(),
                        principal.localProfileId()
                )
            ) {
                return;
            }
            throw IntegrationException.forbidden(
                    "Only a student member of the owning team may attach evidence to this task"
            );
        }
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            auditService.recordRequiredIntegrationEvent(
                    principal,
                    "PROJECT_INTEGRATION_ADMIN_OVERRIDE",
                    "TEAM",
                    team.getId(),
                    "AUTHORIZED",
                    null
            );
            return;
        }
        if (
            principal.applicationRole() == ApplicationRole.LECTURER
            && team.getCourse() != null
            && team.getCourse().getInstructor() != null
            && principal.localProfileId().equals(
                    team.getCourse().getInstructor().getId()
            )
        ) {
            return;
        }
        if (
            principal.applicationRole() == ApplicationRole.STUDENT
            && principal.localProfileId() != null
            && teamMemberRepository.existsByTeamIdAndStudentIdAndRoleInTeam(
                    team.getId(),
                    principal.localProfileId(),
                    RoleInTeam.LEADER
            )
        ) {
            return;
        }
        throw IntegrationException.forbidden(
                "Only the owning team leader or an authorized reviewer may manage this integration"
        );
    }
}
