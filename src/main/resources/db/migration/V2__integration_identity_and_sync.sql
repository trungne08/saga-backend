-- SAGA integration module. Version 1 is reserved for the baseline representing
-- the pre-Flyway legacy schema. Run the documented read-only preflight before
-- applying this V2 migration.

ALTER TABLE identity_map
    ADD COLUMN external_account_id VARCHAR(255) NULL AFTER provider,
    ADD COLUMN external_email VARCHAR(255) NULL AFTER external_username,
    ADD COLUMN mapping_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    ADD COLUMN verified_at DATETIME(6) NULL,
    ADD COLUMN disconnected_at DATETIME(6) NULL,
    ADD COLUMN reviewed_by_cognito_sub VARCHAR(255) NULL,
    ADD COLUMN reviewed_at DATETIME(6) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_identity_student_provider UNIQUE (student_id, provider),
    ADD CONSTRAINT uk_identity_provider_external
        UNIQUE (provider, external_account_id);

UPDATE identity_map
SET provider = UPPER(TRIM(provider))
WHERE provider IS NOT NULL;

ALTER TABLE identity_map
    MODIFY COLUMN provider VARCHAR(32) NOT NULL;

CREATE TABLE identity_mapping_history (
    id CHAR(36) NOT NULL,
    identity_map_id CHAR(36) NOT NULL,
    student_id CHAR(36) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_cognito_sub VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_identity_history_map
        FOREIGN KEY (identity_map_id) REFERENCES identity_map(id),
    CONSTRAINT fk_identity_history_student
        FOREIGN KEY (student_id) REFERENCES student(id),
    INDEX ix_identity_history_lookup
        (provider, external_account_id, occurred_at)
);

ALTER TABLE project
    ADD COLUMN created_by_cognito_sub VARCHAR(255) NULL;

ALTER TABLE jira_board
    ADD COLUMN cloud_id VARCHAR(255) NULL,
    ADD COLUMN site_url VARCHAR(512) NULL,
    ADD COLUMN jira_project_id VARCHAR(255) NULL,
    ADD COLUMN project_key VARCHAR(128) NULL,
    ADD COLUMN encrypted_access_token TEXT NULL,
    ADD COLUMN encrypted_refresh_token TEXT NULL,
    ADD COLUMN token_expires_at DATETIME(6) NULL,
    ADD COLUMN granted_scopes TEXT NULL,
    ADD COLUMN connection_status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    ADD COLUMN connected_by_cognito_sub VARCHAR(255) NULL,
    ADD COLUMN connected_by_student_id CHAR(36) NULL,
    ADD COLUMN webhook_id VARCHAR(255) NULL,
    ADD COLUMN webhook_expires_at DATETIME(6) NULL,
    ADD COLUMN webhook_secret_hash CHAR(64) NULL,
    ADD COLUMN sync_cursor DATETIME(6) NULL,
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_jira_board_project UNIQUE (project_id),
    ADD CONSTRAINT uk_jira_cloud_project UNIQUE (cloud_id, jira_project_id),
    ADD CONSTRAINT fk_jira_connector_student
        FOREIGN KEY (connected_by_student_id) REFERENCES student(id);

CREATE INDEX ix_jira_board_reconciliation
    ON jira_board (connection_status, webhook_expires_at);
CREATE INDEX ix_jira_board_webhook_secret
    ON jira_board (webhook_secret_hash);

CREATE TABLE github_installation (
    id CHAR(36) NOT NULL,
    installation_id BIGINT NOT NULL,
    installed_by_cognito_sub VARCHAR(255) NOT NULL,
    installed_by_student_id CHAR(36) NULL,
    account_login VARCHAR(255) NULL,
    account_type VARCHAR(64) NULL,
    installation_status VARCHAR(32) NOT NULL,
    last_verified_at DATETIME(6) NULL,
    consecutive_failures INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_github_installation_id UNIQUE (installation_id),
    CONSTRAINT fk_github_installer_student
        FOREIGN KEY (installed_by_student_id) REFERENCES student(id)
);

