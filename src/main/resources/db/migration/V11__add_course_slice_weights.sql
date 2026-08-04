ALTER TABLE course
    ADD COLUMN code_contribution_weight DOUBLE NOT NULL DEFAULT 33.333333333333336 AFTER name,
    ADD COLUMN document_contribution_weight DOUBLE NOT NULL DEFAULT 33.333333333333336 AFTER code_contribution_weight,
    ADD COLUMN design_contribution_weight DOUBLE NOT NULL DEFAULT 33.333333333333336 AFTER document_contribution_weight;

ALTER TABLE policy_override_request
    ADD COLUMN proposed_code_weight FLOAT NULL AFTER proposed_weight,
    ADD COLUMN proposed_document_weight FLOAT NULL AFTER proposed_code_weight,
    ADD COLUMN proposed_design_weight FLOAT NULL AFTER proposed_document_weight;
