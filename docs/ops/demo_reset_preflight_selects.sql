-- ============================================================================
-- SAGA Backend — Demo Data Reset, Phase 1 Preflight (READ-ONLY)
-- ============================================================================
-- Every statement in this file is a SELECT COUNT(*). Nothing here writes,
-- deletes, truncates, drops, or disables foreign key checks.
-- Safe to run as-is against the demo/production database for review.
--
-- Purpose: give Product the actual BEFORE counts referenced in
-- docs/ops/DEMO_DATA_RESET_PHASE1_AUDIT.md, both for protected tables
-- (which must stay identical after any future Phase 2) and for the
-- proposed delete-candidate tables (to size the actual cleanup).
--
-- Fill in the {{COURSE_IDS}} / {{SEMESTER_IDS}} placeholders once Product
-- supplies the explicit demo scope (see "Open issue 3" in the audit doc) —
-- until then, the *_SCOPED queries below cannot be run meaningfully.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- SECTION A — PROTECTED COUNTS (must be identical before/after any Phase 2 run)
-- ----------------------------------------------------------------------------

-- TABLE = admin | WHY = protected identity | SCOPE = all rows
SELECT COUNT(*) AS admin_count FROM admin;

-- TABLE = lecturer | WHY = protected identity | SCOPE = all rows
SELECT COUNT(*) AS lecturer_count FROM lecturer;

-- TABLE = student | WHY = protected identity, never inferred from PENDING/null cognito_sub
SELECT COUNT(*) AS student_count FROM student;

-- TABLE = student | WHY = authenticated-identity set must be provably unchanged after Phase 2
SELECT COUNT(*) AS student_with_cognito_sub_count FROM student WHERE cognito_sub IS NOT NULL;

-- TABLE = identity_map | WHY = personal Jira/GitHub identity mapping, protected
SELECT COUNT(*) AS identity_map_count FROM identity_map;

-- TABLE = identity_mapping_history | WHY = personal identity audit trail, protected
SELECT COUNT(*) AS identity_mapping_history_count FROM identity_mapping_history;

-- TABLE = flyway_schema_history | WHY = migration bookkeeping, must never change
SELECT COUNT(*) AS flyway_schema_history_count FROM flyway_schema_history;

-- TABLE = subject | WHY = master data, default KEEP
SELECT COUNT(*) AS subject_count FROM subject;

-- TABLE = class | WHY = master data, default KEEP (Course still references Class)
SELECT COUNT(*) AS class_count FROM class;

-- TABLE = project_type | WHY = master/canonical catalog, default KEEP
SELECT COUNT(*) AS project_type_count FROM project_type;

-- TABLE = rubric_template | WHY = master rubric configuration, default KEEP
SELECT COUNT(*) AS rubric_template_count FROM rubric_template;

-- TABLE = active_semester_setting | WHY = singleton row must always exist, never deleted
SELECT COUNT(*) AS active_semester_setting_row_count FROM active_semester_setting;
SELECT singleton_id, semester_id FROM active_semester_setting;


-- ----------------------------------------------------------------------------
-- SECTION B — DELETE-CANDIDATE TABLES, UNSCOPED TOTALS
-- (Run these now to understand overall table sizes; they are NOT the scoped
--  demo counts — see Section C once Product supplies Course/Semester IDs.)
-- ----------------------------------------------------------------------------

