-- Local SAGA traceability. Legacy pull_request.git_issue_id and
-- commit_data.git_issue_id remain in place for compatibility, but the
-- normalized link tables below are the traceability read source.

CREATE TABLE task_git_issue_link (
    id CHAR(36) NOT NULL,
    task_id CHAR(36) NOT NULL,
    git_issue_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_git_issue_link_pair UNIQUE (task_id, git_issue_id),
    CONSTRAINT fk_task_git_issue_link_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_git_issue_link_issue
        FOREIGN KEY (git_issue_id) REFERENCES git_issue(id)
);

CREATE INDEX ix_task_git_issue_link_issue
    ON task_git_issue_link (git_issue_id);

CREATE TABLE git_issue_pull_request_link (
    id CHAR(36) NOT NULL,
    git_issue_id CHAR(36) NOT NULL,
    pull_request_id CHAR(36) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_git_issue_pull_request_link_pair
        UNIQUE (git_issue_id, pull_request_id),
    CONSTRAINT fk_git_issue_pull_request_link_issue
        FOREIGN KEY (git_issue_id) REFERENCES git_issue(id),
    CONSTRAINT fk_git_issue_pull_request_link_pull
        FOREIGN KEY (pull_request_id) REFERENCES pull_request(id)
);

CREATE INDEX ix_git_issue_pull_request_link_pull
    ON git_issue_pull_request_link (pull_request_id);

CREATE TABLE git_issue_commit_link (
    id CHAR(36) NOT NULL,
    git_issue_id CHAR(36) NOT NULL,
    commit_id CHAR(36) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_git_issue_commit_link_pair UNIQUE (git_issue_id, commit_id),
    CONSTRAINT fk_git_issue_commit_link_issue
        FOREIGN KEY (git_issue_id) REFERENCES git_issue(id),
    CONSTRAINT fk_git_issue_commit_link_commit
        FOREIGN KEY (commit_id) REFERENCES commit_data(id)
);

CREATE INDEX ix_git_issue_commit_link_commit
    ON git_issue_commit_link (commit_id);
