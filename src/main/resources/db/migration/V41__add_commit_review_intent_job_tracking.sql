ALTER TABLE commit_review_intent
    ADD COLUMN ai_job_id CHAR(36) NULL,
    ADD COLUMN review_policy_version VARCHAR(64) NULL,
    ADD COLUMN last_job_status VARCHAR(32) NULL,
    ADD COLUMN started_at DATETIME(6) NULL,
    ADD COLUMN completed_at DATETIME(6) NULL,
    ADD COLUMN safe_error_code VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_commit_review_intent_ai_job
    ON commit_review_intent (ai_job_id);
