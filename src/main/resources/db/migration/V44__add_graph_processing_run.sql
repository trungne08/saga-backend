CREATE TABLE graph_processing_run (
    id CHAR(36) NOT NULL,
    graph_kind VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    course_id CHAR(36) NULL,
    team_id CHAR(36) NULL,
    student_id CHAR(36) NULL,
    nodes_built INT NOT NULL,
    edges_built INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_graph_processing_run_nodes_built_non_negative CHECK (nodes_built >= 0),
    CONSTRAINT ck_graph_processing_run_edges_built_non_negative CHECK (edges_built >= 0),
    INDEX ix_graph_processing_run_occurred_at (occurred_at),
    INDEX ix_graph_processing_run_kind_occurred_at (graph_kind, occurred_at)
);
