package com.saga.be.integration.project;

import com.saga.be.dto.request.CreateTeamProjectRequest;
import com.saga.be.dto.response.ProjectResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.Team;
import com.saga.be.exception.IntegrationException;
import com.saga.be.integration.security.ProjectIntegrationAuthorizationService;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.SagaPrincipal;
import com.saga.be.service.AuthenticationAuditService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamProjectService {

    private final ProjectIntegrationAuthorizationService authorization;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final AuthenticationAuditService auditService;

    public TeamProjectService(
            ProjectIntegrationAuthorizationService authorization,
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            AuthenticationAuditService auditService
    ) {
        this.authorization = authorization;
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ProjectResponse create(
            SagaPrincipal principal,
            UUID teamId,
            CreateTeamProjectRequest request,
            String remoteAddress
    ) {
        Team team = authorization.requireTeamManager(principal, teamId);
        if (team.getProject() != null) {
            throw IntegrationException.conflict(
                    "TEAM_PROJECT_ALREADY_EXISTS",
                    "The team already owns a SAGA Project"
            );
        }

        Project project = Project.builder()
                .course(team.getCourse())
                .name(request.name().trim())
                .createdByCognitoSub(principal.cognitoSub())
                .build();
        project = projectRepository.saveAndFlush(project);
        team.setProject(project);
        teamRepository.saveAndFlush(team);
        auditService.recordIntegrationEvent(
                principal.cognitoSub(),
                "TEAM_PROJECT_CREATED",
                "PROJECT",
                project.getId(),
                "SUCCESS",
                remoteAddress
        );
        return ProjectResponse.from(project, teamId);
    }
}
