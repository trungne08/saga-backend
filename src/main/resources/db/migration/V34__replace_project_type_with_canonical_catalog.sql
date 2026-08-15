-- Product decision: ProjectType is no longer a dynamic ADMIN-managed catalog.
-- It becomes a fixed, migration-seeded canonical SAGA catalog of exactly four types.
-- Existing Project rows lose their old ProjectType reference and read back as
-- projectType=null (legacy-compatible contract); this is a product-approved reset.
--
-- ProjectType is purely a Project classification catalog; it does not decide Contribution
-- weights (see Course.contributionConfigMode / ContributionSliceWeightResolver instead).

UPDATE project
SET project_type_id = NULL
WHERE project_type_id IS NOT NULL;

DELETE FROM project_type;

INSERT INTO project_type (id, created_at, updated_at, code, name, description, criteria_config)
VALUES ('55555555-5555-5555-5555-555555555551', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
        'DESIGN_ARCHITECTURE', 'Design & Architecture', 'Dự án tập trung vào thiết kế sản phẩm hoặc kiến trúc hệ thống.', NULL);

INSERT INTO project_type (id, created_at, updated_at, code, name, description, criteria_config)
VALUES ('55555555-5555-5555-5555-555555555552', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
        'RESEARCH', 'Research', 'Dự án tập trung vào nghiên cứu hoặc khảo sát.', NULL);

INSERT INTO project_type (id, created_at, updated_at, code, name, description, criteria_config)
VALUES ('55555555-5555-5555-5555-555555555553', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
        'TESTER', 'Tester', 'Dự án tập trung vào kiểm thử phần mềm hoặc hệ thống.', NULL);

INSERT INTO project_type (id, created_at, updated_at, code, name, description, criteria_config)
VALUES ('55555555-5555-5555-5555-555555555554', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6),
        'DOCUMENT', 'Document', 'Dự án tập trung vào biên soạn tài liệu.', NULL);
