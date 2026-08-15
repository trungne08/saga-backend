package com.saga.be.service.contribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReservedContributionMarkerClassifierTest {

    @Test
    void exactSagaCodeResolvesToCode() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(List.of("saga:code"));

        assertThat(result.isResolved()).isTrue();
        assertThat(result.criterion()).isEqualTo(ContributionCriterion.CODE);
        assertThat(result.ambiguous()).isFalse();
    }

    @Test
    void exactSagaTestResolvesToTest() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(List.of("saga:test"));

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.TEST);
    }

    @Test
    void exactSagaDocumentResolvesToDocument() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(List.of("saga:document"));

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.DOCUMENT);
    }

    @Test
    void exactSagaResearchResolvesToResearch() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(List.of("saga:research"));

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.RESEARCH);
    }

    @Test
    void markerIsCaseSensitiveAndNotSubstringMatched() {
        ContributionMarkerClassification upperCase = ReservedContributionMarkerClassifier.classify(List.of("SAGA:TEST"));
        ContributionMarkerClassification substring = ReservedContributionMarkerClassifier.classify(List.of("saga:test-extra"));
        ContributionMarkerClassification prefixed = ReservedContributionMarkerClassifier.classify(List.of("not-saga:test"));

        assertThat(upperCase.isNone()).isTrue();
        assertThat(substring.isNone()).isTrue();
        assertThat(prefixed.isNone()).isTrue();
    }

    @Test
    void surroundingWhitespaceIsTrimmedButNoOtherNormalizationApplied() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(List.of("  saga:test  "));

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.TEST);
    }

    @Test
    void noReservedMarkerReturnsNone() {
        ContributionMarkerClassification noLabels = ReservedContributionMarkerClassifier.classify(List.of());
        ContributionMarkerClassification nullLabels = ReservedContributionMarkerClassifier.classify(null);
        ContributionMarkerClassification unrelatedLabels = ReservedContributionMarkerClassifier.classify(List.of("backend", "sprint-3"));

        assertThat(noLabels.isNone()).isTrue();
        assertThat(nullLabels.isNone()).isTrue();
        assertThat(unrelatedLabels.isNone()).isTrue();
    }

    @Test
    void conflictingReservedMarkersAreAmbiguousNotPickFirst() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(
                List.of("saga:test", "saga:research")
        );

        assertThat(result.ambiguous()).isTrue();
        assertThat(result.isResolved()).isFalse();
        assertThat(result.criterion()).isNull();
    }

    @Test
    void unrelatedBusinessLabelsDoNotCauseConflict() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(
                List.of("saga:test", "backend", "sprint-3", "priority-high")
        );

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.TEST);
        assertThat(result.ambiguous()).isFalse();
    }

    @Test
    void duplicateSameMarkerIsNotAmbiguous() {
        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(
                List.of("saga:test", "saga:test")
        );

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.TEST);
        assertThat(result.ambiguous()).isFalse();
    }

    @Test
    void nullLabelEntryIsIgnored() {
        List<String> labels = new java.util.ArrayList<>();
        labels.add(null);
        labels.add("saga:document");

        ContributionMarkerClassification result = ReservedContributionMarkerClassifier.classify(labels);

        assertThat(result.criterion()).isEqualTo(ContributionCriterion.DOCUMENT);
    }
}
