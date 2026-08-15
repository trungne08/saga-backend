-- Product decision (final): Contribution criteria universe is Code/Test/Document/Research.
-- DESIGN is retired as an active Contribution criterion (it remains a ProjectType catalog
-- value only). Each Course has exactly one active Contribution configuration mode: COURSE
-- (one Course-wide weight set used by every Team) or TEAM (every Team must have its own
-- weight set; no fallback). See ContributionSliceWeightResolver / ContributionConfigMode.
--
-- Existing Course rows already had Code+Document+Design summing to 100 (Lecturer-configured
-- or the prior default). There is no source evidence for how a Lecturer would want to
-- redistribute an existing 3-way split across newly introduced criteria, and product
-- ownership over existing Course configuration was not delegated to this migration. Existing
-- rows are therefore left untouched: new columns default to values that preserve the existing
-- invariant without inventing a redistribution rule or discarding any Lecturer-configured
-- value:
--   * test_contribution_weight    DEFAULT 0 (sum stays exactly 100 for existing rows)
--   * research_contribution_weight DEFAULT 0 (sum stays exactly 100 for existing rows)
--   * contribution_config_mode    DEFAULT 'COURSE' (preserves current single-Course-config
--     behavior; TEAM mode must be explicitly activated by a Lecturer after auditing every
--     current Team has a valid override)
--
-- design_contribution_weight (added by V11) is NOT touched by this migration: it is retained
-- as historical/inactive data, not read by the new Contribution calculation. There is no safe
-- semantic mapping from a historical DESIGN weight to the new RESEARCH criterion, so none is
-- invented here (LEGACY_DESIGN_WEIGHT_MIGRATION = TBD; see docs).
--
-- New Course rows created after this migration get the product-recommended 25/25/25/25
-- (Code/Test/Document/Research) default at the application layer
-- (Course#applyDefaultContributionWeights, @PrePersist), not by rewriting this migration's
-- column defaults — this migration only adds columns safely for existing data.

ALTER TABLE course
    ADD COLUMN test_contribution_weight DOUBLE NOT NULL DEFAULT 0 AFTER code_contribution_weight;

ALTER TABLE course
    ADD COLUMN research_contribution_weight DOUBLE NOT NULL DEFAULT 0 AFTER document_contribution_weight;

ALTER TABLE course
    ADD COLUMN contribution_config_mode VARCHAR(16) NOT NULL DEFAULT 'COURSE' AFTER design_contribution_weight;
