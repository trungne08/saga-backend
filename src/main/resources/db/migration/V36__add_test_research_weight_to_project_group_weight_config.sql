-- Same product decision as V35: Contribution criteria universe becomes Code/Test/Document/
-- Research; DESIGN is retired as an active criterion. project_group_weight_config is the
-- storage for TEAM-mode per-Team overrides (0..1 scale, matching its existing columns).
--
-- Existing rows already had code_weight + document_weight + design_weight summing to 1.0.
-- No safe redistribution formula exists, so existing rows are preserved: the two new columns
-- default to 0, keeping the stored sum at 1.0 without discarding any Lecturer-configured
-- value. design_weight is retained as historical/inactive data (not read by the new resolver);
-- no DESIGN -> RESEARCH mapping is invented.

ALTER TABLE project_group_weight_config
    ADD COLUMN test_weight DECIMAL(6,5) NOT NULL DEFAULT 0 AFTER code_weight;

ALTER TABLE project_group_weight_config
    ADD COLUMN research_weight DECIMAL(6,5) NOT NULL DEFAULT 0 AFTER document_weight;