CREATE INDEX ix_github_installation_status
    ON github_installation (installation_status);

ALTER TABLE git_repo
    ADD COLUMN repository_id BIGINT NULL,
    ADD COLUMN owner_login VARCHAR(255) NULL,
    ADD COLUMN full_name VARCHAR(512) NULL,
    ADD COLUMN default_branch VARCHAR(255) NULL,
    ADD COLUMN installation_id CHAR(36) NULL,
    ADD COLUMN connection_status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    ADD COLUMN sync_cursor DATETIME(6) NULL,
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_git_repo_provider_id UNIQUE (provider, repository_id),
    ADD CONSTRAINT uk_git_repo_project_full_name UNIQUE (project_id, full_name),
    ADD CONSTRAINT fk_git_repo_installation
        FOREIGN KEY (installation_id) REFERENCES github_installation(id);

CREATE INDEX ix_git_repo_reconciliation
    ON git_repo (connection_status);
CREATE INDEX ix_git_repo_installation
    ON git_repo (installation_id);

ALTER TABLE task
    ADD COLUMN external_id VARCHAR(255) NULL,
    ADD COLUMN assignee_external_id VARCHAR(255) NULL,
    ADD COLUMN reporter_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD COLUMN resolved_at DATETIME(6) NULL,
    ADD COLUMN resolution VARCHAR(255) NULL,
    ADD CONSTRAINT uk_task_project_external UNIQUE (project_id, external_id);

ALTER TABLE sprint
    ADD COLUMN external_sprint_id VARCHAR(255) NULL,
    ADD CONSTRAINT uk_sprint_board_external
        UNIQUE (board_id, external_sprint_id);

ALTER TABLE git_issue
    ADD COLUMN github_issue_id BIGINT NULL,
    ADD COLUMN node_id VARCHAR(255) NULL,
    ADD COLUMN author_external_id VARCHAR(255) NULL,
    ADD COLUMN assignee_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_git_issue_repo_external
        UNIQUE (repo_id, github_issue_id);

ALTER TABLE commit_data
    ADD COLUMN github_commit_id VARCHAR(255) NULL,
    ADD COLUMN author_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_commit_repo_sha UNIQUE (repo_id, sha_hash);

ALTER TABLE pull_request
    ADD COLUMN github_pull_request_id BIGINT NULL,
    ADD COLUMN node_id VARCHAR(255) NULL,
    ADD COLUMN pull_number INT NULL,
    ADD COLUMN author_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_pull_repo_external
        UNIQUE (repo_id, github_pull_request_id);

ALTER TABLE pr_review
    ADD COLUMN github_review_id BIGINT NULL,
    ADD COLUMN reviewer_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_pr_review_external UNIQUE (github_review_id);

ALTER TABLE `comment`
    ADD COLUMN external_comment_id VARCHAR(255) NULL,
    ADD COLUMN author_external_id VARCHAR(255) NULL,
    ADD COLUMN external_updated_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_comment_source_external
        UNIQUE (source_system, external_comment_id);

ALTER TABLE sync_job_log
    ADD COLUMN items_processed INT NULL,
    ADD COLUMN items_failed INT NULL,
    ADD COLUMN cursor_before DATETIME(6) NULL,
    ADD COLUMN cursor_after DATETIME(6) NULL;

CREATE INDEX ix_sync_job_target_started
    ON sync_job_log (target_id, started_at);

CREATE TABLE webhook_receipt (
    id CHAR(36) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    delivery_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    target_id CHAR(36) NULL,
    payload_ciphertext LONGTEXT NOT NULL,
    receipt_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    processed_at DATETIME(6) NULL,
    error_category VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_webhook_provider_delivery
        UNIQUE (provider, delivery_id)
);

CREATE INDEX ix_webhook_receipt_retry
    ON webhook_receipt (receipt_status, created_at);
