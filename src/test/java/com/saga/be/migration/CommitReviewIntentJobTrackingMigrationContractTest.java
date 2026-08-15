package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommitReviewIntentJobTrackingMigrationContractTest {

    @Test
    void v41OnlyAddsNullableJobTrackingColumns() throws IOException {
        String sql = Files.readString(
                Path.of("src", "main", "resources", "db", "migration",
                        "V41__add_commit_review_intent_job_tracking.sql"),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").toLowerCase();
        assertTrue(sql.contains("alter table commit_review_intent"));
        assertTrue(sql.contains("add column ai_job_id"));
        assertTrue(sql.contains("add column review_policy_version"));
        assertFalse(sql.contains("drop table"));
        assertFalse(sql.contains("delete from"));
    }
}
