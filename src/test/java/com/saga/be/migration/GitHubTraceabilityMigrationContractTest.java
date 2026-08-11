package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GitHubTraceabilityMigrationContractTest {

    @Test
    void v28AddsNormalizedTraceabilityWithoutChangingLegacyForeignKeys() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V28__add_github_issue_traceability.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "create table task_git_issue_link",
                "unique (task_id, git_issue_id)",
                "create table git_issue_pull_request_link",
                "unique (git_issue_id, pull_request_id)",
                "create table git_issue_commit_link",
                "unique (git_issue_id, commit_id)",
                "foreign key (task_id) references task(id)",
                "foreign key (git_issue_id) references git_issue(id)",
                "foreign key (pull_request_id) references pull_request(id)",
                "foreign key (commit_id) references commit_data(id)"
        );
        assertThat(sql).doesNotContain(
                "alter table pull_request",
                "alter table commit_data",
                "drop table",
                "course_service"
        );
    }

    @Test
    void v28PreflightIsReadOnlyAndChecksRuntimeForeignKeyCompatibility() throws Exception {
        String sql = Files.readString(Path.of(
                "docs", "integrations", "mysql-traceability-v28-preflight.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "information_schema.tables",
                "information_schema.columns",
                "information_schema.schemata",
                "information_schema.engines",
                "information_schema.table_constraints",
                "default_character_set_name",
                "default_collation_name",
                "character_set_name",
                "collation_name",
                "engine",
                "table_collation",
                "@@default_storage_engine",
                "engine_metadata.comment",
                "character_maximum_length = 36",
                "uuid_type_compatible",
                "uuid_charset_compatible",
                "uuid_collation_compatible",
                "fk_engine_compatible",
                "database_default_matches_uuid",
                "v28_preflight_ready",
                "'task'",
                "'git_issue'",
                "'pull_request'",
                "'commit_data'",
                "target_column.column_name = 'id'",
                "'task_git_issue_link'"
        );
        assertThat(sql).doesNotContain("utf8mb4", "innodb");
        assertThat(sql).doesNotMatch(
                "(?im)^\\s*(alter|create|drop|update|delete|insert|replace|truncate|signal|set)\\b.*"
        );
    }
}
