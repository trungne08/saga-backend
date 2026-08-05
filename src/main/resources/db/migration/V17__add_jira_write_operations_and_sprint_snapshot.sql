CREATE TABLE jira_write_operation (
    id CHAR(36) NOT NULL,
    project_id CHAR(36) NOT NULL,
    actor_profile_id CHAR(36) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    remote_resource_id VARCHAR(255) NULL,
    remote_resource_key VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    safe_error_code VARCHAR(64) NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_jira_write_operation_project_key UNIQUE (project_id, idempotency_key),
    CONSTRAINT fk_jira_write_operation_project
        FOREIGN KEY (project_id) REFERENCES project(id)
);

CREATE INDEX ix_jira_write_operation_recovery
    ON jira_write_operation (status, updated_at);

ALTER TABLE sprint
    ADD COLUMN state VARCHAR(16) NULL,
    ADD COLUMN complete_date DATETIME(6) NULL;

ALTER TABLE task
    ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE sprint
    ADD COLUMN deleted_at DATETIME(6) NULL;
