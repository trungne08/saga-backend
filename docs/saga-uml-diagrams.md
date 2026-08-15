# UML Diagrams Required for the SAGA System

> Updated for the current Backend project flow: Project V1, Jira/GitHub
> integrations, asynchronous synchronization, and GitHub traceability. Diagrams
> must show Team/Course-scoped authorization and must not model Project as a
> standalone CRUD module.

## 1. Current Project Flow

### 1.1. Core business relationships

- A `Course` contains multiple `Team` records through `TeamMember`; each `Project` belongs to one `Team`.
- Every `Project` requires a `ProjectType`; the catalog is managed by ADMIN and is not hard-coded.
- A Project may have multiple GitHub repositories and one canonical Jira link per provider identity.
- A Project contains canonical local Sprint and Task snapshots. Sprint/Task data may be synchronized from Jira or created/updated through Jira write-through operations.
- A `Task` is linked to GitHub Issues through an explicit many-to-many link; PR/Commit data is used for traceability when synchronized data is available.
- Contribution weights prioritize `Project + Team` configuration and fall back to Course configuration.

### 1.2. Project creation and configuration

1. ADMIN creates or reviews a `ProjectType`.
2. A Lecturer creates a Project for a Team through `POST /api/teams/{teamId}/projects`; `projectTypeId` is required.
3. The Backend validates the actor, Team, Course, and existing Project state before persisting the Project.
4. An authorized manager can read/update Project details, view dashboard statistics, and configure group weights through `PUT /api/projects/{projectId}/group-weights`.
5. Project DELETE is not currently supported and must not be modeled as a complete use case.

### 1.3. Jira/GitHub integration

- Jira: OAuth/credential flow → discover the canonical Jira Project and Scrum board → validate ownership/provider identity → persist `JiraBoard`.
- GitHub: install/verify the GitHub App → select multiple repositories → persist local links → start backfill/synchronization.
- Callbacks return safe results to the frontend; credentials and raw provider payloads must not appear in responses or logs.
- Disconnect operations retain required local history and do not hard-delete all traceability data.

### 1.4. Sprint and Task management

- Read operations primarily use canonical local snapshots.
- Create/update/delete operations use an idempotent Jira write operation, canonical GET/upsert, and fresh-read confirmation before `COMPLETED`.
- Tasks support transitions, assignees, Sprint/backlog assignment, and estimation. Sprints support create, update, start, close, and delete.
- Delete is implemented as a tombstone or association removal; audit, contribution, and peer-review data are not hard-deleted.
- A provider mutation that cannot yet be confirmed locally must enter recovery; retries must not blindly repeat the provider mutation.

### 1.5. Synchronization and traceability

1. A sync job may be created by OAuth/linking, webhook, scheduler, reconciliation, or manual sync.
2. A worker claims the job, calls the provider, upserts Jira/GitHub snapshots, and records safe diagnostics.
3. Project GitHub Issues are read from local data; an authorized manager can link/unlink an Issue to a Task. Link/unlink changes only the SAGA local database.
4. Traceability consists of Planning (Jira Task), Development Tracking (GitHub Issue/PR/Commit), and Implementation (commit/review details when available).
5. Project timelines and Task traceability must be bounded/paginated and must not recursively fetch the entire graph.

## 2. Class Diagrams by Business Flow

### 2.1. Scope

The Class Diagram set must cover the entire SAGA system, divided into coherent
diagrams by business flow. Together, these diagrams form the complete system
Class Diagram. Each diagram should show the relevant Controller/API, Service,
Repository, Entity/DTO, security, and integration classes.

The following layers and cross-cutting concerns must be covered across the set:

- Controller/API layer
- Service/use-case layer
- Repository layer
- Entity and DTO layer
- Authentication, authorization, and security
- Academic Management and Course/Team management
- Project, Sprint, and Task management
- Jira/GitHub integration, provider adapters, and OAuth callbacks
- Sync jobs, webhooks, write operations, and recovery
- Peer Review, contribution, analytics, and early warning
- Notification, invitation, and delivery processing
- AI Agent gateway, projections, and internal-tool boundary

Every diagram should show the Controller → Service/Use Case → Repository →
Entity/DTO flow where applicable. Keep class names and relationships consistent
across diagrams, especially for shared entities and cross-flow dependencies.

### 2.2. Class Diagram: Authentication and Authorization flow

- `AuthController`, `AuthenticatedProfile`, `AuthenticatedIdentity`
- `SagaPrincipal`, `SecurityConfig`, `OidcIdentityService`, `CurrentAccountStatusService`
- `ADMIN`, `LECTURER`, and `STUDENT` role relationships
- session, CSRF, account-status, and authorization relationships

### 2.3. Class Diagram: Academic Management and Course Enrollment flow

