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
