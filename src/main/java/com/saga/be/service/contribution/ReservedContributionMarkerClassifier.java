package com.saga.be.service.contribution;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Exact-match reserved Contribution markers: {@code saga:code}, {@code saga:test},
 * {@code saga:document}, {@code saga:research}. No substring matching, no fuzzy matching, no
 * AI/content inference — a label must equal one of these strings exactly (after trimming
 * surrounding whitespace; no case-folding or other normalization is applied, since no existing
 * deterministic label-normalization convention exists in source to reuse and inventing one would
 * go beyond "exact match"). More than one conflicting marker on the same resource is
 * {@link ContributionMarkerClassification#conflicting()} — it is never resolved by picking one.
 */
public final class ReservedContributionMarkerClassifier {

    private ReservedContributionMarkerClassifier() {
    }

    public static ContributionMarkerClassification classify(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return ContributionMarkerClassification.none();
        }
        Set<ContributionCriterion> matched = EnumSet.noneOf(ContributionCriterion.class);
        for (String label : labels) {
            ContributionCriterion criterion = markerCriterion(label);
            if (criterion != null) {
                matched.add(criterion);
            }
        }
        if (matched.isEmpty()) {
            return ContributionMarkerClassification.none();
        }
        if (matched.size() > 1) {
            return ContributionMarkerClassification.conflicting();
        }
        return ContributionMarkerClassification.resolved(matched.iterator().next());
    }

    private static ContributionCriterion markerCriterion(String label) {
        if (label == null) {
            return null;
        }
        return switch (label.trim()) {
            case "saga:code" -> ContributionCriterion.CODE;
            case "saga:test" -> ContributionCriterion.TEST;
            case "saga:document" -> ContributionCriterion.DOCUMENT;
            case "saga:research" -> ContributionCriterion.RESEARCH;
            default -> null;
        };
    }
}