SELECT COUNT(*) AS total_course FROM course;
SELECT COUNT(*) AS total_semester FROM semester;
SELECT COUNT(*) AS total_project FROM project;
SELECT COUNT(*) AS total_team FROM team;
SELECT COUNT(*) AS total_team_member FROM team_member;
SELECT COUNT(*) AS total_student_course_invitation FROM student_course_invitation;
SELECT COUNT(*) AS total_jira_board FROM jira_board;
SELECT COUNT(*) AS total_github_installation FROM github_installation;
SELECT COUNT(*) AS total_git_repo FROM git_repo;
SELECT COUNT(*) AS total_sprint FROM sprint;
SELECT COUNT(*) AS total_task FROM task;
SELECT COUNT(*) AS total_jira_write_operation FROM jira_write_operation;
SELECT COUNT(*) AS total_task_weight_config FROM task_weight_config;
SELECT COUNT(*) AS total_project_group_weight_config FROM project_group_weight_config;
SELECT COUNT(*) AS total_document FROM document;
SELECT COUNT(*) AS total_meeting_log FROM meeting_log;
SELECT COUNT(*) AS total_meeting_attendee FROM meeting_attendee;
SELECT COUNT(*) AS total_peer_review FROM peer_review;
SELECT COUNT(*) AS total_peer_review_detail FROM peer_review_detail;
SELECT COUNT(*) AS total_ai_interaction_log FROM ai_interaction_log;
SELECT COUNT(*) AS total_risk_alert FROM risk_alert;
SELECT COUNT(*) AS total_comment FROM comment;
SELECT COUNT(*) AS total_pr_review FROM pr_review;
SELECT COUNT(*) AS total_pull_request FROM pull_request;
SELECT COUNT(*) AS total_commit_data FROM commit_data;
SELECT COUNT(*) AS total_commit_file FROM commit_file;
SELECT COUNT(*) AS total_commit_review_intent FROM commit_review_intent;
SELECT COUNT(*) AS total_commit_review_result FROM commit_review_result;
SELECT COUNT(*) AS total_git_issue FROM git_issue;
SELECT COUNT(*) AS total_file_module FROM file_module;
SELECT COUNT(*) AS total_task_attachment FROM task_attachment;
SELECT COUNT(*) AS total_task_web_link FROM task_web_link;
SELECT COUNT(*) AS total_task_git_issue_link FROM task_git_issue_link;
SELECT COUNT(*) AS total_git_issue_commit_link FROM git_issue_commit_link;
SELECT COUNT(*) AS total_git_issue_pull_request_link FROM git_issue_pull_request_link;


-- ----------------------------------------------------------------------------
-- SECTION C — SCOPED COUNTS (fill in Course/Semester IDs supplied by Product)
-- These are the DELETE_ORDER-numbered queries matching
-- docs/ops/DEMO_DATA_RESET_PHASE1_AUDIT.md section 5.
-- Replace {{COURSE_IDS}} with a literal UUID list, e.g.
--   ('11111111-1111-1111-1111-111111111111','22222222-2222-2222-2222-222222222222')
-- ----------------------------------------------------------------------------

-- DELETE_ORDER 34 | TABLE = course | SCOPE = explicit Course-ID allowlist from Product
-- POST_CONDITION: 0 rows in this scope after Phase 2
SELECT COUNT(*) AS scoped_course FROM course WHERE id IN ({{COURSE_IDS}});

-- DELETE_ORDER 33 | TABLE = project | SCOPE = project.course_id IN scope
SELECT COUNT(*) AS scoped_project FROM project WHERE course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 25 | TABLE = team | SCOPE = team.course_id IN scope
SELECT COUNT(*) AS scoped_team FROM team WHERE course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 17 | TABLE = team_member | SCOPE = via team.course_id IN scope
SELECT COUNT(*) AS scoped_team_member
FROM team_member tm JOIN team t ON tm.team_id = t.id
WHERE t.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 18 | TABLE = student_course_invitation | SCOPE = course_id IN scope
SELECT COUNT(*) AS scoped_student_course_invitation
FROM student_course_invitation WHERE course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 14 | TABLE = task_weight_config | SCOPE = course_id IN scope
SELECT COUNT(*) AS scoped_task_weight_config
FROM task_weight_config WHERE course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 32 | TABLE = jira_board | SCOPE = via project.course_id IN scope
-- NOTE: also flags rows with a non-null encrypted token / connected student — see Open Issue 1
SELECT COUNT(*) AS scoped_jira_board,
       SUM(CASE WHEN encrypted_access_token IS NOT NULL THEN 1 ELSE 0 END) AS with_encrypted_token,
       SUM(CASE WHEN connected_by_student_id IS NOT NULL THEN 1 ELSE 0 END) AS with_connected_student
