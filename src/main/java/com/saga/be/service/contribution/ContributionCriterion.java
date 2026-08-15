package com.saga.be.service.contribution;

/**
 * The four active Contribution scoring criteria. Deliberately separate from {@code TaskType}
 * (a Jira business/issue-type enum persisted as a native MySQL enum) and from
 * {@code DocumentType} — this is a Contribution-only classification, not a provider taxonomy.
 */
public enum ContributionCriterion {
    CODE,
    TEST,
    DOCUMENT,
    RESEARCH
}
