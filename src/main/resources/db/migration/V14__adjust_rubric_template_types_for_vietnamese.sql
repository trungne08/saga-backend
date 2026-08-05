ALTER TABLE rubric_template
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE rubric_template
    MODIFY COLUMN criteria_name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    MODIFY COLUMN description TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    MODIFY COLUMN weight DECIMAL(5,2) NOT NULL DEFAULT 25.00;
