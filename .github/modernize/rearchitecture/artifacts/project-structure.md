# Project structure

Project type: Spring Boot REST backend for academic-course coordination and provider integration.

Functional domains:
- Course/class/semester/subject catalog and enrollment structure.
- Team roster and team project ownership.
- Student identity and personal provider-link management.
- Project-level Jira/GitHub integration lifecycle and sync status.
- Webhook ingestion and durable receipt processing.
- Security, audit, and OAuth session handling.

Layer view:
- Web layer: controller package with REST endpoints and DTOs.
- Service layer: business orchestration in service and integration/* package.
- Persistence layer: JPA repositories backed by MySQL + Flyway, plus MongoDB-backed domain.
- Security/auth layer: Spring Security, OAuth2 client, Cognito authorities mapping, session repository, audit handlers.
- Integration boundary: provider clients and webhook verification components.

Key source anchors:
- app boot: `src/main/java/com/saga/be/BeApplication.java:10-18`
- security wiring: `src/main/java/com/saga/be/config/SecurityConfig.java:31-167`
- public endpoints: controllers under `src/main/java/com/saga/be/controller`
- provider workflows: integration packages `src/main/java/com/saga/be/integration/*`
