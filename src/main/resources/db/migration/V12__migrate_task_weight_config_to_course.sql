ALTER TABLE task_weight_config
    ADD COLUMN course_id CHAR(36) NULL AFTER class_id;

UPDATE task_weight_config twc
SET twc.course_id = (
    SELECT MIN(c.id)
    FROM course c
    WHERE c.class_id = twc.class_id
      AND c.subject_id = twc.subject_id
)
WHERE twc.course_id IS NULL;

ALTER TABLE task_weight_config
    ADD INDEX idx_task_weight_config_course_id (course_id);

ALTER TABLE task_weight_config
    ADD CONSTRAINT fk_task_weight_config_course
        FOREIGN KEY (course_id) REFERENCES course (id);
