# Local Jira and GitHub integration development

This runbook is local-only. It does not change Railway, provider consoles, AWS,
Aiven, or any database outside the database intentionally selected by the
developer.

## Start safely with integrations disabled

Copy `.env.local.example` to `.env.local`, then populate only the local database
and Cognito values required for the APIs being tested. The application imports
`.env.local` automatically.

```powershell
Copy-Item .env.local.example .env.local
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

Local defaults are:

```text
JIRA_INTEGRATION_ENABLED=false
GITHUB_INTEGRATION_ENABLED=false
INTEGRATION_RECONCILIATION_ENABLED=false
```

With either provider disabled, its REST client is not created, scheduled work
does not call that provider, OAuth start endpoints return HTTP 503 with
`error=INTEGRATION_NOT_CONFIGURED`, and webhook endpoints return the same safe
error. Normal Cognito and application APIs continue to run.

## Callback URLs

The local callback origin is independent from public webhooks:

```text
PUBLIC_BASE_URL=http://localhost:8080
JIRA_CALLBACK_URL=http://localhost:8080/api/integrations/jira/callback
GITHUB_PERSONAL_CALLBACK_URL=http://localhost:8080/api/me/integrations/github/callback
GITHUB_PROJECT_CALLBACK_URL=http://localhost:8080/api/integrations/github/project/callback
GITHUB_SETUP_URL=http://localhost:8080/api/integrations/github/setup
```

The local profile permits HTTP callbacks. Production keeps its HTTPS-only public
origin validation.

## Webhook tunnel

Jira and GitHub cannot send webhooks to localhost. Set only this optional value
after creating an HTTPS tunnel yourself:

```text
LOCAL_WEBHOOK_BASE_URL=https://your-public-tunnel.example
```

When the explicit provider webhook URL is blank, SAGA derives:

```text
JIRA_WEBHOOK_PUBLIC_URL=https://your-public-tunnel.example/api/webhooks/jira
GITHUB_WEBHOOK_PUBLIC_URL=https://your-public-tunnel.example/api/webhooks/github
```

No tunnel provider or domain is hard-coded. When no tunnel is configured, keep
the provider flags disabled; the application still starts normally.

## Enabling a provider locally

Set only the provider being tested to `true`. Startup then requires the complete
credential set and rejects empty or obvious placeholder values such as `VALUE`,
`changeme`, `example-secret`, and `${{REF}}`.

Jira additionally requires its callback URL, scopes, and HTTPS webhook URL.
GitHub additionally requires its App credentials, both callback URLs, setup URL,
and HTTPS webhook URL. Never commit `.env.local`, tokens, private keys, or
webhook secrets.

## Database choices

`V2__integration_identity_and_sync.sql` remains required for integration tables
and columns. Hibernate stays on `ddl-auto=validate`; it will correctly fail if
the selected database has not received V2.

Choose one explicitly:

1. Use a disposable local/dev database and run V2 through Flyway.
2. Use an existing development database only after backup and explicit manual
   approval.

Do not use `ddl-auto=update` as a substitute for the migration. This task does
not run Flyway against any live or remote database.
