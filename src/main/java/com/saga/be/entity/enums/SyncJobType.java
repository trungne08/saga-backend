package com.saga.be.entity.enums;

public enum SyncJobType {
    JIRA_SYNC,
    GIT_SYNC,
    INITIAL_BACKFILL,
    RECONCILIATION,
    WEBHOOK_PROCESSING,
    OTHER
}
