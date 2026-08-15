-- Create rubric_template table first (required by peer_review_detail FK)
CREATE TABLE rubric_template (
    id CHAR(36) PRIMARY KEY,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    subject_id CHAR(36) NOT NULL,
    criteria_name VARCHAR(255) NOT NULL,
    description TEXT,
    weight DECIMAL(38,2) NOT NULL DEFAULT 25.00,
    FOREIGN KEY (subject_id) REFERENCES subject(id)
) CHARSET=utf8mb4;

ALTER TABLE rubric_template
ADD CONSTRAINT fk_rubric_template_subject 
FOREIGN KEY (subject_id) 
REFERENCES subject(id);

-- Create peer_review_detail table
CREATE TABLE peer_review_detail (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    peer_review_id CHAR(36) NOT NULL,
    rubric_id CHAR(36) NOT NULL,
    criteria_name VARCHAR(255) NOT NULL,
    criteria_order INT NOT NULL,
    star_rating INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_peer_review_detail_peer_review
        FOREIGN KEY (peer_review_id) REFERENCES peer_review (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_peer_review_detail_rubric
        FOREIGN KEY (rubric_id) REFERENCES rubric_template (id)
);

CREATE INDEX idx_peer_review_detail_peer_review_id
    ON peer_review_detail (peer_review_id);

CREATE INDEX idx_peer_review_detail_rubric_id
    ON peer_review_detail (rubric_id);
