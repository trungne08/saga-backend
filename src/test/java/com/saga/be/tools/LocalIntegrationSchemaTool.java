package com.saga.be.tools;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local-development-only JDBC preflight. This class is deliberately located in
 * {@code src/test}: it is never included in the Spring Boot production JAR.
 */
public final class LocalIntegrationSchemaTool {

    private static final Pattern MYSQL_URL = Pattern.compile(
            "^jdbc:mysql://(?<host>[^/:?]+)(?::(?<port>\\d+))?/(?<database>[^?;/]+).*$",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> REQUIRED_INTEGRATION_TABLES = List.of(
            "identity_mapping_history", "github_installation", "webhook_receipt");
    private static final List<String> LEGACY_TABLES = List.of(
            "identity_map", "project", "jira_board", "git_repo", "task", "sprint",
            "git_issue", "commit_data", "pull_request", "pr_review", "comment",
            "sync_job_log", "student");

    private LocalIntegrationSchemaTool() {
    }

    public static void main(String[] args) {
        try {
            Invocation invocation = Invocation.parse(args);
            String profile = requireEnvironment("SPRING_PROFILES_ACTIVE");
            assertLocalProfile(profile);
            DatabaseTarget target = parseDatabaseTarget(requireEnvironment("DATABASE_JDBC_URL"));
            assertSafeTarget(target, invocation.approvedHost());

            String username = requireEnvironment("DATABASE_USERNAME");
            String password = requireEnvironment("DATABASE_PASSWORD");
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(target.jdbcUrl(), username, password)) {
                SchemaStatus status = inspect(connection, target);
                printStatus(target, status);
                if (invocation.mode() == Mode.MIGRATE) {
                    assertMigrationPreconditions(status);
                    System.out.println("migration_preflight=READY");
                }
            }
        } catch (Exception exception) {
            System.err.println("Local integration schema tool failed: " + safeMessage(exception));
            System.exit(2);
        }
    }

    static DatabaseTarget parseDatabaseTarget(String jdbcUrl) {
        Matcher matcher = MYSQL_URL.matcher(Objects.requireNonNull(jdbcUrl, "DATABASE_JDBC_URL is required"));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("DATABASE_JDBC_URL must be a jdbc:mysql://host[:port]/database URL");
        }
        String host = matcher.group("host");
        String database = matcher.group("database");
        if (!host.matches("[A-Za-z0-9.-]+") || !database.matches("[A-Za-z0-9_$-]+")) {
            throw new IllegalArgumentException("The database host or name contains unsupported characters");
        }
        return new DatabaseTarget(jdbcUrl, host, database);
    }

    static void assertLocalProfile(String profile) {
        List<String> profiles = Arrays.stream(profile.split("[,;\\s]+"))
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        if (profiles.contains("prod") || profiles.contains("production")) {
            throw new IllegalArgumentException("Refusing to run with a production profile");
        }
        if (!profiles.contains("local")) {
            throw new IllegalArgumentException("Refusing to run unless SPRING_PROFILES_ACTIVE includes local");
        }
    }

    static void assertSafeTarget(DatabaseTarget target, Optional<String> approvedHost) {
        String host = target.host().toLowerCase(Locale.ROOT);
        if (host.matches(".*(railway|amazonaws|rds\\.|production|prod).*")) {
            throw new IllegalArgumentException("Refusing a Railway, AWS RDS, or production-like database host");
        }
        boolean loopback = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        boolean approved = approvedHost
                .map(value -> value.equalsIgnoreCase(target.host()))
                .orElse(false);
        if (!loopback && !approved) {
            throw new IllegalArgumentException(
                    "Refusing a non-local host. Re-run with --approved-host <exact-dev-host> after manual approval.");
        }
    }

    private static SchemaStatus inspect(Connection connection, DatabaseTarget target) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        boolean historyExists = tableExists(metadata, target.database(), "flyway_schema_history");
        boolean v2Applied = historyExists && isV2Applied(connection);
        List<String> presentIntegrationTables = REQUIRED_INTEGRATION_TABLES.stream()
                .filter(table -> tableExists(metadata, target.database(), table))
                .toList();
        boolean authorExternalIdExists = columnExists(metadata, target.database(), "comment", "author_external_id");
        int legacyTableCount = (int) LEGACY_TABLES.stream()
                .filter(table -> tableExists(metadata, target.database(), table))
                .count();
        return new SchemaStatus(historyExists, v2Applied, presentIntegrationTables,
                authorExternalIdExists, legacyTableCount);
    }

    private static boolean tableExists(DatabaseMetaData metadata, String catalog, String table) {
        try (ResultSet resultSet = metadata.getTables(catalog, null, table, new String[]{"TABLE"})) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect table metadata", exception);
        }
    }

    private static boolean columnExists(DatabaseMetaData metadata, String catalog, String table, String column) {
        try (ResultSet resultSet = metadata.getColumns(catalog, null, table, column)) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to inspect column metadata", exception);
        }
    }

    private static boolean isV2Applied(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = ?")) {
            statement.setString(1, "2");
            statement.setBoolean(2, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private static void printStatus(DatabaseTarget target, SchemaStatus status) {
        System.out.println("connection_status=OK");
        System.out.println("target_host=" + target.host());
        System.out.println("target_database=" + target.database());
        System.out.println("flyway_history_exists=" + status.historyExists());
        System.out.println("v2_applied=" + status.v2Applied());
        for (String table : REQUIRED_INTEGRATION_TABLES) {
            System.out.println("integration_table." + table + "="
                    + (status.presentIntegrationTables().contains(table) ? "PRESENT" : "MISSING"));
        }
        System.out.println("comment.author_external_id=" + (status.authorExternalIdExists() ? "PRESENT" : "MISSING"));
        System.out.println("legacy_table_count=" + status.legacyTableCount());
    }

    private static void assertMigrationPreconditions(SchemaStatus status) {
        if (status.v2Applied()) {
            throw new IllegalStateException("Migration cancelled: V2 is already recorded as successful");
        }
        if (!status.historyExists() && status.legacyTableCount() != LEGACY_TABLES.size()) {
            throw new IllegalStateException("Migration cancelled: expected " + LEGACY_TABLES.size()
                    + " legacy tables before baseline, found " + status.legacyTableCount());
        }
    }

    private static String requireEnvironment(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(variable + " is required");
        }
        return value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    enum Mode { CHECK, MIGRATE }

    record DatabaseTarget(String jdbcUrl, String host, String database) {
    }

    record SchemaStatus(boolean historyExists, boolean v2Applied, List<String> presentIntegrationTables,
                        boolean authorExternalIdExists, int legacyTableCount) {
    }

    record Invocation(Mode mode, Optional<String> approvedHost) {
        static Invocation parse(String[] args) {
            if (args.length < 1 || args.length > 3) {
                throw new IllegalArgumentException("Usage: CHECK|MIGRATE [--approved-host <exact-host>]");
            }
            Mode mode;
            try {
                mode = Mode.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Mode must be CHECK or MIGRATE");
            }
            if (args.length == 1) {
                return new Invocation(mode, Optional.empty());
            }
            if (args.length != 3 || !"--approved-host".equals(args[1]) || args[2].isBlank()) {
                throw new IllegalArgumentException("Usage: CHECK|MIGRATE [--approved-host <exact-host>]");
            }
            return new Invocation(mode, Optional.of(args[2]));
        }
    }
}
