-- Safe, operator-visible diagnostics for terminal sync jobs.
ALTER TABLE sync_job_log
    ADD COLUMN error_category VARCHAR(128) NULL AFTER error_message,
    ADD COLUMN failure_stage VARCHAR(64) NULL AFTER error_category;
