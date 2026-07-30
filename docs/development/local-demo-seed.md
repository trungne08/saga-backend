# Local demo integration seed

The local demo seed is disabled by default and is available only with the
`local` Spring profile. It never creates a Cognito user. Before enabling it,
sign in once with the real SAGA Student account so its Student profile exists.

Add the following to the uncommitted `.env.local` file:

```properties
LOCAL_DEMO_SEED_ENABLED=true
LOCAL_DEMO_LEADER_COGNITO_SUB=<value-from-/api/auth/me>
```

Then start the local application normally:

```powershell
.\mvnw.cmd spring-boot:run
```

The seeder creates or reuses only records marked with the `SAGA Local Demo`
names/codes. It creates a demo course hierarchy, a demo instructor record with
no Cognito identity (required because Course requires an instructor), a Team,
a LEADER TeamMember for the configured existing Student, and the Team Project
through `TeamProjectService`.

On success the application log prints the `teamId` and `projectId` only. Use
the printed `projectId` for the project Jira/GitHub integration endpoints.
Set `LOCAL_DEMO_SEED_ENABLED=false` after setup; later runs with the same
leader are idempotent and do not duplicate the demo records.
