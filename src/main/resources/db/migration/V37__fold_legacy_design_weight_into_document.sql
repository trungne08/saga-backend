-- Product decision (explicit, product-owner-given formula — not an invented redistribution):
-- DESIGN was retired as an active Contribution criterion in DEC-088. Its historical weight value
-- (design_contribution_weight / design_weight) was never read by ContributionSliceWeightResolver
-- again after that migration, which meant a Lecturer's original design-allocated intent silently
-- stopped participating in the active weight sum. This migration folds that dormant value into
-- DOCUMENT so the Lecturer's originally-intended total is preserved, per the exact formula:
--   newDocument = oldDocument + oldDesign
--   design column is zeroed after the fold (its value has moved, not been discarded)
-- No DESIGN -> RESEARCH mapping is created. code/test/research columns are NOT touched by this
-- migration at all.
--
-- SAFETY GUARD (audited): neither the official Course PUT (CourseContributionWeightService
-- #updateCurrentWeights) nor an UPDATE of an already-existing ProjectGroupWeightConfig row
-- (ProjectGroupWeightConfigService#update) ever zeroes the legacy design column — only a brand
-- new ProjectGroupWeightConfig row gets design explicitly zeroed at creation. This means a row
-- can legitimately already have a fully-configured, valid active 4-field sum (100 for Course,
-- 1.0 for Team/Project) sitting alongside a stale non-zero legacy design value that was never
-- cleared. Folding design into document for such a row would push the active sum above
-- 100/1.0 (e.g. code=40 test=20 document=30 research=10 design=30(legacy) -> naive fold would
-- produce document=60, active sum=130). This migration must NEVER do that.
--
-- Deterministic detection rule (not invented — it re-tests the exact invariant that was the ONLY
-- validated sum rule before DEC-088, i.e. code+document+design=100/1.0): a row is only folded if
-- design is genuinely "the missing piece" of an otherwise-legacy total — that is, if
-- code+test+document+research+design still equals exactly 100 (Course) / 1.0 (Team). If the four
-- active columns alone already sum to 100/1.0 (proving the row was explicitly reconfigured via
-- the new validated PUT after design was last set), design is left untouched and inactive,
-- exactly as ContributionSliceWeightResolver already treats it (never read).
--
-- V34/V35/V36 applied status could not be confirmed with runtime evidence at the time this
-- migration was authored, so per the safe default under that uncertainty, V34/V35/V36 are left
-- untouched — this is a new migration after the current head, not a rewrite of any prior one.

UPDATE course
SET document_contribution_weight = document_contribution_weight + design_contribution_weight,
    design_contribution_weight = 0
WHERE design_contribution_weight IS NOT NULL
  AND design_contribution_weight <> 0
  AND ABS(
        (code_contribution_weight + test_contribution_weight
            + document_contribution_weight + research_contribution_weight
            + design_contribution_weight) - 100
      ) < 0.01;

UPDATE project_group_weight_config
SET document_weight = document_weight + design_weight,
    design_weight = 0
WHERE design_weight IS NOT NULL
  AND design_weight <> 0
  AND (code_weight + test_weight + document_weight + research_weight + design_weight) = 1;
