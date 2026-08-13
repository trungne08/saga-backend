package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.saga.be.entity.enums.TaskType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class TaskTypeRequestMigrationContractTest {

    private static final Path MIGRATION = Path.of(
            "src", "main", "resources", "db", "migration",
            "V29__expand_task_type_enum_for_request.sql"
    );

    @Test
    void v29PhysicalEnumMatchesEveryExactJavaTaskType() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        Matcher enumMatcher = Pattern.compile(
                "(?is)modify\\s+column\\s+type\\s+enum\\s*\\((.*?)\\)"
        ).matcher(sql);

        assertThat(enumMatcher.find()).isTrue();
        List<String> migratedValues = Pattern.compile("'([A-Z_]+)'")
                .matcher(enumMatcher.group(1))
                .results()
                .map(result -> result.group(1))
                .toList();
        Set<String> javaValues = Arrays.stream(TaskType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(migratedValues)
                .doesNotHaveDuplicates()
                .hasSize(javaValues.size())
                .containsExactlyInAnyOrderElementsOf(javaValues);
    }

    @Test
    void v29PreservesNullableNoDefaultAndDoesNotRewriteTaskRows() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .toLowerCase();
        String normalizedSql = sql.replaceAll("\\s+", " ").trim();

        assertThat(normalizedSql).contains(
                "alter table task",
                "modify column type enum(",
                ") null default null"
        );
        assertThat(sql).doesNotContain(
                "update task",
                "delete from task",
                "insert into task",
                "drop table",
                "drop column",
                "course_service"
        );
    }
}
