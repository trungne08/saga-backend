package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GraphProcessingRunMigrationContractTest {

    @Test
    void migrationCreatesImmutableTelemetryWithoutForeignKeysOrSeedHistory() throws Exception {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration",
                "V44__add_graph_processing_run.sql"), StandardCharsets.UTF_8).toLowerCase();
        assertTrue(sql.contains("create table graph_processing_run"));
        assertTrue(sql.contains("occurred_at datetime(6) not null"));
        assertTrue(sql.contains("index ix_graph_processing_run_occurred_at (occurred_at)"));
        assertTrue(sql.contains("index ix_graph_processing_run_kind_occurred_at (graph_kind, occurred_at)"));
        assertTrue(sql.contains("nodes_built int not null"));
        assertTrue(sql.contains("edges_built int not null"));
        assertFalse(sql.contains("foreign key"));
        assertFalse(sql.contains("insert into"));
    }
}
