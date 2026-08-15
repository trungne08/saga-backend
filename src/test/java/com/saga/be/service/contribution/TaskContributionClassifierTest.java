package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.saga.be.entity.Task;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskContributionClassifierTest {

    @Test
    void nullTaskAndUnlabeledTaskDoNotEnterAnyCriterion() {
        assertThat(TaskContributionClassifier.classify(null)).isEmpty();

        Task unlabeled = new Task();
        unlabeled.setLabels(List.of("backend", "ui-ux"));
        assertThat(TaskContributionClassifier.classify(unlabeled)).isEmpty();
    }

    @Test
    void exactReservedMarkerRoutesToThatCriterion() {
        assertThat(TaskContributionClassifier.classify(labeled("saga:code")))
                .contains(ContributionCriterion.CODE);
        assertThat(TaskContributionClassifier.classify(labeled("saga:test")))
                .contains(ContributionCriterion.TEST);
        assertThat(TaskContributionClassifier.classify(labeled("saga:document")))
                .contains(ContributionCriterion.DOCUMENT);
        assertThat(TaskContributionClassifier.classify(labeled("saga:research")))
                .contains(ContributionCriterion.RESEARCH);
    }

    @Test
    void conflictingMarkersExcludeTheTask() {
        Task task = new Task();
        task.setLabels(List.of("saga:test", "saga:research"));
        assertThat(TaskContributionClassifier.classify(task)).isEmpty();
    }

    @Test
    void documentAndResearchRequireAtLeastOneAttachmentToRecognizeStoryPoints() {
        assertThat(TaskContributionClassifier.requiresAttachmentEvidence(ContributionCriterion.DOCUMENT)).isTrue();
        assertThat(TaskContributionClassifier.requiresAttachmentEvidence(ContributionCriterion.RESEARCH)).isTrue();
        assertThat(TaskContributionClassifier.requiresAttachmentEvidence(ContributionCriterion.CODE)).isFalse();
        assertThat(TaskContributionClassifier.requiresAttachmentEvidence(ContributionCriterion.TEST)).isFalse();

        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.DOCUMENT, 0)).isFalse();
        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.RESEARCH, 0)).isFalse();
        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.DOCUMENT, 1)).isTrue();
        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.RESEARCH, 3)).isTrue();
        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.CODE, 0)).isTrue();
        assertThat(TaskContributionClassifier.storyPointsRecognized(ContributionCriterion.TEST, 0)).isTrue();
    }

    private static Task labeled(String marker) {
        Task task = new Task();
        task.setLabels(List.of(marker));
        return task;
    }
}
