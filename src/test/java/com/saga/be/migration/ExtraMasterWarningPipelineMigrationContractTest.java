package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExtraMasterWarningPipelineMigrationContractTest {

    @Test
    void v42AddsCanonicalReviewResultBusinessWarningAndWarningEmailOutbox() throws IOException {
        String sql = Files.readString(
                Path.of("src", "main", "resources", "db", "migration",
                        "V42__add_commit_review_result_warning_email_and_business_warning.sql"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").toLowerCase();
        assertTrue(sql.contains("create table commit_review_result"));
        assertTrue(sql.contains("create table business_warning"));
        assertTrue(sql.contains("create table warning_email_outbox"));
        assertTrue(sql.contains("uk_commit_review_result_intent"));
        assertTrue(sql.contains("uk_commit_review_result_job"));
        assertTrue(sql.contains("uk_business_warning_event"));
        assertFalse(sql.contains("student_course_invitation"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("delete from"));
    }
}