FROM jira_board jb JOIN project p ON jb.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 28 | TABLE = git_repo | SCOPE = via project.course_id IN scope
SELECT COUNT(*) AS scoped_git_repo
FROM git_repo gr JOIN project p ON gr.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 29 | TABLE = github_installation | SCOPE = installations used ONLY by in-scope git_repo rows
-- PRECONDITION CHECK — must return 0 rows for an installation to be safely deletable.
-- Any installation_id appearing here is shared with a non-demo project: DO NOT DELETE.
SELECT gi.id AS installation_id, gi.installation_id AS github_installation_id, COUNT(*) AS repos_outside_scope
FROM github_installation gi
JOIN git_repo gr ON gr.installation_id = gi.id
JOIN project p ON gr.project_id = p.id
WHERE gi.id IN (
    SELECT DISTINCT gr2.installation_id
    FROM git_repo gr2 JOIN project p2 ON gr2.project_id = p2.id
    WHERE p2.course_id IN ({{COURSE_IDS}})
)
AND (p.course_id IS NULL OR p.course_id NOT IN ({{COURSE_IDS}}))
GROUP BY gi.id, gi.installation_id;

-- DELETE_ORDER 31 | TABLE = sprint | SCOPE = via jira_board.project_id -> project.course_id IN scope
SELECT COUNT(*) AS scoped_sprint
FROM sprint s JOIN jira_board jb ON s.board_id = jb.id JOIN project p ON jb.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 30 | TABLE = task | SCOPE = via project.course_id IN scope
-- Also count self-referential blocks_task_id rows that must be nulled first (precondition).
SELECT COUNT(*) AS scoped_task,
       SUM(CASE WHEN blocks_task_id IS NOT NULL THEN 1 ELSE 0 END) AS with_blocks_task_id
FROM task t JOIN project p ON t.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 26 | TABLE = pull_request | SCOPE = via git_repo.project_id -> course_id IN scope
SELECT COUNT(*) AS scoped_pull_request
FROM pull_request pr JOIN git_repo gr ON pr.repo_id = gr.id JOIN project p ON gr.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 27 | TABLE = git_issue | SCOPE = via git_repo.project_id -> course_id IN scope
SELECT COUNT(*) AS scoped_git_issue
FROM git_issue gi JOIN git_repo gr ON gi.repo_id = gr.id JOIN project p ON gr.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 24 | TABLE = commit_data | SCOPE = via git_repo.project_id -> course_id IN scope
SELECT COUNT(*) AS scoped_commit_data
FROM commit_data cd JOIN git_repo gr ON cd.repo_id = gr.id JOIN project p ON gr.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 21 | TABLE = meeting_log | SCOPE = via team.course_id IN scope
SELECT COUNT(*) AS scoped_meeting_log
FROM meeting_log ml JOIN team t ON ml.team_id = t.id
WHERE t.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 22 | TABLE = peer_review | SCOPE = via sprint -> jira_board -> project.course_id IN scope
SELECT COUNT(*) AS scoped_peer_review
FROM peer_review pv
JOIN sprint s ON pv.sprint_id = s.id
JOIN jira_board jb ON s.board_id = jb.id
JOIN project p ON jb.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 15 | TABLE = document | SCOPE = project.course_id IN scope
SELECT COUNT(*) AS scoped_document
FROM document d JOIN project p ON d.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 11-13 | TABLE = ai_interaction_log / risk_alert / jira_write_operation | SCOPE = project.course_id IN scope
SELECT COUNT(*) AS scoped_ai_interaction_log
FROM ai_interaction_log ail JOIN project p ON ail.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

SELECT COUNT(*) AS scoped_risk_alert
FROM risk_alert ra JOIN project p ON ra.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

SELECT COUNT(*) AS scoped_jira_write_operation
FROM jira_write_operation jwo JOIN project p ON jwo.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- DELETE_ORDER 16 | TABLE = project_group_weight_config | SCOPE = project.course_id IN scope
SELECT COUNT(*) AS scoped_project_group_weight_config
FROM project_group_weight_config pgwc JOIN project p ON pgwc.project_id = p.id
WHERE p.course_id IN ({{COURSE_IDS}});

-- Semester scope (separate from Course scope; Product must confirm which Semesters, if any)
-- TABLE = semester | SCOPE = explicit Semester-ID allowlist from Product
SELECT COUNT(*) AS scoped_semester FROM semester WHERE id IN ({{SEMESTER_IDS}});

-- PRECONDITION CHECK for semester cleanup: does active_semester_setting currently point at
-- a semester in scope? If yes, semester_id must be nulled BEFORE any semester delete.
SELECT singleton_id, semester_id
FROM active_semester_setting
WHERE semester_id IN ({{SEMESTER_IDS}});
