CREATE TABLE project_type (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    criteria_config TEXT NULL,
    CONSTRAINT pk_project_type PRIMARY KEY (id),
    CONSTRAINT uk_project_type_code UNIQUE (code)
);

ALTER TABLE project
    ADD COLUMN project_type_id CHAR(36) NULL;

ALTER TABLE project
    ADD CONSTRAINT fk_project_project_type
        FOREIGN KEY (project_type_id) REFERENCES project_type (id);

CREATE INDEX idx_project_project_type_id ON project (project_type_id);
