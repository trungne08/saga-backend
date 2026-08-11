-- Read-only MySQL preflight for V28 GitHub traceability.
-- Run against the selected production schema before the first V28 deployment.
-- V28 inherits the database default engine, character set, and collation.

-- A. Database defaults inherited by the V28 tables.
SELECT schema_name AS database_name,
       default_character_set_name,
       default_collation_name
FROM information_schema.schemata
WHERE schema_name = DATABASE();

-- B. Referenced table metadata. Every row must be PRESENT.
SELECT target.table_name,
       target_table.engine,
       target_table.table_collation,
       CASE
           WHEN target_table.table_name IS NULL THEN 'MISSING'
           ELSE 'PRESENT'
       END AS preflight_status
FROM (
    SELECT 'task' AS table_name
    UNION ALL SELECT 'git_issue'
    UNION ALL SELECT 'pull_request'
    UNION ALL SELECT 'commit_data'
) target
LEFT JOIN information_schema.tables target_table
  ON target_table.table_schema = DATABASE()
 AND target_table.table_name = target.table_name
ORDER BY target.table_name;

-- C. Referenced UUID column metadata. Every row must be CHAR(36).
SELECT target.table_name,
       target_column.data_type,
       target_column.column_type,
       target_column.character_maximum_length,
       target_column.character_set_name,
       target_column.collation_name,
       target_column.is_nullable,
       CASE
           WHEN target_column.column_name IS NULL THEN 'MISSING'
           WHEN target_column.data_type = 'char'
                AND target_column.character_maximum_length = 36
               THEN 'UUID_TYPE_COMPATIBLE'
           ELSE 'UUID_TYPE_INCOMPATIBLE'
       END AS preflight_status
FROM (
    SELECT 'task' AS table_name
    UNION ALL SELECT 'git_issue'
    UNION ALL SELECT 'pull_request'
    UNION ALL SELECT 'commit_data'
) target
LEFT JOIN information_schema.columns target_column
  ON target_column.table_schema = DATABASE()
 AND target_column.table_name = target.table_name
 AND target_column.column_name = 'id'
ORDER BY target.table_name;

