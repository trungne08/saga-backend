-- Keep the legacy native ENUM columns aligned with their Java enums.
-- MODIFY COLUMN retains existing rows while allowing the newer sync outcomes.
ALTER TABLE sync_job_log
    MODIFY COLUMN job_type ENUM(
        'JIRA_SYNC',
        'GIT_SYNC',
        'INITIAL_BACKFILL',
        'RECONCILIATION',
        'WEBHOOK_PROCESSING',
        'OTHER'
    ) NULL,
    MODIFY COLUMN status ENUM(
        'PENDING',
        'IN_PROGRESS',
        'COMPLETED',
        'PARTIAL_FAILURE',
        'FAILED'
    ) NULL;
