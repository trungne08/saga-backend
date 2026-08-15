-- Canonical auto-review result snapshot, business warning identity, and
-- warning-email outbox. Invitation outbox is untouched.

CREATE TABLE commit_review_result (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    intent_id CHAR(36) NOT NULL,
    ai_job_id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    git_repo_id CHAR(36) NOT NULL,
    commit_id CHAR(36) NOT NULL,
    sha_hash VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    traceability_status VARCHAR(32) NOT NULL,
    message_quality VARCHAR(16) NOT NULL,
    code_quality VARCHAR(32) NOT NULL,
    inferred_function_label VARCHAR(32) NULL,
    inferred_function_confidence VARCHAR(16) NULL,
    task_alignment VARCHAR(32) NOT NULL,
    verdict_eligible BOOLEAN NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    overall_status VARCHAR(32) NOT NULL,
    schema_version VARCHAR(64) NOT NULL,
    findings_json MEDIUMTEXT NULL,
    evidence_refs_json MEDIUMTEXT NULL,
    completed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_commit_review_result PRIMARY KEY (id),
    CONSTRAINT uk_commit_review_result_intent UNIQUE (intent_id),
    CONSTRAINT uk_commit_review_result_job UNIQUE (ai_job_id),
    CONSTRAINT fk_commit_review_result_intent
        FOREIGN KEY (intent_id) REFERENCES commit_review_intent (id),
    CONSTRAINT fk_commit_review_result_repo
        FOREIGN KEY (git_repo_id) REFERENCES git_repo (id),
    CONSTRAINT fk_commit_review_result_commit
        FOREIGN KEY (commit_id) REFERENCES commit_data (id)
);

CREATE INDEX ix_commit_review_result_project_sha
    ON commit_review_result (project_id, sha_hash);

CREATE TABLE business_warning (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    warning_type VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    severity VARCHAR(32) NULL,
    team_id CHAR(36) NULL,
    project_id CHAR(36) NULL,
    sprint_id CHAR(36) NULL,
    student_id CHAR(36) NULL,
    commit_sha VARCHAR(64) NULL,
    evidence_summary VARCHAR(1000) NOT NULL,
    progress_mode VARCHAR(32) NULL,
    CONSTRAINT pk_business_warning PRIMARY KEY (id),
    CONSTRAINT uk_business_warning_event UNIQUE (event_key)
);

CREATE INDEX ix_business_warning_team_type
    ON business_warning (team_id, warning_type, created_at);
CREATE INDEX ix_business_warning_project_type
    ON business_warning (project_id, warning_type, created_at);
CREATE INDEX ix_business_warning_student_type
    ON business_warning (student_id, warning_type, created_at);

CREATE TABLE warning_email_outbox (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    notification_id CHAR(36) NOT NULL,
    recipient_profile_id CHAR(36) NOT NULL,
    recipient_role VARCHAR(32) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(160) NOT NULL,
    body_text VARCHAR(1000) NOT NULL,
    body_html VARCHAR(2000) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    processing_started_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    failure_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_warning_email_outbox PRIMARY KEY (id),
    CONSTRAINT uk_warning_email_notification UNIQUE (notification_id),
    CONSTRAINT fk_warning_email_notification
        FOREIGN KEY (notification_id) REFERENCES user_notification (id)
);

CREATE INDEX ix_warning_email_status_created
    ON warning_email_outbox (delivery_status, created_at);
