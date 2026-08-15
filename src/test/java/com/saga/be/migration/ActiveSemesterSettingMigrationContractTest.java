package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ActiveSemesterSettingMigrationContractTest {

    @Test
    void v24AddsOnlyTypedSingletonActiveSemesterSettingWithoutHardcodedSemester() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration",
                "V24__add_active_semester_setting.sql"), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        String lower = sql.toLowerCase();

        assertTrue(lower.contains("create table active_semester_setting"));
        assertTrue(lower.contains("singleton_id tinyint not null"));
        assertTrue(lower.contains("semester_id char(36) null"));
        assertTrue(lower.contains("check (singleton_id = 1)"));
        assertTrue(lower.contains("foreign key (semester_id) references semester(id)"));
        assertTrue(lower.contains("insert into active_semester_setting (singleton_id, semester_id)"));
        assertTrue(lower.contains("values (1, null)"));
        assertFalse(lower.contains("alter table semester"));
        assertFalse(lower.contains("update semester"));
        assertFalse(lower.contains("delete from semester"));
    }
}