- `Semester`, `Subject`, `Course`, `ActiveSemesterSetting`
- academic controllers, services, and repositories
- student import, `Team`, `TeamMember`, invitation/outbox, and Course authorization
- `ExcelImportService`, `CourseStudentManagementService`, `StudentInvitationOutboxService`

### 2.4. Class Diagram: Team and Project Creation flow

- `Project`, `ProjectType`, `Team`, `TeamMember`, `Course`
- `TeamProjectController`, `ProjectTypeController` (list only), `ProjectDetailController`
- `TeamProjectService`, `ProjectTypeService`, `ProjectDetailService`
- `ProjectDashboardStatsService`, `ProjectRepository`, `ProjectTypeRepository`
- Project creation, ownership, detail, dashboard stats, and group-weight relationships

### 2.5. Class Diagram: Jira Integration and Project Linking flow

- `ProjectIntegrationController`, `ProjectIntegrationCallbackController`
- `ProjectIntegrationService`, `ProjectIntegrationSessionStore`
- `JiraBoardResolutionService`, `JiraBoardLinkPersistenceService`, `JiraCredentialService`
- `GitHubProjectReadService`, `ManualProjectSyncService`
- `JiraBoard`, `SyncJob`, `SyncJobLog`, OAuth callback/result-store classes
- Jira resource discovery, board resolution, ownership validation, link, disconnect, and sync relationships

### 2.6. Class Diagram: GitHub Integration and Repository Sync flow

- `ProjectIntegrationController`, `ProjectIntegrationCallbackController`
- `ProjectIntegrationService`, `GitHubProjectReadService`, `ManualProjectSyncService`
- `GitRepo`, GitHub installation/repository classes, `SyncJob`, `SyncJobLog`
- GitHub App setup, repository selection, reconnect/disconnect, webhook, and backfill relationships

### 2.7. Class Diagram: Jira Sprint and Task Management flow

- `ProjectSprintController`, `ProjectTaskReadController`
- `ProjectSprintService`, `ProjectTaskReadService`
- `JiraSprintWriteService`, `JiraTaskWriteService`
- `JiraWriteOperationService`, `JiraWriteRecoveryService`, `JiraTaskSprintFinalizationService`
- `Sprint`, `Task`, `JiraWriteOperation`, `JiraSprintUpsertService`, `JiraIssueUpsertService`
- transition, assignee, Sprint/backlog, estimation, tombstone, idempotency, canonical confirmation, and recovery relationships

### 2.8. Class Diagram: GitHub Issue and Project Traceability flow

- `ProjectGitHubIssueController`, `ProjectGitHubReadController`, `ProjectTraceabilityController`
- `GitHubTraceabilityService`, `GitHubIssueReadService`, `GitHubDataUpsertService`
- `GitIssue`, `GitRepo`, `PullRequest`, `CommitData`
- `TaskGitIssueLink`, `GitIssueCommitLink`, `GitIssuePullRequestLink`
- Webhook verifiers/receipt processors and GitHub sync/backfill services
- Project timeline, bounded traceability, Issue detail, Task link/unlink, PR, and Commit relationships

### 2.9. Class Diagram: Peer Review and Contribution Evaluation flow

- `ProjectGroupWeightConfig`, `ProjectGroupWeightConfigService`
- `TeamContributionService`, `ContributionCalculationService`
- `PeerReview`, `PeerReviewDetail`, `RubricTemplate`, `PeerReviewService`
- contribution inputs from Task, Commit, Peer Review, Course weights, and Project/Team group weights

### 2.10. Class Diagram: Notification and Invitation Delivery flow

- `Notification`, `NotificationBroadcast`, `NotificationDelivery`
- `StudentCourseInvitation`, invitation/outbox services
- notification broadcast, delivery claim/processor, Firebase adapter, and recipient relationships

### 2.11. Class Diagram: Lecturer Analytics and Early Warning flow
- `LecturerAnalyticsController`, `LecturerTeamAnalyticsQueryService`, `LecturerStudentAnalyticsQueryService`, `CourseEarlyWarningQueryService`
- Dashboard, progress, burndown, heatmap, contribution, and early-warning DTOs

### 2.12. Class Diagram: AI Agent and Internal Tool flow

- public AI gateway/controller and conversation/artifact classes
- `AgentDelegationService`, `AgentToolProjectionService`, `AgentTaskProposalValidationService`
- internal tool controllers, trust boundary, authorization, and project/task context projections

### 2.13. Shared classes across flows

- `SagaPrincipal` and authorization services are referenced by every protected flow.
- `Project`, `Team`, `Course`, `Task`, `Sprint`, `GitRepo`, `SyncJob`, and `Notification` may appear in multiple diagrams.
- Do not duplicate a shared class with conflicting attributes or relationship names.

