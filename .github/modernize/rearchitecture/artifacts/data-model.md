# Data model overview

Primary relational / JPA entities observed in the codebase:
- `Course`, `Class`, `Semester`, `Subject`, `Lecturer`
- `Student`, `Team`, `TeamMember`, `Project`
- `GitRepo`, `GitHubInstallation`, `JiraBoard`
- `WebhookReceipt`, `IdentityMap`, `IdentityMappingHistory`
- `SyncJobLog`, `SystemAuditLog`, `Task`, `Comment`, `CommitData`, `PullRequest`, `PrReview`

Key relationships to preserve in migration design:
- `Course` is linked to `Subject`, `Class`, `Semester`, `Lecturer`.
- `Team` and `TeamMember` manage project assignment membership.
- `Project` is the ownership boundary for Jira/GitHub integration resources.
- `IdentityMap` ties a `Student` to a provider-specific external account ID, while `IdentityMappingHistory` records the audit trail of those state changes.
- `WebhookReceipt` is the durable deduplication and replay envelope for provider-sent webhook traffic.
- `JiraBoard` and `GitRepo` are provider resource records tied to a `Project`.

Key-entity summary:
- `Student` is the authenticated local profile identity for personal provider mappings.
- `Project` is the project-level integration root for Jira and GitHub.
- `WebhookReceipt` is the critical durable boundary bridging public webhooks to async reconciliation workers.
- `IdentityMap` is the bridge between local academic identity and external integration identity.
