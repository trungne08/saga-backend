package com.saga.be.service.contribution;

import com.saga.be.entity.Task;
import java.util.Optional;

/**
 * Labels are the only authority that routes a Jira Task into a Contribution criterion.
 * Exact reserved markers {@code saga:code}/{@code saga:test}/{@code saga:document}/{@code saga:research}
 * on {@link Task#getLabels()} decide the bucket. No keyword/title/type fallback.
 * DOCUMENT and RESEARCH story points count only when the Task has at least one Jira file
 * attachment or one submitted web link; that evidence is a recognition gate, not extra score.
 */
public final class TaskContributionClassifier {

    private TaskContributionClassifier() {
    }

    public static Optional<ContributionCriterion> classify(Task task) {
        if (task == null) {
            return Optional.empty();
        }
        ContributionMarkerClassification marker = ReservedContributionMarkerClassifier.classify(task.getLabels());
        if (!marker.isResolved()) {
            return Optional.empty();
        }
        return Optional.of(marker.criterion());
    }

    public static boolean requiresAttachmentEvidence(ContributionCriterion criterion) {
        return criterion == ContributionCriterion.DOCUMENT || criterion == ContributionCriterion.RESEARCH;
    }

    public static boolean storyPointsRecognized(ContributionCriterion criterion, int evidenceCount) {
        if (criterion == null) {
            return false;
        }
        if (!requiresAttachmentEvidence(criterion)) {
            return true;
        }
        return evidenceCount > 0;
    }
}
