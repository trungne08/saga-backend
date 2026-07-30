# SAGA Backend

Spring Boot backend for SAGA. Production integration setup, API contracts,
migration preflight, and operational guidance are documented in
[`docs/integrations/README.md`](docs/integrations/README.md). Railway build,
configuration, migration, and first-deployment controls are documented in
[`docs/deployment/railway.md`](docs/deployment/railway.md). Local-only Jira and
GitHub development is documented in
[`docs/development/local-integrations.md`](docs/development/local-integrations.md).
The guarded local Flyway workflow is documented in
[`docs/development/local-database-migration.md`](docs/development/local-database-migration.md).

Copy `.env.example` to `.env`, fill secrets locally, then run:

```powershell
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Never commit `.env`, OAuth tokens, GitHub private keys, webhook secrets, or
database credentials.
