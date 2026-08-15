CREATE TABLE task_attachment (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    external_id VARCHAR(64) NOT NULL,
    filename VARCHAR(512) NULL,
    mime_type VARCHAR(255) NULL,
    size_bytes BIGINT NULL,
    author_external_id VARCHAR(128) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_attachment_external UNIQUE (task_id, external_id),
    CONSTRAINT fk_task_attachment_task
        FOREIGN KEY (task_id) REFERENCES task(id)
        ON DELETE CASCADE
);

CREATE INDEX ix_task_attachment_task ON task_attachment (task_id);
