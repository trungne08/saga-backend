CREATE TABLE active_semester_setting (
    singleton_id TINYINT NOT NULL,
    semester_id CHAR(36) NULL,
    CONSTRAINT pk_active_semester_setting PRIMARY KEY (singleton_id),
    CONSTRAINT ck_active_semester_setting_singleton CHECK (singleton_id = 1),
    CONSTRAINT fk_active_semester_setting_semester
        FOREIGN KEY (semester_id) REFERENCES semester(id)
);

INSERT INTO active_semester_setting (singleton_id, semester_id)
VALUES (1, NULL);
