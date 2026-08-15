package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CourseContributionConfigMigrationContractTest {

    @Test
    void v35OnlyAddsColumnsAndDoesNotRewriteExistingCourseRows() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V35__add_course_contribution_config_mode_and_weights.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();
        String statementsOnly = withoutSqlComments(sql);

        assertThat(sql).contains(
                "alter table course",
                "add column test_contribution_weight double not null default 0",
                "add column research_contribution_weight double not null default 0",
                "add column contribution_config_mode varchar(16) not null default 'course'"
        );
        assertThat(statementsOnly).doesNotContain(
                "update course",
                "insert into course",
                "delete from course",
                "code_contribution_weight = 25",
                "document_contribution_weight = 25",
                "design_contribution_weight =",
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
