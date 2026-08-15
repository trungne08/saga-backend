package com.saga.be.service.contribution;

/**
 * Outcome of matching a resource's labels against the exact reserved Contribution markers
 * (see {@link ReservedContributionMarkerClassifier}). Exactly one of three states: no reserved
 * marker was found ({@link #isNone()}, the Task does not enter any criterion),
 * exactly one reserved marker was found ({@link #isResolved()}), or more than one conflicting
 * reserved marker was found ({@code ambiguous}, the resource must not be scored into any
 * criterion until the conflict is fixed).
 */
public record ContributionMarkerClassification(ContributionCriterion criterion, boolean ambiguous) {

    public static ContributionMarkerClassification none() {
        return new ContributionMarkerClassification(null, false);
    }

    public static ContributionMarkerClassification resolved(ContributionCriterion criterion) {
        return new ContributionMarkerClassification(criterion, false);
    }

    public static ContributionMarkerClassification conflicting() {
        return new ContributionMarkerClassification(null, true);
    }

    public boolean isResolved() {
        return criterion != null;
    }

    public boolean isNone() {
        return !ambiguous && criterion == null;
    }
}