## 3. Activity Diagrams

### 3.1. Overall Project lifecycle

Course/Team → select ProjectType → create Project → configure Project → link Jira/GitHub → sync/backfill → manage Sprint/Task → link traceability → analytics/contribution.

### 3.2. Create a Project for a Team with a ProjectType

Actor → validate Course/Team authorization → validate `projectTypeId` → check for duplicates → create Project → return Project detail.

### 3.3. Link a Jira Project and board

OAuth/credential → retrieve resources → resolve canonical Jira Project → discover board → validate ownership/conflicts → persist `JiraBoard` → register webhook/sync.

### 3.4. Link GitHub repositories and run initial backfill

GitHub App setup → verify installation → select repositories → persist local links → enqueue backfill → synchronize branches/Issues/PRs/Commits → update job status.

### 3.5. Create/update a Sprint or Task through Jira write-through

Browser request + CSRF + idempotency key → validate scope → resolve provider metadata → create `JiraWriteOperation` → call Jira → canonical GET/upsert → fresh-read confirmation → `COMPLETED` or recovery-required.

### 3.6. Synchronization and recovery

Webhook/manual/scheduler → claim `SyncJob` → read provider data → upsert locally → finalize; timeout/failure/unconfirmed remote success → controlled retry/recovery.

### 3.7. GitHub Issue–Task traceability

Read local Issue → select Task → validate same Project → create local link → read linked PR/Commit/timeline with bounds; unlink removes only the local link.

### 3.8. Additional activities

Login/authentication; student import into Course/Team; peer review/contribution;
notification delivery; lecturer dashboard, burndown, heatmap, progress, and early warning.

## 4. State Diagrams

### 4.1. `JiraWriteOperation`

`PENDING → IN_PROGRESS → REMOTE_SUCCEEDED → COMPLETED`

Error/recovery branches: `FAILED`, `RETRY`, and `RECOVERY_REQUIRED`.
`REMOTE_SUCCEEDED` does not mean `COMPLETED` until canonical local confirmation succeeds.

### 4.2. `SyncJob`

`PENDING → CLAIMED/RUNNING → SUCCEEDED` or `FAILED → RETRY`.
Show stale-job recovery and distinguish sync jobs from provider write operations.

### 4.3. Jira Sprint

Common provider states: `future → active → closed`; the local response may expose
`null`. Do not replace these with a date-derived state.

### 4.4. Task

Task stores Jira-provided status/metadata snapshots; do not hard-code
`Todo → In Progress → Review → Done`. Show `blocked/cancelled` only when the
corresponding status exists on the board.

### 4.5. Webhook receipt and NotificationDelivery

- Webhook receipt: received → authenticated → claimed → processed/failed/retry.
- Notification delivery: `Queued → Claimed → Sending → Delivered` or `Failed → Retry`.

### 4.6. Other lifecycles

`StudentCourseInvitation`: Created → Sent → Claimed → Accepted/Expired/Cancelled.
`PeerReview`: Draft → Submitted → Evaluated → Finalized/Reopened.

## 5. Priority Sequence Diagrams for Project

1. Create a Project for a Team with a `ProjectType`.
2. Update Project details and group weights.
3. Link Jira: OAuth → canonical Project → board → persistence.
4. Link GitHub: App setup → repository selection → backfill.
5. Create a Jira Task with idempotency and canonical confirmation.
6. Create a Jira Sprint, start/close it, and synchronize its state.
7. Recover a write operation after remote success.
8. Process a webhook/reconciliation update into the local snapshot.
9. Run a manual Project sync and inspect sync history.
10. Link/unlink a GitHub Issue to/from a Task.
11. View Task traceability and a bounded Project timeline.
12. View Project/Team dashboard data and contribution using group-weight fallback.

Other platform sequences: login, student import, peer review, notification, and lecturer analytics.

## 6. Recommended Implementation Order

1. Class Diagrams for Authentication, Academic, Team/Project, and Integration flows.
2. Activity Diagrams for Project lifecycle, integrations, and Jira write-through.
3. State Diagrams for `JiraWriteOperation`, `SyncJob`, Sprint, and webhook receipt.
4. Sequence Diagrams for the 12 priority Project flows above.
5. Class Diagrams for peer review/contribution, notification, analytics, and AI Agent flows.
6. Additional Activity/Sequence Diagrams for the same flows.

## 7. Recommended Diagram Set

- 10–11 Class Diagrams, one for each major business flow, collectively covering the entire SAGA system.
- Optional overview diagram showing only major bounded areas and their dependencies.
- 7–9 Activity Diagrams.
- 5–7 State Diagrams.
- 10–12 Sequence Diagrams focused on Project and integrations.
