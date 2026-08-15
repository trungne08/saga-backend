package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * V37 folds the legacy DESIGN Contribution weight into DOCUMENT with a product-given formula
 * (newDocument = oldDocument + oldDesign), but only for rows where design is genuinely the
 * "missing piece" of an otherwise-legacy 100%/1.0 total — never for a row whose active four
 * fields (code/test/document/research) already validly sum to 100/1.0 on their own, since that
 * would push the active sum above 100/1.0 and silently corrupt an already-correct configuration.
 *
 * <p>Because this conditional guard is arithmetic (not just a fixed column check), a plain
 * SQL-text-content assertion cannot prove the fold/no-fold branches actually behave correctly.
 * This test executes the real V37 SQL file against an isolated in-memory H2 database (minimal
 * tables with only the columns V37 touches) and asserts the resulting rows — the same pattern
 * this test uses is the actual migration text, not a reimplementation of its logic.
 */
class LegacyDesignWeightFoldMigrationContractTest {

    private Connection connection;

    @BeforeEach
    void openDatabase() throws Exception {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:v37test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1"
        );
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE course (
                        id CHAR(36) PRIMARY KEY,
                        code_contribution_weight DOUBLE NOT NULL,
                        test_contribution_weight DOUBLE NOT NULL,
                        document_contribution_weight DOUBLE NOT NULL,
                        research_contribution_weight DOUBLE NOT NULL,
                        design_contribution_weight DOUBLE NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE project_group_weight_config (
                        id CHAR(36) PRIMARY KEY,
                        code_weight DECIMAL(6,5) NOT NULL,
                        test_weight DECIMAL(6,5) NOT NULL,
                        document_weight DECIMAL(6,5) NOT NULL,
                        research_weight DECIMAL(6,5) NOT NULL,
                        design_weight DECIMAL(6,5) NOT NULL
                    )
                    """);
        }
    }

    @AfterEach
    void closeDatabase() throws Exception {
        connection.close();
    }

    @Test
    void v37FoldsOrSkipsEachRowAccordingToTheDeterministicGuard() throws Exception {
        String untouchedLegacyRowId = insertCourse("40", "0", "30", "0", "30");
        String alreadyConfiguredRowId = insertCourse("40", "20", "30", "10", "30");
        String designZeroRowId = insertCourse("25", "25", "25", "25", "0");
        String untouchedLegacyTeamRowId = insertGroupConfig("0.4", "0", "0.3", "0", "0.3");
        String alreadyConfiguredTeamRowId = insertGroupConfig("0.4", "0.2", "0.3", "0.1", "0.3");

        runMigration();

        // A: untouched legacy row -> document absorbs design, active total preserved at 100.
        CourseRow untouchedLegacy = readCourse(untouchedLegacyRowId);
        assertThat(untouchedLegacy.code).isEqualByComparingTo("40");
        assertThat(untouchedLegacy.test).isEqualByComparingTo("0");
        assertThat(untouchedLegacy.document).isEqualByComparingTo("60");
        assertThat(untouchedLegacy.research).isEqualByComparingTo("0");
        assertThat(untouchedLegacy.design).isEqualByComparingTo("0");
        assertThat(activeSum(untouchedLegacy)).isEqualByComparingTo("100");

        // B: already validly configured 4-slice row (active sum already 100) -> NOT folded again;
        // design left untouched/inactive rather than corrupting the active sum to 130.
        CourseRow alreadyConfigured = readCourse(alreadyConfiguredRowId);
        assertThat(alreadyConfigured.code).isEqualByComparingTo("40");
        assertThat(alreadyConfigured.test).isEqualByComparingTo("20");
        assertThat(alreadyConfigured.document).isEqualByComparingTo("30");
        assertThat(alreadyConfigured.research).isEqualByComparingTo("10");
        assertThat(alreadyConfigured.design).isEqualByComparingTo("30");
        assertThat(activeSum(alreadyConfigured)).isEqualByComparingTo("100");

        // C: design already 0 -> pure no-op.
        CourseRow designZero = readCourse(designZeroRowId);
        assertThat(designZero.code).isEqualByComparingTo("25");
        assertThat(designZero.test).isEqualByComparingTo("25");
        assertThat(designZero.document).isEqualByComparingTo("25");
        assertThat(designZero.research).isEqualByComparingTo("25");
        assertThat(designZero.design).isEqualByComparingTo("0");

        // D: Team scale (0..1) equivalent of A and B.
        GroupConfigRow untouchedLegacyTeam = readGroupConfig(untouchedLegacyTeamRowId);
        assertThat(untouchedLegacyTeam.code).isEqualByComparingTo("0.4");
        assertThat(untouchedLegacyTeam.test).isEqualByComparingTo("0");
        assertThat(untouchedLegacyTeam.document).isEqualByComparingTo("0.6");
        assertThat(untouchedLegacyTeam.research).isEqualByComparingTo("0");
        assertThat(untouchedLegacyTeam.design).isEqualByComparingTo("0");
        assertThat(activeSum(untouchedLegacyTeam)).isEqualByComparingTo("1");

        GroupConfigRow alreadyConfiguredTeam = readGroupConfig(alreadyConfiguredTeamRowId);
        assertThat(alreadyConfiguredTeam.code).isEqualByComparingTo("0.4");
        assertThat(alreadyConfiguredTeam.test).isEqualByComparingTo("0.2");
        assertThat(alreadyConfiguredTeam.document).isEqualByComparingTo("0.3");
        assertThat(alreadyConfiguredTeam.research).isEqualByComparingTo("0.1");
        assertThat(alreadyConfiguredTeam.design).isEqualByComparingTo("0.3");
        assertThat(activeSum(alreadyConfiguredTeam)).isEqualByComparingTo("1");
    }

    @Test
    void v37NeverContainsDestructiveStatements() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V37__fold_legacy_design_weight_into_document.sql"
        ), StandardCharsets.UTF_8).toLowerCase();

        assertThat(sql).doesNotContain(
                "drop table",
                "drop column",
                "delete from course",
                "delete from project_group_weight_config",
                "insert into course",
                "insert into project_group_weight_config"
        );
    }

    private void runMigration() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V37__fold_legacy_design_weight_into_document.sql"
        ), StandardCharsets.UTF_8);
        RunScript.execute(connection, new StringReader(sql));
    }

    private String insertCourse(String code, String test, String document, String research, String design) throws Exception {
        String id = UUID.randomUUID().toString();
        try (var statement = connection.prepareStatement(
                "INSERT INTO course (id, code_contribution_weight, test_contribution_weight, "
                        + "document_contribution_weight, research_contribution_weight, design_contribution_weight) "
                        + "VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            statement.setString(1, id);
            statement.setBigDecimal(2, new BigDecimal(code));
            statement.setBigDecimal(3, new BigDecimal(test));
            statement.setBigDecimal(4, new BigDecimal(document));
            statement.setBigDecimal(5, new BigDecimal(research));
            statement.setBigDecimal(6, new BigDecimal(design));
            statement.executeUpdate();
        }
        return id;
    }

    private String insertGroupConfig(String code, String test, String document, String research, String design) throws Exception {
        String id = UUID.randomUUID().toString();
        try (var statement = connection.prepareStatement(
                "INSERT INTO project_group_weight_config (id, code_weight, test_weight, "
                        + "document_weight, research_weight, design_weight) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            statement.setString(1, id);
            statement.setBigDecimal(2, new BigDecimal(code));
            statement.setBigDecimal(3, new BigDecimal(test));
            statement.setBigDecimal(4, new BigDecimal(document));
            statement.setBigDecimal(5, new BigDecimal(research));
            statement.setBigDecimal(6, new BigDecimal(design));
            statement.executeUpdate();
        }
        return id;
    }

    private CourseRow readCourse(String id) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT code_contribution_weight, test_contribution_weight, document_contribution_weight, "
                        + "research_contribution_weight, design_contribution_weight FROM course WHERE id = ?"
        )) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new CourseRow(
                        resultSet.getBigDecimal(1), resultSet.getBigDecimal(2),
                        resultSet.getBigDecimal(3), resultSet.getBigDecimal(4), resultSet.getBigDecimal(5)
                );
            }
        }
    }

    private GroupConfigRow readGroupConfig(String id) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT code_weight, test_weight, document_weight, research_weight, design_weight "
                        + "FROM project_group_weight_config WHERE id = ?"
        )) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new GroupConfigRow(
                        resultSet.getBigDecimal(1), resultSet.getBigDecimal(2),
                        resultSet.getBigDecimal(3), resultSet.getBigDecimal(4), resultSet.getBigDecimal(5)
                );
            }
        }
    }

    private BigDecimal activeSum(CourseRow row) {
        return row.code.add(row.test).add(row.document).add(row.research);
    }

    private BigDecimal activeSum(GroupConfigRow row) {
        return row.code.add(row.test).add(row.document).add(row.research);
    }

    private record CourseRow(BigDecimal code, BigDecimal test, BigDecimal document, BigDecimal research, BigDecimal design) {
    }

    private record GroupConfigRow(BigDecimal code, BigDecimal test, BigDecimal document, BigDecimal research, BigDecimal design) {
    }
}
