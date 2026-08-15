package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectTypeMigrationContractTest {

    @Test
    void v31CreatesProjectTypeAndNullableProjectForeignKeyWithoutBackfill() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V31__add_project_type.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "create table project_type",
                "unique (code)",
                "add column project_type_id char(36) null",
                "foreign key (project_type_id) references project_type (id)"
        );
        assertThat(sql).doesNotContain(
                "insert into project_type",
                "update project",
                "add column project_type_id char(36) not null",
                "drop table",
                "drop column"
        );
    }

    @Test
    void v32CreatesProjectScopedGroupWeightConfigWithUniqueProjectAndBigDecimalWeights() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V32__add_project_group_weight_config.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "create table project_group_weight_config",
                "unique (project_id)",
                "code_weight decimal(6,5) not null",
                "document_weight decimal(6,5) not null",
                "design_weight decimal(6,5) not null",
                "foreign key (project_id) references project (id)",
                "foreign key (team_id) references team (id)"
        );
        assertThat(sql).doesNotContain(
                "drop table",
                "task_weight_config",
                "peer_review",
                "rubric",
                "contribution"
        );
    }

    @Test
    void v34ReplacesProjectTypeDataWithExactlyFourCanonicalRowsInSafeOrder() throws Exception {
        String rawSql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V34__replace_project_type_with_canonical_catalog.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String sql = rawSql.toLowerCase();

        int nullOutIndex = sql.indexOf("update project");
        int deleteIndex = sql.indexOf("delete from project_type");
        int firstInsertIndex = sql.indexOf("insert into project_type");
        assertThat(nullOutIndex).isPositive();
        assertThat(deleteIndex).isGreaterThan(nullOutIndex);
        assertThat(firstInsertIndex).isGreaterThan(deleteIndex);

        assertThat(sql).contains(
                "update project",
                "set project_type_id = null",
                "delete from project_type",
                "'design_architecture'",
                "'research'",
                "'tester'",
                "'document'"
        );
        assertThat(sql).doesNotContain(
                "drop table",
                "drop column",
                "drop foreign key",
                "drop constraint fk_project_project_type"
        );

        long insertCount = sql.lines().filter(line -> line.trim().startsWith("insert into project_type")).count();
        assertThat(insertCount).isEqualTo(4);
    }
}
