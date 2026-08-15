# Tech stack

Runtime and framework evidence:
- Java 17 (`pom.xml:30-33`)
- Spring Boot 4.1.0 (`pom.xml:6-11`)
- Spring WebMVC (`pom.xml:59-64`)
- Spring Security + OAuth2 client + Actuator (`pom.xml:65-77`)
- Spring Data JPA + MongoDB + Flyway (`pom.xml:35-57`)
- MySQL connector + Flyway MySQL (`pom.xml:41-57`)
- Springdoc OpenAPI UI 3.0.3 (`pom.xml:89-93`)
- Apache POI 5.2.3 for Excel import (`pom.xml:116-120`)

Infrastructure/operations evidence:
- Local run is documented in `README.md:13-18`.
- Docs locate integration security and webhook details in `docs/integrations/README.md`.
- Production environment is configured via environment variables and `.env` as documented in README/docs.

Migration blockers / constraints:
- Mixed relational and document persistence: MySQL via JPA and MongoDB via Spring Data MongoDB.
- OAuth/OIDC frontend behavior is backed by session-based Spring Security and Cognito flow context.
- Webhook public ingress is security-sensitive and signed/authenticated on the raw payload.
- Provider integrations are a sizeable external-boundary subsystem; the project root is a backend-first service rather than a single CRUD app.
