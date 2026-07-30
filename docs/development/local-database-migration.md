# Local integration database migration

This workflow is intentionally local-only. It does not deploy or change
Railway, AWS, Atlassian, GitHub, or Aiven Console settings. The explicitly
confirmed migration may change only the selected development database. The
scripts do not run unless `SPRING_PROFILES_ACTIVE` includes `local` and the
target is loopback or has been explicitly approved with `-ApprovedHost`.

It uses the project's MySQL JDBC driver through Maven Wrapper. No `mysql`,
`mysql.exe`, `mysqlsh`, MySQL Workbench, or local MySQL Server is required.
The scripts read only `DATABASE_JDBC_URL`, `DATABASE_USERNAME`, and
`DATABASE_PASSWORD`; they do not use `AIVEN_*` fallbacks or store credentials.

## Read-only preflight

```powershell
.\scripts\check-local-integration-schema.ps1 -ApprovedHost <AIVEN_HOST>
```

For a loopback database, `-ApprovedHost` may be omitted. A remote Aiven
development host always needs its exact hostname in `-ApprovedHost`.

The preflight compiles and runs
`com.saga.be.tools.LocalIntegrationSchemaTool` from `src/test/java` with the
test classpath. That class is excluded from the production JAR. It opens a JDBC
connection, then uses JDBC metadata plus a read-only `SELECT` against Flyway
history. It reports connectivity, Flyway history, successful V2 status, the
three new integration tables, and `comment.author_external_id`. It never
creates, alters, or drops schema objects.

## Explicit local migration

First take a backup and read the preflight output. Then run:

The script displays host, database, and active profile but never the password.
It refuses production profiles and Railway/AWS RDS/production-like host names.
A non-loopback host needs an exact `-ApprovedHost` value, including an Aiven
development host. It also requires the literal confirmation
`MIGRATE_LOCAL_V2`.

For a manually approved non-loopback development database:

```powershell
.\scripts\run-local-integration-migration.ps1 -ApprovedHost <AIVEN_HOST>
```

The script checks for `V2__integration_identity_and_sync.sql`, checks whether
`flyway_schema_history` exists, and refuses to proceed if V2 is already
successful. Before any Flyway command, the same JDBC tool validates the legacy
schema: if history is absent, all 13 legacy tables must exist. It then runs
`flyway:baseline` at version 1 through `mvnw.cmd`, followed by
`flyway:migrate`. If history already exists, it runs only `flyway:migrate`.
The JDBC tool does not run Flyway or modify schema; only this explicitly
confirmed script invokes the configured Flyway Maven Plugin.

The password is placed in a temporary Flyway config file, never printed or sent
as a Maven command-line property, and the file is removed in a `finally` block.
Any failed Maven command stops the script immediately.

Expected history for the first successful legacy migration:

| rank | version | type | script | success |
|---:|---:|---|---|---:|
| 1 | 1 | BASELINE | `<< Flyway Baseline >>` | 1 |
| 2 | 2 | SQL | `V2__integration_identity_and_sync.sql` | 1 |

Hibernate remains `ddl-auto=validate`. Do not use `ddl-auto=update` as a
migration mechanism. `V2` must be applied before the integration-enabled
application can validate the existing schema.
