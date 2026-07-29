-- SAGA integration V2 read-only preflight for MySQL.
-- This file performs SELECT/SHOW only. It does not create, alter, update,
-- delete, baseline, repair, or migrate anything.

SELECT
    DATABASE() AS selected_schema,
    VERSION() AS mysql_version,
    @@transaction_read_only AS transaction_read_only,
    @@sql_mode AS sql_mode;

-- Must return exactly one row for every legacy table that V2 alters.
SELECT required.table_name,
       CASE WHEN existing.table_name IS NULL THEN 'MISSING' ELSE 'PRESENT' END
           AS preflight_status
FROM (
    SELECT 'identity_map' AS table_name
    UNION ALL SELECT 'project'
    UNION ALL SELECT 'jira_board'
    UNION ALL SELECT 'git_repo'
    UNION ALL SELECT 'task'
    UNION ALL SELECT 'sprint'
    UNION ALL SELECT 'git_issue'
    UNION ALL SELECT 'commit_data'
    UNION ALL SELECT 'pull_request'
    UNION ALL SELECT 'pr_review'
    UNION ALL SELECT 'comment'
    UNION ALL SELECT 'sync_job_log'
    UNION ALL SELECT 'student'
) required
LEFT JOIN information_schema.tables existing
       ON existing.table_schema = DATABASE()
      AND existing.table_name = required.table_name
ORDER BY required.table_name;

-- Every table touched by foreign keys/DDL should use InnoDB. Review any
-- non-InnoDB result before migration.
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'identity_map', 'student', 'project', 'jira_board', 'git_repo',
      'task', 'sprint', 'git_issue', 'commit_data', 'pull_request',
      'pr_review', 'comment', 'sync_job_log'
  )
ORDER BY table_name;

-- Compare referenced/referencing ID types and collations. CHAR(36) identifier
-- columns participating in a foreign key must be compatible.
SELECT table_name, column_name, column_type, character_set_name, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'student' AND column_name = 'id')
      OR (table_name = 'project' AND column_name = 'id')
      OR (table_name = 'identity_map'
          AND column_name IN ('id', 'student_id'))
      OR (table_name = 'jira_board'
          AND column_name IN ('id', 'project_id'))
      OR (table_name = 'git_repo'
          AND column_name IN ('id', 'project_id'))
  )
ORDER BY table_name, column_name;

-- Expected result for a never-baselined legacy database: table_count = 0.
SELECT COUNT(*) AS flyway_history_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'flyway_schema_history';

-- Run this next SELECT only if the preceding count is 1.
-- SELECT installed_rank, version, description, type, script, checksum,
--        installed_by, installed_on, execution_time, success
-- FROM flyway_schema_history
-- ORDER BY installed_rank;

-- A clean pre-V2 legacy schema should return zero rows here. Any result means
-- V2 or a partial/manual equivalent may already exist; stop and investigate.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'identity_mapping_history',
      'github_installation',
      'webhook_receipt'
  )
ORDER BY table_name;

-- A clean pre-V2 legacy schema should return zero rows. Results indicate that
-- V2 columns may already have been added manually or by an earlier build.
SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
      (table_name = 'identity_map'
       AND column_name IN (
           'external_account_id', 'external_email', 'mapping_status',
           'verified_at', 'disconnected_at', 'reviewed_by_cognito_sub',
           'reviewed_at', 'version'
       ))
      OR
      (table_name = 'jira_board'
       AND column_name IN (
           'cloud_id', 'jira_project_id', 'encrypted_access_token',
           'encrypted_refresh_token', 'connection_status', 'webhook_id',
           'webhook_secret_hash', 'sync_cursor', 'version'
       ))
      OR
      (table_name = 'git_repo'
       AND column_name IN (
           'repository_id', 'installation_id', 'connection_status',
           'sync_cursor', 'version'
       ))
  )
ORDER BY table_name, ordinal_position;

-- A clean pre-V2 schema must return zero rows for both name-collision checks.
SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
      'uk_identity_student_provider', 'uk_identity_provider_external',
      'fk_identity_history_map', 'fk_identity_history_student',
      'uk_jira_board_project', 'uk_jira_cloud_project',
      'fk_jira_connector_student', 'uk_github_installation_id',
      'fk_github_installer_student', 'uk_git_repo_provider_id',
      'uk_git_repo_project_full_name', 'fk_git_repo_installation',
      'uk_task_project_external', 'uk_sprint_board_external',
      'uk_git_issue_repo_external', 'uk_commit_repo_sha',
      'uk_pull_repo_external', 'uk_pr_review_external',
      'uk_comment_source_external', 'uk_webhook_provider_delivery'
  )
ORDER BY table_name, constraint_name;

SELECT table_name, index_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN (
      'ix_identity_history_lookup', 'ix_jira_board_reconciliation',
      'ix_jira_board_webhook_secret', 'ix_github_installation_status',
      'ix_git_repo_reconciliation', 'ix_git_repo_installation',
      'ix_sync_job_target_started', 'ix_webhook_receipt_retry'
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

-- All following duplicate/invalid-data checks must return zero rows.
SELECT student_id, UPPER(TRIM(provider)) AS normalized_provider, COUNT(*) AS rows_count
FROM identity_map
GROUP BY student_id, UPPER(TRIM(provider))
HAVING COUNT(*) > 1;

SELECT id, provider
FROM identity_map
WHERE provider IS NULL
   OR TRIM(provider) = ''
   OR UPPER(TRIM(provider)) NOT IN ('JIRA', 'GITHUB');

SELECT project_id, COUNT(*) AS rows_count
FROM jira_board
GROUP BY project_id
HAVING COUNT(*) > 1;

SELECT project_id, name, COUNT(*) AS rows_count
FROM git_repo
GROUP BY project_id, name
HAVING COUNT(*) > 1;

SELECT repo_id, sha_hash, COUNT(*) AS rows_count
FROM commit_data
WHERE sha_hash IS NOT NULL
GROUP BY repo_id, sha_hash
HAVING COUNT(*) > 1;

-- Existing orphan checks must return zero rows before adding new foreign keys.
SELECT jb.id AS jira_board_id, jb.project_id
FROM jira_board jb
LEFT JOIN project p ON p.id = jb.project_id
WHERE jb.project_id IS NOT NULL
  AND p.id IS NULL;

SELECT gr.id AS git_repo_id, gr.project_id
FROM git_repo gr
LEFT JOIN project p ON p.id = gr.project_id
WHERE gr.project_id IS NOT NULL
  AND p.id IS NULL;

SELECT im.id AS identity_map_id, im.student_id
FROM identity_map im
LEFT JOIN student s ON s.id = im.student_id
WHERE im.student_id IS NULL
   OR s.id IS NULL;

-- Capacity snapshot only; use it to estimate lock/DDL duration.
SELECT table_name, table_rows, data_length, index_length
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'identity_map', 'jira_board', 'git_repo', 'task', 'sprint',
      'git_issue', 'commit_data', 'pull_request', 'pr_review',
      'comment', 'sync_job_log'
  )
ORDER BY data_length + index_length DESC;
