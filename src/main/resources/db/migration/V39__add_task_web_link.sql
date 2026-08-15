CREATE TABLE task_web_link (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(255) NULL,
    remote_link_id VARCHAR(64) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_web_link_task
        FOREIGN KEY (task_id) REFERENCES task(id)
        ON DELETE CASCADE
);

CREATE INDEX ix_task_web_link_task ON task_web_link (task_id);
