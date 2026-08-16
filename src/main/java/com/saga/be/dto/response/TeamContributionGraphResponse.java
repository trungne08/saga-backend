package com.saga.be.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Flowchart payload for the contribution graph. Arithmetic is the SAGA mixer
 * (CODE/TEST/DOCUMENT/RESEARCH weights as ratios, peer is team-star share).
 */
public record TeamContributionGraphResponse(
        UUID teamId,
        UUID projectId,
        LocalDateTime evaluatedAt,
        String formula,
        ContributionGraphWeights weights,
        UUID sprintId,
        String sprintName,
        List<ContributionGraphNode> nodes,
        List<ContributionGraphEdge> edges
) {
    public static final String FORMULA =
            "slice = Σ(SP_criterion × weightRatio); P = stars_i / teamStars; "
                    + "pct = (slice × P) / Σadjust × 100";

    public TeamContributionGraphResponse {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public record ContributionGraphWeights(
            double codeWeightRatio,
            double testWeightRatio,
            double documentWeightRatio,
            double researchWeightRatio,
            double codeWeightPercent,
            double testWeightPercent,
            double documentWeightPercent,
            double researchWeightPercent
    ) {
    }

    public record ContributionGraphNode(
            String id,
            String kind,
            String criterion,
            Double weightRatio,
            Double weightPercent,
            UUID studentId,
            String fullName,
            String studentCode,
            String roleInTeam,
            Double sliceScore,
            Double peerCoefficient,
            Double adjustedScore,
            Double finalContributionPercentage,
            List<ContributionWarning> warnings
    ) {
        public ContributionGraphNode {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record ContributionGraphEdge(
            String id,
            String source,
            String target,
            String criterion,
            double storyPoints,
            double weightedSlice,
            List<ContributionGraphTask> tasks
    ) {
        public ContributionGraphEdge {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    public record ContributionGraphTask(
            UUID taskId,
            String title,
            String externalKey,
            UUID sprintId,
            String sprintName,
            double storyPoints
    ) {
    }
}
