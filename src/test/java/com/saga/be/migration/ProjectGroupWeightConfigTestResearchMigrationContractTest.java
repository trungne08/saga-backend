package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectGroupWeightConfigTestResearchMigrationContractTest {

    @Test
    void v36OnlyAddsColumnsAndDoesNotRewriteExistingRows() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V36__add_test_research_weight_to_project_group_weight_config.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();
        String statementsOnly = withoutSqlComments(sql);

        assertThat(sql).contains(
                "alter table project_group_weight_config",
                "add column test_weight decimal(6,5) not null default 0",
                "add column research_weight decimal(6,5) not null default 0"
        );
        assertThat(statementsOnly).doesNotContain(
                "update project_group_weight_config",
                "insert into project_group_weight_config",
                "delete from project_group_weight_config",
                "design_weight",
                "drop table",
                "drop column"
        );
    }

    private String withoutSqlComments(String sql) {
        return sql.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .reduce("", (acc, line) -> acc + line + "\n");
    }
}
