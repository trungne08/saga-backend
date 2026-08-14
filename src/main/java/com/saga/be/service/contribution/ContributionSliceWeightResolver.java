package com.saga.be.service.contribution;

import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which Code/Document/Design slice weights a contribution calculation should use:
 * an exact Project+Team {@link ProjectGroupWeightConfig} override when one exists for the
 * Team currently being calculated, otherwise the existing Course-level slice weights.
 */
@Service
public class ContributionSliceWeightResolver {

    private final ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    public ContributionSliceWeightResolver(ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository) {
        this.projectGroupWeightConfigRepository = projectGroupWeightConfigRepository;
    }

    @Transactional(readOnly = true)
    public ContributionSliceWeights resolve(UUID projectId, Team team) {
        if (projectId != null && team != null && team.getId() != null) {
            Optional<ProjectGroupWeightConfig> config = projectGroupWeightConfigRepository.findByProjectId(projectId)
                    .filter(candidate -> candidate.getTeam() != null
                            && team.getId().equals(candidate.getTeam().getId()));
            if (config.isPresent()) {
                ProjectGroupWeightConfig override = config.get();
                return ContributionSliceWeights.normalizeConfigured(
                        override.getCodeWeight(),
                        override.getDocumentWeight(),
                        override.getDesignWeight()
                );
            }
        }
        return ContributionSliceWeights.fromCourse(team == null ? null : team.getCourse());
    }
}
