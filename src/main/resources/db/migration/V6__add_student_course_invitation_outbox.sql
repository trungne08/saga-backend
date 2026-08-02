CREATE TABLE student_course_invitation (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    student_id CHAR(36) NOT NULL,
    course_id CHAR(36) NOT NULL,
    invitation_type VARCHAR(32) NOT NULL,
    invitation_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    processing_started_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    failure_code VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_course_invitation_type
        UNIQUE (student_id, course_id, invitation_type),
    CONSTRAINT fk_student_course_invitation_student
        FOREIGN KEY (student_id) REFERENCES student (id),
    CONSTRAINT fk_student_course_invitation_course
        FOREIGN KEY (course_id) REFERENCES course (id),
    INDEX ix_student_course_invitation_status_created (invitation_status, created_at),
    INDEX ix_student_course_invitation_status_processing
        (invitation_status, processing_started_at)
);
