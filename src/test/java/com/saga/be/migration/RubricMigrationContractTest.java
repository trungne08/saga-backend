package com.saga.be.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RubricMigrationContractTest {

    private static final Path MIGRATION_DIRECTORY = Path.of(
            "src", "main", "resources", "db", "migration"
    );
    private static final Pattern VERSION_PATTERN = Pattern.compile("^V(\\d+)__.+\\.sql$");

    @Test
    void v22OnlyMakesRubricSubjectNullable() throws IOException {
        String sql = read("V22__make_rubric_subject_nullable.sql");
        String normalized = sql.replace("\r\n", "\n").trim();
        String lowerCaseSql = normalized.toLowerCase();

        assertEquals("""
                ALTER TABLE rubric_template
                    MODIFY COLUMN subject_id CHAR(36) NULL;""", normalized);
        assertFalse(lowerCaseSql.contains("insert"));
        assertFalse(lowerCaseSql.contains("update"));
        assertFalse(lowerCaseSql.contains("delete"));
        assertFalse(lowerCaseSql.contains("drop"));
    }

    @Test
    void v23OnlyAddsNullableRubricSoftDeleteColumn() throws IOException {
        String sql = read("V23__add_rubric_template_soft_delete.sql");
        String normalized = sql.replace("\r\n", "\n").trim();
        String lowerCaseSql = normalized.toLowerCase();

        assertEquals("""
                ALTER TABLE rubric_template
                    ADD COLUMN deleted_at DATETIME(6) NULL;""", normalized);
        assertFalse(lowerCaseSql.contains("insert"));
        assertFalse(lowerCaseSql.contains("update"));
        assertFalse(lowerCaseSql.contains("delete from"));
        assertFalse(lowerCaseSql.contains("drop"));
    }

    @Test
    void preservesHistoricalV10AndV13SourcesAndMakesV22NewestMigration() throws IOException {
        assertEquals(
                "A62E5A372E43CB7BAB0DAE327DAECE67D0F55296919336126CD6134373B28E5B",
                sha256("V10__add_peer_review_detail.sql")
        );
        assertEquals(
                "DA918835ADDDC272BC3A870776F9389CAA60F9174DF88AEF95BA034DE142E2AD",
                sha256("V13__seed_default_peer_review_rubrics.sql")
        );
        assertEquals(
                "D309E0CD4CEA71E32A448FDFC773ED729BD120F1E904765011822FC57A16A9FF",
                sha256("V22__make_rubric_subject_nullable.sql")
        );

        List<Integer> versions;
        try (var migrationFiles = Files.list(MIGRATION_DIRECTORY)) {
            versions = migrationFiles
                    .map(path -> path.getFileName().toString())
                    .map(VERSION_PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .toList();
        }

        assertTrue(versions.containsAll(List.of(10, 13, 21, 22, 23)));
        assertEquals(23, versions.stream().mapToInt(Integer::intValue).max().orElseThrow());
    }

    private String read(String migrationName) throws IOException {
        return Files.readString(MIGRATION_DIRECTORY.resolve(migrationName), StandardCharsets.UTF_8);
    }

    private String sha256(String migrationName) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(read(migrationName).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) {
                result.append(String.format("%02X", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
