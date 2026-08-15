ALTER TABLE git_repo
    ADD COLUMN review_cutover_at DATETIME(6) NULL;

UPDATE git_repo
    SET review_cutover_at = created_at
    WHERE review_cutover_at IS NULL
      AND created_at IS NOT NULL;

CREATE TABLE commit_review_intent (
    id CHAR(36) NOT NULL,
    git_repo_id CHAR(36) NOT NULL,
    commit_id CHAR(36) NOT NULL,
    sha_hash VARCHAR(64) NOT NULL,
    review_mode VARCHAR(32) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    priority_rank INT NOT NULL,
    intent_status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_commit_review_intent_repo_sha UNIQUE (git_repo_id, sha_hash),
    CONSTRAINT fk_commit_review_intent_repo
        FOREIGN KEY (git_repo_id) REFERENCES git_repo(id),
    CONSTRAINT fk_commit_review_intent_commit
        FOREIGN KEY (commit_id) REFERENCES commit_data(id)
);

CREATE INDEX ix_commit_review_intent_ready
    ON commit_review_intent (intent_status, priority_rank, created_at, id);
