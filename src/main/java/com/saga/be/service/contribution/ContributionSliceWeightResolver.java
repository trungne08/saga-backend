package com.saga.be.service.contribution;

import com.saga.be.entity.ProjectGroupWeightConfig;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.ContributionConfigMode;
import com.saga.be.exception.IntegrationException;
import com.saga.be.repository.ProjectGroupWeightConfigRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the Code/Test/Document/Research slice weights a Contribution calculation uses.
 * Each Course has exactly one active {@link ContributionConfigMode}:
 * <ul>
 *   <li>{@code COURSE}: every Team in the Course uses the Course-level weights.</li>
 *   <li>{@code TEAM}: every Team must have its own exact Project+Team
 *       {@link ProjectGroupWeightConfig}; there is no fallback to Course weights — a missing
 *       Team override fails closed instead of silently mixing modes.</li>
 * </ul>
 */
@Service
public class ContributionSliceWeightResolver {

    private final ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository;

    public ContributionSliceWeightResolver(ProjectGroupWeightConfigRepository projectGroupWeightConfigRepository) {
        this.projectGroupWeightConfigRepository = projectGroupWeightConfigRepository;
    }

    @Transactional(readOnly = true)
    public ContributionSliceWeights resolve(Team team) {
        if (team == null || team.getCourse() == null) {
            return ContributionSliceWeights.fromCourse(null);
        }
        ContributionConfigMode mode = team.getCourse().getContributionConfigMode();
        if (mode == ContributionConfigMode.TEAM) {
            return teamWeights(team);
        }
        return ContributionSliceWeights.fromCourse(team.getCourse());
    }

    private ContributionSliceWeights teamWeights(Team team) {
        Optional<ProjectGroupWeightConfig> config = team.getProject() == null
                ? Optional.empty()
                : projectGroupWeightConfigRepository.findByProjectId(team.getProject().getId())
                        .filter(candidate -> candidate.getTeam() != null
                                && team.getId().equals(candidate.getTeam().getId()));
        ProjectGroupWeightConfig override = config.orElseThrow(() -> IntegrationException.conflict(
                "TEAM_WEIGHT_CONFIG_INCOMPLETE",
                "This Course is in TEAM contribution configuration mode but this Team has no weight override"
        ));
        return ContributionSliceWeights.normalizeConfigured(
                override.getCodeWeight(),
                override.getTestWeight(),
                override.getDocumentWeight(),
                override.getResearchWeight()
        );
    }
}