-- D. Machine-readable compatibility result. V28_PREFLIGHT_READY must be PASS.
-- FK engine compatibility requires one supported transactional engine shared by
-- all targets and the current default storage engine inherited by the V28 tables.
SELECT
    CASE
        WHEN COUNT(target_column.column_name) = 4
             AND SUM(CASE
                         WHEN target_column.data_type = 'char'
                              AND target_column.character_maximum_length = 36
                             THEN 1 ELSE 0
                     END) = 4
            THEN 'PASS' ELSE 'FAIL'
    END AS UUID_TYPE_COMPATIBLE,
    CASE
        WHEN COUNT(target_column.column_name) = 4
             AND COUNT(DISTINCT target_column.character_set_name) = 1
            THEN 'PASS' ELSE 'FAIL'
    END AS UUID_CHARSET_COMPATIBLE,
    CASE
        WHEN COUNT(target_column.column_name) = 4
             AND COUNT(DISTINCT target_column.collation_name) = 1
            THEN 'PASS' ELSE 'FAIL'
    END AS UUID_COLLATION_COMPATIBLE,
    CASE
        WHEN COUNT(target_table.table_name) = 4
             AND COUNT(DISTINCT target_table.engine) = 1
             AND COUNT(engine_metadata.engine) = 4
             AND SUM(CASE
                         WHEN UPPER(engine_metadata.support) IN ('YES', 'DEFAULT')
                              AND UPPER(engine_metadata.transactions) = 'YES'
                              AND LOWER(engine_metadata.comment) LIKE '%foreign key%'
                             THEN 1 ELSE 0
                     END) = 4
             AND SUM(CASE
                         WHEN LOWER(target_table.engine) = LOWER(@@default_storage_engine)
                             THEN 1 ELSE 0
                     END) = 4
            THEN 'PASS' ELSE 'FAIL'
    END AS FK_ENGINE_COMPATIBLE,
    CASE
        WHEN COUNT(target_column.column_name) = 4
             AND SUM(CASE
                         WHEN target_column.character_set_name = schema_defaults.default_character_set_name
                              AND target_column.collation_name = schema_defaults.default_collation_name
                             THEN 1 ELSE 0
                     END) = 4
            THEN 'PASS' ELSE 'FAIL'
    END AS DATABASE_DEFAULT_MATCHES_UUID,
    CASE
        WHEN COUNT(target_table.table_name) = 4
             AND COUNT(target_column.column_name) = 4
             AND SUM(CASE
                         WHEN target_column.data_type = 'char'
                              AND target_column.character_maximum_length = 36
                             THEN 1 ELSE 0
                     END) = 4
             AND COUNT(DISTINCT target_column.character_set_name) = 1
             AND COUNT(DISTINCT target_column.collation_name) = 1
             AND COUNT(DISTINCT target_table.engine) = 1
             AND COUNT(engine_metadata.engine) = 4
             AND SUM(CASE
                         WHEN UPPER(engine_metadata.support) IN ('YES', 'DEFAULT')
                              AND UPPER(engine_metadata.transactions) = 'YES'
                              AND LOWER(engine_metadata.comment) LIKE '%foreign key%'
                             THEN 1 ELSE 0
                     END) = 4
             AND SUM(CASE
                         WHEN LOWER(target_table.engine) = LOWER(@@default_storage_engine)
                             THEN 1 ELSE 0
                     END) = 4
             AND SUM(CASE
                         WHEN target_column.character_set_name = schema_defaults.default_character_set_name
                              AND target_column.collation_name = schema_defaults.default_collation_name
                             THEN 1 ELSE 0
                     END) = 4
             AND (SELECT COUNT(*)
                  FROM information_schema.tables v28_table
                  WHERE v28_table.table_schema = DATABASE()
                    AND v28_table.table_name IN (
                        'task_git_issue_link',
                        'git_issue_pull_request_link',
                        'git_issue_commit_link'
                    )) = 0
             AND (SELECT COUNT(*)
                  FROM information_schema.table_constraints v28_constraint
                  WHERE v28_constraint.constraint_schema = DATABASE()
                    AND v28_constraint.constraint_name IN (
                        'uk_task_git_issue_link_pair',
                        'fk_task_git_issue_link_task',
                        'fk_task_git_issue_link_issue',
                        'uk_git_issue_pull_request_link_pair',
                        'fk_git_issue_pull_request_link_issue',
                        'fk_git_issue_pull_request_link_pull',
                        'uk_git_issue_commit_link_pair',
                        'fk_git_issue_commit_link_issue',
                        'fk_git_issue_commit_link_commit'
                    )) = 0
            THEN 'PASS' ELSE 'FAIL'
    END AS V28_PREFLIGHT_READY
FROM (
    SELECT 'task' AS table_name
    UNION ALL SELECT 'git_issue'
    UNION ALL SELECT 'pull_request'
    UNION ALL SELECT 'commit_data'
) target
LEFT JOIN information_schema.tables target_table
  ON target_table.table_schema = DATABASE()
 AND target_table.table_name = target.table_name
LEFT JOIN information_schema.columns target_column
  ON target_column.table_schema = DATABASE()
 AND target_column.table_name = target.table_name
 AND target_column.column_name = 'id'
LEFT JOIN information_schema.engines engine_metadata
  ON LOWER(engine_metadata.engine) = LOWER(target_table.engine)
LEFT JOIN information_schema.schemata schema_defaults
  ON schema_defaults.schema_name = DATABASE();

-- Must return zero rows before V28.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
      'task_git_issue_link',
      'git_issue_pull_request_link',
      'git_issue_commit_link'
  )
ORDER BY table_name;

-- Must return zero rows before V28.
SELECT table_name, constraint_name
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN (
      'uk_task_git_issue_link_pair',
      'fk_task_git_issue_link_task',
      'fk_task_git_issue_link_issue',
      'uk_git_issue_pull_request_link_pair',
      'fk_git_issue_pull_request_link_issue',
      'fk_git_issue_pull_request_link_pull',
      'uk_git_issue_commit_link_pair',
      'fk_git_issue_commit_link_issue',
      'fk_git_issue_commit_link_commit'
  )
ORDER BY table_name, constraint_name;
