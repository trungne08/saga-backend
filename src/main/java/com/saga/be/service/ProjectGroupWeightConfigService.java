package com.saga.be.service;

import com.saga.be.dto.request.ProjectGroupWeightConfigRequest;
import com.saga.be.dto.response.ProjectGroupWeightConfigResponse;
import com.saga.be.entity.Project;
import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import com.saga.be.repository.ProjectRepository;
import com.saga.be.repository.TeamRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectGroupWeightConfigService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final ProjectGroupWeightConfigRepository configRepository;

    public ProjectGroupWeightConfigService(
            ProjectRepository projectRepository,
            TeamRepository teamRepository,
            ProjectGroupWeightConfigRepository configRepository
    ) {
        this.projectRepository = projectRepository;
        this.teamRepository = teamRepository;
        this.configRepository = configRepository;
    }

    @Transactional
    public ProjectGroupWeightConfigResponse update(
            SagaPrincipal principal,
            UUID projectId,
            ProjectGroupWeightConfigRequest request
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IntegrationException(
                        HttpStatus.NOT_FOUND,
                        "PROJECT_NOT_FOUND",
                        "The project does not exist"
                ));
        Team team = teamRepository.findWithCourseAndInstructorByProjectId(projectId)
                .orElseThrow(() -> IntegrationException.conflict(
                        "PROJECT_TEAM_MISSING",
                        "The project is not assigned to a team"
                ));

        requireLecturerOwnerOrAdmin(principal, team);

        if (request.groupId() == null) {
            throw IntegrationException.invalid("GROUP_ID_REQUIRED", "groupId is required");
        }
        if (!team.getId().equals(request.groupId())) {
            throw IntegrationException.invalid(
                    "GROUP_PROJECT_MISMATCH",
                    "The group does not own this project"
            );
        }

        BigDecimal codeWeight = requireWeightInRange(request.codeWeight(), "codeWeight");
        BigDecimal documentWeight = requireWeightInRange(request.documentWeight(), "documentWeight");
        BigDecimal designWeight = requireWeightInRange(request.designWeight(), "designWeight");
        BigDecimal total = codeWeight.add(documentWeight).add(designWeight);
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw IntegrationException.invalid(
                    "GROUP_WEIGHT_SUM_INVALID",
                    "codeWeight, documentWeight and designWeight must add up to exactly 1.0"
            );
        }

        ProjectGroupWeightConfig config = configRepository.findByProjectId(projectId)
                .orElseGet(() -> ProjectGroupWeightConfig.builder()
                        .project(project)
                        .build());
        config.setTeam(team);
        config.setCodeWeight(codeWeight);
        config.setDocumentWeight(documentWeight);
        config.setDesignWeight(designWeight);
        config.setNote(normalizeNote(request.note()));
        config.setUpdatedByProfileId(principal.localProfileId());

        config = configRepository.save(config);
        return ProjectGroupWeightConfigResponse.from(config);
    }

    private void requireLecturerOwnerOrAdmin(SagaPrincipal principal, Team team) {
        if (principal.applicationRole() == ApplicationRole.ADMIN) {
            return;
        }
        if (principal.applicationRole() == ApplicationRole.LECTURER
                && team.getCourse() != null
                && team.getCourse().getInstructor() != null
                && Objects.equals(principal.localProfileId(), team.getCourse().getInstructor().getId())) {
            return;
        }
        throw IntegrationException.forbidden(
                "Only the course instructor or an authorized administrator may manage group weight configuration"
        );
    }

    private BigDecimal requireWeightInRange(BigDecimal value, String fieldName) {
        if (value == null) {
            throw IntegrationException.invalid("GROUP_WEIGHT_REQUIRED", fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw IntegrationException.invalid(
                    "GROUP_WEIGHT_OUT_OF_RANGE",
                    fieldName + " must be between 0 and 1"
            );
        }
        return value;
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
