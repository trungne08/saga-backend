CREATE TABLE project_group_weight_config (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    project_id CHAR(36) NOT NULL,
    team_id CHAR(36) NOT NULL,
    code_weight DECIMAL(6,5) NOT NULL,
    document_weight DECIMAL(6,5) NOT NULL,
    design_weight DECIMAL(6,5) NOT NULL,
    note VARCHAR(1000) NULL,
    updated_by_profile_id CHAR(36) NULL,
    CONSTRAINT pk_project_group_weight_config PRIMARY KEY (id),
    CONSTRAINT uk_project_group_weight_config_project UNIQUE (project_id),
    CONSTRAINT fk_project_group_weight_config_project
        FOREIGN KEY (project_id) REFERENCES project (id),
    CONSTRAINT fk_project_group_weight_config_team
        FOREIGN KEY (team_id) REFERENCES team (id)
);

CREATE INDEX idx_project_group_weight_config_team_id
    ON project_group_weight_config (team_id);
