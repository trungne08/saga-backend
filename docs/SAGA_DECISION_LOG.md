## DEC-106 — Realtime account-disable browser revocation over session SSE

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**.
- Extends DEC-101; does not rewrite it. Request-level `401 ACCOUNT_DISABLED`, session invalidation, `/api/auth/me` gating, self-profile PATCH gating, and disabled OIDC callback behavior remain the hard security fallback.
- New public operation: `GET /api/auth/session-events` (`text/event-stream`). Auth is the current browser `JSESSIONID` only. GET, no CSRF, no Bearer, no actor/profile/session id from the client. Actor is `SagaPrincipal`.
- Canonical browser event is `account-disabled` with `{"code":"ACCOUNT_DISABLED","occurredAt":"<UTC ISO>"}`. INACTIVE and SUSPENDED map to the same code. Payload never includes identity, tokens, session id, Admin identity, or raw status.
- After a successful Admin `PATCH /api/admin/users/{id}/status` to a non-ACTIVE status, an internal `AccountDisabledEvent` is published and handled `@TransactionalEventListener(AFTER_COMMIT)`. Same-JVM open Student/Lecturer SSE subscriptions are pushed, their `HttpSession`s are invalidated, and emitters are completed. A failed emitter cannot roll back the committed status change.
- SSE registry is process-local in-memory, keyed by `ApplicationRole + localProfileId`. No Redis, Spring Session, shared event bus, or JSESSIONID persistence. `GLOBAL_CROSS_INSTANCE_EVENT_BUS = NO`. Replica B detects a disable committed on replica A by a single bounded 5s DB revalidation of currently connected profiles (`MAX_EXPECTED_DETECTION_DELAY <= 5s`). Do not claim millisecond-global revocation.
- ADMIN may keep a heartbeat-only stream; Admin has no AccountStatus. Re-enable does not resurrect an invalidated session and does not send `account-enabled`. FCM/Bell remain notification transport and are not auth-revocation authority.
- CORS stays explicit `FRONTEND_ORIGINS` with credentials; `Last-Event-ID` is allowed for EventSource reconnect. No wildcard, no token in URL.
- Verification: targeted SSE hub/integration + DEC-101/OIDC/self-profile/auth/OpenAPI/CORS tests **PASS**. OpenAPI **154**. `MIGRATION_REQUIRED = NO`. `git diff --check` passes.

## DEC-105 — Public commit review summary is additive on the existing commit list

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**.
- **PUBLIC_API_OPERATION_CHANGED = NO.** No new endpoint. `GET /api/projects/{projectId}/github/repositories/{repositoryId}/commits` keeps its path, `branch`/`page`/`size` params, authorization, and GitHub provider pagination/error semantics unchanged. Internal `/internal/**` commit-review routes remain non-FE-contract.
- **PUBLIC_API_SCHEMA_CHANGED = YES (additive only).** Each `Commit` item gains a nullable `review: CommitReviewSummary | null`. New `CommitReviewSummary` schema: `intentStatus` (exact persisted `CommitReviewIntentStatus`, 8 values, no synthetic public enum), `reviewMode` (nullable, see below), `startedAt`/`completedAt` (both nullable — `CommitReviewIntent.started_at`/`completed_at` are nullable DB columns), and `result` (nullable `{traceabilityStatus, messageQuality, codeQuality, taskAlignment, verdictEligible, verdict, overallStatus}`, all source-backed from persisted `CommitReviewResult`, no `findings[]` in this list projection).
- `review = null` when the provider commit has no matching local `CommitData` (exact repo + full SHA only, never message/short SHA/author/timestamp), or `CommitData` exists but no `CommitReviewIntent` was ever queued for it.
- `FAILED`/`CANCELLED` intents always map to `result = null`; the resolver forces this even if a `CommitReviewResult` row unexpectedly exists, so a processing failure can never be misread as a `NEEDS_CHANGES` verdict. A `COMPLETED` intent without a persisted result also maps to `result = null` rather than fabricating a verdict — an already-known invariant gap, not new behavior introduced here.
- `reviewMode` is populated only from a persisted `CommitReviewResult` (`HISTORICAL_LIGHT`/`TASK_LINKED`/`UNLINKED_ADVISORY`), never from `CommitReviewIntent.reviewMode`'s own two-value `CommitReviewMode` enum (`HISTORICAL_LIGHT`/`LIVE_TASK_AWARE`). Those are different concepts: an intent's `LIVE_TASK_AWARE` mode only resolves into the public `TASK_LINKED` vs `UNLINKED_ADVISORY` distinction once the AI result exists, so exposing it earlier would misrepresent an undecided outcome as decided; the field is `null` until then.
- Batch resolution (`CommitReviewSummaryResolver`) loads local `CommitData`, `CommitReviewIntent`, and `CommitReviewResult` for the entire requested commit page in three bounded repository queries, independent of page size (bounded by the existing `size<=100` cap) — no per-commit query.
- Never exposes `aiJobId`, provider/model identifiers, internal AI URLs, raw error text, or OAuth/session/token material. Enrichment is read-only: it never calls AI, enqueues/polls a review job, or triggers a GitHub sync as a side effect of this GET.
- No migration. No auth/permission change. No Contribution/Jira change.

## DEC-104 — Disabled OIDC browser callback redirects safely to Frontend

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**.
- DEC-101 remains unchanged for API requests: disabled `/api/auth/me`, business `/api/**`, and self-profile PATCH invalidate the session and return JSON `401 ACCOUNT_DISABLED`.
- The OIDC callback is a top-level browser navigation, not an XHR. Only a confirmed INACTIVE or SUSPENDED Student/Lecturer in `CognitoAuthenticationSuccessHandler` is redirected to the configured Frontend failure route instead of receiving the callback JSON body.
- `AUTH_FAILURE_REDIRECT_URI` is a non-secret, absolute HTTP(S) property. It falls back to `AUTH_SUCCESS_REDIRECT_URI` when absent; no Java source hardcodes a Frontend host. Its existing query is discarded and the redirect contains only the allowlisted `error=ACCOUNT_DISABLED`.
- Before redirect, the handler clears `SecurityContext` and invalidates any transient session. It does not save a `SagaPrincipal`, include identity/token/session/OAuth material in the URL, call Cognito, or change local account status.
- ACTIVE login remains unchanged: synchronize, save token-free session principal, then redirect to `AUTH_SUCCESS_REDIRECT_URI`. Identity conflicts, invalid claims, and provider failures keep their existing failure semantics and are not reclassified as ACCOUNT_DISABLED.

## DEC-103 — Self Profile V1 uses local profile authority

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**.
- Active `STUDENT` and `LECTURER` may update only their own local `fullName` and `avatarUrl` with `PATCH /api/auth/me`. The actor is only `SagaPrincipal` from the browser session; the operation accepts no target/actor ID. Session + CSRF are required and Bearer authentication is not used. ADMIN self-update is outside this scope.
- `cognitoSub`, email, application role, Student identity/studentCode, account status, local profile ID, Team role, and Course membership remain read-only. The self endpoint never changes provider state, account status, Team/Course/Project data, or any integration.
- SAGA local profile is authoritative for `fullName` and `avatarUrl`. A newly created profile initializes them from valid OIDC name/picture. On every existing-profile login, OIDC continues to synchronize identity fields but never overwrites locally edited name/avatar.
- `AuthMeResponse` is retained as the canonical read/update response and adds nullable `studentCode`: canonical Student code for STUDENT and null for LECTURER/ADMIN.
- DEC-101 is preserved: PENDING/non-ACTIVE Student and INACTIVE/SUSPENDED Student/Lecturer remain gated before controller execution; disabled sessions receive `401 ACCOUNT_DISABLED` and are invalidated. `GET /api/auth/me` is gated too.
- `avatarUrl` is string-validated only using the OIDC URL sanitizer: absolute HTTP(S), host, no user-info, bounded length. Explicit null clears the local avatar. Backend never fetches the URL or calls a provider. Existing Student/Lecturer `full_name` and nullable `avatar_url` columns are sufficient; no migration is required.

## DEC-102 — Course read status is computed from the Course Semester

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**.
- `GET /api/v1/courses`, `GET /api/v1/courses/{id}`, and ADMIN `POST`/`PUT` return the stable `CourseResponse`. `academicClass` is the sole nested Class JSON name; `clazz` and `academicClazz` are not exposed. The JPA field remains `Course.clazz` mapped through `class_id`; the Class entity/table remains in use.
- Public Course responses expose only active Contribution weights (`codeContributionWeight`, `testContributionWeight`, `documentContributionWeight`, `researchContributionWeight`) and never `designContributionWeight`. Legacy DESIGN storage remains internal for schema validation/history; no migration or Contribution arithmetic change is implied.
- `courseStatus` is a computed-only enum: `OPEN` or `CLOSED`. It is not persisted, has no scheduler, and requires no migration.
- The only status authority is the Course's Semester `startDate`/`endDate`. Its business timezone is `Asia/Ho_Chi_Minh`; an injected `Clock` is converted from `instant` to that zone and then to `LocalDateTime`. Do not use the system-default timezone or the Jira timezone.
- Both boundaries are inclusive: `OPEN` iff Semester and both dates are non-null and `startDate <= now <= endDate`; otherwise (including a missing Semester or either legacy null date) the response is `CLOSED` without an error.
- The Admin Active Semester setting is not consulted and is not a Course-status authority. Authentication/session/CSRF and Course write behavior are unchanged.

## DEC-100 — Agent TASK_CREATE Confirm may compose TASK_SPRINT; EXECUTING re-entry is recovery-only

- Date: 2026-08-17; status: **ACCEPTED / IMPLEMENTED_SOURCE_TEST**. `SAGA_CURRENT_STATE.md` still must not claim this until an explicit CURRENT_STATE update after this source/test snapshot.
- **Does not rewrite DEC-099** (Course isolation), DEC-061/J1F (TASK_SPRINT remote-success recovery), or MEMBER/LEADER permission. No Bearer. No Contribution. No public endpoint. No Jira delete rollback. No auto-select active Sprint. ZERO/MULTIPLE Sprint match must not pick-first.
- **PUBLIC_API_OPERATION_CHANGED = NO.** Confirm remains `POST /api/v1/ai/pending-actions/{actionId}/confirm` with empty body, session + CSRF.
- **PUBLIC_API_SCHEMA_CHANGED = NO.** `ActionExecution` and `ApiErrorResponse` are unchanged. Confirm errors stay `ApiErrorResponse` (no Task on 409). Do not invent `AGENT_TASK_CREATED_SPRINT_FAILED`.
- **PUBLIC_CONFIRM_BEHAVIOR_CHANGED = YES** (narrow supersession of DEC-081 + FE “disable repeated confirmation”):
  - Normal `PENDING` Confirm is still atomic claim once. `TASK_UPDATE` and arbitrary `EXECUTING` actions are **not** confirmable.
  - `EXECUTING` may be re-entered **only** for composite `TASK_CREATE` whose payload has `sprintId` **and** Backend durable Jira evidence is recovery-safe (below). Same `actionId`, same actor, same payload. Reauthorization still runs. This is not a generic duplicate Confirm.

### Product / AI proposal rules

- User omits Sprint → omit `sprintId` → Confirm create-only → Backlog.
- User names Sprint + SINGLE_MATCH → proposal payload `sprintId` (UUID) and immutable `pendingAction.summary` includes canonical **Sprint name** (not UUID-only).
- Explicit Sprint + ZERO_MATCH → no proposal (not-found/clarification). **Never** omit `sprintId` to dump Backlog.
- Explicit Sprint + MULTIPLE_MATCH → no proposal; ask the user to choose. No pick-first. No active-Sprint default.

### Confirm compose (Design A)

- `K_create` = existing `pendingAction.idempotencyKey`.
- `K_sprint` = `idempotencyKey + ":sprint"` (distinct because `uk_jira_write_operation_project_key` is `(project_id, idempotency_key)`).
- Confirm: `JiraTaskWriteService.create(K_create)` then, if `sprintId` present, `JiraTaskWriteService.sprint(K_sprint)` with existing XOR `sprintId` / `backlog`. Reuse those services; do not copy provider calls. Permission remains `requireProjectManager`.

### J1F / Agent pending / HTTP (do not lump sprint failures)

| Branch | Jira TASK_SPRINT | Agent pending | Confirm HTTP |
| --- | --- | --- | --- |
| A pre-remote failure | `FAILED` or `UNKNOWN`; no replay of move on same `K_sprint` | `FAILED` via existing `finalizeFailure` | Existing IntegrationException status/code; `ApiErrorResponse` only |
| B remote success, canonical pending | `REMOTE_SUCCEEDED`; same `K_sprint` canonical recovery only; never `FAILED`; never replay move | Stay `EXECUTING`; **do not** `finalizeFailure` | `409 JIRA_WRITE_RECOVERY_REQUIRED` or `409 JIRA_WRITE_OPERATION_IN_PROGRESS` |
| C completed | `COMPLETED` after fresh local Sprint/backlog confirm | `COMPLETED` | `200 ActionExecution` + `TaskReadResponse` |

Do not describe local Task as Backlog on branch B: Jira may already have moved.

### Durable evidence: in-flight vs recovery EXECUTING

`agent_pending_action.status=EXECUTING` **alone is not evidence.** There is no recovery flag on the pending row. Opening every `EXECUTING` action is **forbidden**.

Recovery re-entry is allowed only when **all** hold:

1. `actionType=TASK_CREATE`
2. `payload.sprintId != null`
3. pending status is already `EXECUTING` (normal claim already happened)
4. `jira_write_operation(projectId, K_create)` exists, type `TASK_CREATE`, status `COMPLETED`
5. `jira_write_operation(projectId, K_sprint)` exists, type `TASK_SPRINT`, status `REMOTE_SUCCEEDED` or `COMPLETED` (completed replay only finalizes Agent pending; it must not POST move again)

In-flight / fail-closed (no EXECUTING re-entry): missing `K_create`; `K_create` in `PENDING`/`UNKNOWN`/`REMOTE_SUCCEEDED`; missing `K_sprint`; `K_sprint` in `PENDING`/`UNKNOWN`/`FAILED`; `TASK_UPDATE`; payload without `sprintId`. Concurrent second Confirm in those states must not become a second normal claim and must not duplicate Jira create or sprint move (`JIRA_WRITE_OPERATION_IN_PROGRESS` / `PENDING_ACTION_NOT_CONFIRMABLE`).

Implementation must inspect the pending action **without** a second normal claim, then evaluate `JiraWriteOperation` **before** compose. Existing public/internal claim is PENDING-only; `require_pending_action` can already read `EXECUTING`. Do **not** add a public GET. An internal Backend→AI inspect (no status mutation) is in scope if needed. If an implementation cannot apply the predicate above, **stop** — do not fail open.

### Concurrency regression (mandatory before CURRENT_STATE)

Two concurrent `POST .../confirm` on the same initially `PENDING` action:

- exactly one normal claim
- no duplicate Jira `TASK_CREATE` POST
- no duplicate remote `TASK_SPRINT` move
- stable `K_create` / `K_sprint`
- in-flight second request fail-safe (`PENDING_ACTION_NOT_CONFIRMABLE` and/or `JIRA_WRITE_OPERATION_IN_PROGRESS`)
- branch B retry uses the same `K_sprint`
- Agent pending `COMPLETED` at most once
- no Agent/`TASK_SPRINT` `COMPLETED` unless fresh local Task sprint target matches `payload.sprintId`

### DEC-081 supersession (narrow)

DEC-081 remains: browser session+CSRF, Backend reauthorize, `JiraTaskWriteService` owns writes, no AI-direct Jira, no delete/account/role/Course mutation. **Superseded only:** (1) V1 Confirm of `TASK_CREATE` **may compose** existing `TASK_SPRINT` when `sprintId` is on the proposal; (2) repeated Confirm stays disabled except the EXECUTING recovery re-entry defined here.

## DEC-099 — Active Course is conversation-bound AI chat resource scope

- Ngày: 2026-08-16; trạng thái: **CONFIRMED_SOURCE_TEST**. Không rewrite DEC-081. Không đổi permission MEMBER/LEADER/Lecturer/Admin. Không Bearer. Không expose `/internal/**`. Không sửa Contribution. Không sửa commit-review lane.
- **Resource scope ≠ actor identity.** Browser may send additive optional `courseId` on `POST /api/v1/ai/conversations` and `POST /api/v1/ai/conversations/{id}/messages`. Browser still must not send `actorId` / `studentId` / `applicationRole` / `currentActor`. Backend validates the session actor actually has current Course access (Student TeamMember, Lecturer instructor, Admin existing Course) before binding.
- **Conversation-bound isolation.** Backend persists `ai_agent_conversation_scope` (V43). AI persists `agent_conversation.course_id` (Alembic `20260819_0007`). A conversation bound to Course A reused with Course B is `409 AI_AGENT_COURSE_SCOPE_MISMATCH` / `COURSE_SCOPE_MISMATCH`. FE must start a new conversation for Course B. History of A is never silently reused for B.
- **Discovery filter-first.** `discover_resource_context` stays NoArgs. Active Course comes from validated delegation, not from AI arguments. Backend filters Course **before** ZERO/SINGLE/MULTIPLE. Cross-Course Project/Team/Task access fail-closes even if the same user is authorized in both Courses. Admin SYSTEM capabilities stay unscoped unless a Course-scoped capability is in use.
- **Natural language + safe slot filling.** Planner remains LLM/semantic, not an exact-phrase list. `propose_task_create` may generate description from intent; self-reference `tôi/mình/tui` uses Backend `currentActor.localProfileId`; named assignee uses existing Team-scoped resolver (no pick-first). Do not invent Priority. Do not invent TaskType when ambiguous. Relative due dates are not guessed without a deterministic timezone in agent data.
- **DEC-081 unchanged.** Proposal / pendingAction only. Browser Confirm = session + CSRF → claim → Backend reauthorize → `JiraTaskWriteService`. No Jira mutation before Confirm. TOOL-role traces stay in AI audit storage and are filtered from public conversation messages.
- OpenAPI **152** (additive fields, no new operation). Flyway **V43**. AI Alembic **20260819_0007**.

## DEC-098 — Lecturer Course broadcast may include optional HTTPS actionUrl; Admin broadcast does not

- Ngày: 2026-08-16; trạng thái: **CONFIRMED_SOURCE**. Không rewrite DEC-070. Không mở Admin link. Không Bearer. Không expose `/internal/**`. Không đổi Contribution/AI migration.
- **Supersession (hẹp):** DEC-070 “request bodies never accept … external action URLs” is superseded **only** for `POST /api/v1/courses/notifications/broadcast` Lecturer Course manual broadcast. Admin `POST /api/admin/notifications/broadcast` still rejects unknown fields including `actionUrl`. Automatic Task/Sprint/integration producers remain `actionUrl=null`.
- **Lecturer contract:** existing LECTURER-only route, session + CSRF + required `Idempotency-Key`. Additive optional `actionUrl`. Absent/null/blank → Bell `actionUrl=null`. Present → absolute HTTPS only, max 500 (existing `user_notification.action_url`). Reject `http`, `javascript:`, `data:`, `file:`, malformed URI, missing host, control characters. Backend validates and persists the trimmed string; it does not fetch or follow the URL.
- **Idempotency:** normalized `actionUrl` is part of the request fingerprint when present. Same key + same courseIds/title/message + same URL replays; same key + changed URL is a different intent and conflicts. Existing no-URL fingerprints are unchanged.
- **Persistence:** reuse `user_notification.action_url`. No `notification_broadcast` column and no new Flyway version; replay uses the request + fingerprint, not a master URL column.
- **Authorization unchanged:** every Course must be active (`deletedAt` null) and assigned to the current Lecturer; recipients are distinct TeamMember Students only. Invitation is not enrollment. No school-wide Lecturer broadcast. No sender/recipient/FID/Cognito fields.
- **Admin 400:** `audience` is only `STUDENTS|LECTURERS|ALL_USERS`. UI `ALL` and extra `actionUrl`/`link` bind to `400 INVALID_REQUEST`. Missing `Idempotency-Key` is the same binding error. Do not relax Jackson fail-closed unknown properties to accept a wrong Admin payload.
- **Bell:** `GET /api/me/notifications` already returns nullable `actionUrl`. Student FE must render a safe `target="_blank" rel="noopener noreferrer"` CTA when present. FCM remains refetch-only; Firebase payload contract is unchanged.
- OpenAPI **152** (no new operation). Flyway head remains **V42**.

## DEC-097 — Extra Master: manual GitIssue↔Commit writer, review result, warning pipeline, Early Warning V2, Leader Team DOCX

- Ngày: 2026-08-16; trạng thái: **CONFIRMED_SOURCE** (tests/runtime numbers recorded after suite). Không rewrite DEC-094/095/096. Không sửa DEC-023. Không đổi Contribution formula. Không Bearer browser. Không expose `/internal/**`.
- **GIT_ISSUE_COMMIT_WRITER = MANUAL_EXPLICIT_ONLY.** GitHub snapshots/client không có explicit Commit↔Issue relation. Không parse Jira key / `#issue` / branch / title / NLP. Production writer: Project Integration Manager `POST/DELETE /api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}`, relation `MANUAL`, unique pair, idempotent link, repeated unlink 204, cross-Project `TRACEABILITY_PROJECT_MISMATCH`. Session JSESSIONID + CSRF. Không auto-traceability.
- **HISTORICAL:** bounded backlog page 20 of local `CommitData` without intent; no GitHub discovery call. LIVE HIGH drains before LOW. HISTORICAL_LIGHT only; bounded digest advisory, no per-commit storm.
- **REVIEW RESULT:** persist nested `commit-review-result-v2` on Backend (`commit_review_result`). Unknown schema/enum fail closed, no warning. FAILED/CANCELLED ≠ NEEDS_CHANGES. Replay same job/intent → one business result.
- **WARNINGS:** `COMMIT_REVIEW_NEEDS_CHANGES` only LIVE TASK_LINKED terminal NEEDS_CHANGES. `UNLINKED_COMMIT_ADVISORY` stays `reviewAdvisories[]`. Canonical `business_warning` + `NotificationService.createOnceForEvent` (Bell) + FCM + separate `warning_email_outbox`. FCM/Gmail failure không rollback warning/Bell. Invitation outbox untouched. Semantic `event_key` dedup.
- **EARLY WARNING V2** (Backend decides, AI never invents): keep TASK_DUE_* / OVERDUE_TASK. Add MEMBER/TEAM inactivity 72h from max(mapped CommitData.timestamp, Document.createdAt); `INACTIVITY_GRACE_PERIOD=TBD_PRODUCT` — membership `createdAt` must exist and be ≥72h or no warning. Sprint policy constants: start 0.40, WARNING gap 0.25, CRITICAL 0.40; STORY_POINTS if all SP present else TASK_COUNT, never mix. REPEATED_COMMIT_ISSUES window=3 threshold=2 LIVE TASK_LINKED PASS/NEEDS_CHANGES. Recipients: unique ACTIVE GitHub IdentityMap author (no pick-first) + all current `roleInTeam=LEADER`; invitation ≠ membership.
- **AI/REPORT:** `confirmedWarnings[]` / `reviewAdvisories[]` / `unsupportedSignals[]` never promoted. Student self + Leader exact Team tools. Lecturer Course team sections. Admin supported counts only. Safe language only.
- **LEADER TEAM REPORT:** hidden `POST /internal/ai/v1/agent/tools/leader-team-progress-report`. Artifact `LEADER_TEAM_PROGRESS_REPORT` + scope `TEAM`. Download reauth = current STUDENT + exact LEADER of that Team (do not invent Admin/Lecturer download). MULTIPLE_MATCH → candidates, no artifact. `REPORT_NARRATIVE_MODE=DETERMINISTIC_TEMPLATE`.
- OpenAPI **152** (POST+DELETE issue–commit). Migration **V42**. AI Alembic **20260818_0006**.

## DEC-096 — Role-aware capability matrix, report artifacts, warning-in-report, fail-closed permissions


- Ngày: 2026-08-16; trạng thái: **CONFIRMED_SOURCE_TEST**. HF/runtime/browser product smoke **TBD**. Không sửa DEC-023. Không rewrite DEC-095 (identity/tools/auto-review client giữ nguyên); DEC-096 **bổ sung** capability matrix, warning split, Lecturer/Admin report context versions, ADMIN Course access via existing analytics auth, safe AI 401/403.
- **SELF IDENTITY:** Current actor = `SagaPrincipal` + V30 delegation. Browser payload không chứa `actorId`/`applicationRole`. Tên/MSSV chỉ resolve người khác khi permission cho phép. Ambiguity = chọn Course/Team/Project, không hỏi lại identity, không pick-first.
- **SAFE DENIAL:** Public `/api/v1/ai/**` 401 → `Phiên đăng nhập đã hết hạn.` AI 403 (public + internal tool AccessDenied/IntegrationException forbidden) → `Bạn không có quyền truy cập hoặc thực hiện thao tác này.` Internal service-token 401 **không** remap thành session-expired. Lecturer Course không dạy / Leader Team không lead / Member Team khác → `ZERO_MATCH`. MEMBER Lecturer-report / non-Admin Admin-report → AccessDenied. Artifact UUID người khác reauthorize current access.
- **SRS_EFFECTIVE_AUTHORIZATION:** `requireProjectReadAccess` — ADMIN; LECTURER instructor of Course; STUDENT any `TeamMember` of owning Team. **Không** LEADER-only. Bounded `saga-srs-v1` / evidenceRefs / in-memory DOCX / download reauth `SRS_DOCX`+`PROJECT` unchanged.
- **LECTURER REPORT:** projection `saga-lecturer-course-report-context-v1`, artifact `LECTURER_PROGRESS_REPORT`. LECTURER = instructed Course only. ADMIN = same underlying `LecturerAnalyticsAuthorizationService.requireCourseAccess` **và** bắt buộc `courseId` (không pick-first all courses). Per-Team sections; source-backed dashboard/contribution/velocity/14-day overview/burndown only if exactly one active sprint. Download reauth uses `requireCourseAccess` (ADMIN allowed when domain auth allows).
- **ADMIN REPORT:** projection `saga-admin-system-report-context-v1`, artifact `ADMIN_SYSTEM_REPORT`. ADMIN-only. MSR/DEADLINE_PROCESS/SNA_ISOLATION remain `TBD`/`count=null`. Graph history unsupported. Course XLSX export unchanged; DOCX ≠ XLSX replacement.
- **WARNINGS:** `confirmedWarnings` only from `TASK_DUE_*` date-only classifier (Jira zone, skip DONE/CANCELLED) and `OVERDUE_TASK` early-warning/anomaly. `unsupportedSignals` lists inactivity 3D, sprint-behind, repeated commit issues, auto-review result warning (and Admin TBD anomalies). `reviewAdvisories=[]`. Auto-review operational counts from `commit_review_intent.intentStatus`; `resultWarningIntegrationConfirmed=false`. Unlinked commit advisory ≠ Task failure.
- **CAPABILITY MATRIX** (authorization source, not product assumption):
  - MEMBER: self progress/own tasks/self contribution/deadline-on-own-tasks/ACTIVE commit discovery/SRS-if-team-member = CONFIRMED. Team progress PARTIAL (project/task read if member; leader-team-context + team contribution evaluate FORBIDDEN). Teammate progress/early warning/course report/admin report/task proposals = FORBIDDEN. Commit review PARTIAL (no result warning). Team DOCX artifact type = FORBIDDEN.
  - LEADER: self + exact led Team progress/teammate progress/team contribution/deadline-on-led-team/SRS/task proposals = CONFIRMED. Early warning FORBIDDEN. Course/admin report FORBIDDEN. Team report PARTIAL (context, no dedicated DOCX type). Commit review PARTIAL.
  - LECTURER: instructed Course progress/contribution/deadline/OVERDUE_TASK/SRS/course report/task proposals = CONFIRMED. Self-progress/own-tasks/my-commits FORBIDDEN. Admin report FORBIDDEN. Commit review PARTIAL (operational aggregate).
  - ADMIN: system report CONFIRMED. Course report CONFIRMED iff `courseId` + requireCourseAccess. Same analytics reads as Lecturer when that auth allows. Self-progress/my-commits FORBIDDEN. XLSX course export unchanged.
- OpenAPI **149**. Migration head **V41**.

## DEC-095 — Role-aware AI chat identity, report projections, artifact reauth, typed auto-review client

- Ngày: 2026-08-16; trạng thái: **CONFIRMED_SOURCE_TEST**. HF/runtime/browser product smoke **TBD**. Không sửa DEC-023. Không rewrite DEC-094 (intent + cutover foundation giữ nguyên); DEC-095 **bổ sung** typed AI start/poll/result mà DEC-094 để TBD.
- **CURRENT_ACTOR:** Browser `/api/v1/ai/**` không nhận `actorId` / `studentId` / `lecturerId` / `applicationRole`. Current actor = `SagaPrincipal` session. Conversation bind opaque V30 delegation (`conversationId` + actor profile + role). Self-reference (“tôi”, “nhóm tôi”, “lớp tôi”) resolve từ Backend identity, không từ tên/MSSV user nhập. Ambiguity chỉ được hỏi **chọn resource** (Course/Team/Project/commit), không hỏi lại identity.
- **ROLE-AWARE INTERNAL TOOLS** (`/internal/ai/v1/agent/tools/*`, `@Hidden`, không OpenAPI/FE): `self-progress`, `self-recent-commits`, `leader-team-context`, `lecturer-course-context`, `lecturer-progress-report`, `admin-system-report`. Không nhận `studentId`/`lecturerId` để bypass actor. MEMBER = self only. LEADER = exact `TeamMember.roleInTeam=LEADER`; nhiều Team → `MULTIPLE_MATCH`, không pick-first. Lecturer = exact instructed Course. Admin report = ADMIN only.
- **RECENT COMMITS:** canonical `CommitData` + **ACTIVE** GitHub `IdentityMap` only; bounded 10; AI không gọi GitHub. 0 → empty; 1 → resolve; >1 → `MULTIPLE_MATCH`. Mapping missing/DISCONNECTED/ambiguous → không attribution ngẫu nhiên.
- **REPORT PROJECTIONS:** `saga-lecturer-progress-report-v1` / `saga-admin-system-report-v1`. Reuse dashboard/analytics/admin report services. Không final grade, không AI risk score, không fabricate trend/graph history. Early Warning authority vẫn **OVERDUE_TASK** only. Admin anomalies MSR/DEADLINE_PROCESS/SNA_ISOLATION giữ `TBD`/`count=null`. Graph `historySupported=false`, `points=[]`.
- **ARTIFACTS:** SRS DOCX pipeline không đổi (`SRS_DOCX` + `PROJECT` → `projects.get`). Download reauth thêm `LECTURER_PROGRESS_REPORT` + `COURSE` (LECTURER instructor exact Course) và `ADMIN_SYSTEM_REPORT` + `SYSTEM` (ADMIN). Browser chỉ `GET /api/v1/ai/artifacts/{id}/download`. Không expose AI filesystem path. Bytes không phải durable truth (regenerate in-memory như SRS).
- **AUTO REVIEW TYPED CLIENT:** Backend→AI `X-SAGA-Backend-Service-Token`. Exact: `POST /internal/backend/v1/commit-reviews`, `GET .../{jobId}`, `GET .../schemas/final-result-v2`, `POST .../execution/run-bounded`. Policies exact `commit-review-historical-v1`+LOW, `commit-review-live-task-aware-v1`+HIGH. Unknown enum fail closed. `FAILED`/`CANCELLED` ≠ `NEEDS_CHANGES`.
- **ORCHESTRATION:** persist intent (DEC-094) → `CommitReviewIntentQueued` AFTER_COMMIT → claim PENDING→STARTING → HTTP **ngoài** DB transaction (`Propagation.NOT_SUPPORTED`) → persist job id (`REQUIRES_NEW`). Scheduler bounded drain/poll; tests tắt `app.agent-ai.commit-review.execution-enabled=false`. Unconfigured AI (`SAGA_AI_AGENT_BASE_URL` / token trống) không claim. V41 job tracking columns. Không scan historical backlog vô hạn.
- OpenAPI **149** (không public route mới). Migration head **V40 → V41**.

## DEC-094 — Auto commit-review intent after canonical persist; Early Warning V2 gated

- Ngày: 2026-08-16; trạng thái: **PARTIAL / CONFIRMED_SOURCE_TEST** cho orchestration; warning fan-out / Early Warning V2 **không ship** vì blocker.
- **CONFIRMED persist hook:** mọi GitHub webhook / reconciliation / manual sync hội tụ `GitHubDataUpsertService.upsertCommit` sau `CommitData.saveAndFlush`. Commit mới `(repo_id, sha_hash)` enqueue đúng một `commit_review_intent`. Replay không tạo intent thứ hai.
- **SAFE_REVIEW_CUTOVER_FIELD:** không reuse `createdAt`/`lastSyncedAt` làm live field. Cột mới `git_repo.review_cutover_at` = thời điểm SAGA bắt đầu authoritative integration repo; set once lúc link; backfill V40 từ `git_repo.created_at` (link-time). Commit `timestamp < cutover` → HISTORICAL_LIGHT LOW; `>= cutover` → LIVE_TASK_AWARE HIGH. Scheduler drain HIGH trước, historical bounded 5/tick.
- **Không gọi AI job start:** Backend không có endpoint start `COMMIT_REVIEW` (chỉ chat proxy). Không invent path. Intent ở `PENDING` cho tới khi AI publish start/result contract.
- **Traceability:** vẫn `GitIssueCommitLink` → `GitIssue` → `TaskGitIssueLink` → `Task`. `GitIssueCommitLink` **không có production writer**. Jira key/message không phải Task authority.
- **V2 parser:** reject unknown enum; `FAILED` / `LIVE_UNLINKED_ADVISORY` / `HISTORICAL` ≠ `NEEDS_CHANGES`.
- **Không ship:** warning Bell/FCM/Email, warning-email outbox, historical digest, MEMBER/TEAM inactivity (grace TBD_PRODUCT), SPRINT_PROGRESS_BEHIND thresholds (RECOMMENDED_PENDING_CONFIRMATION), REPEATED_COMMIT_ISSUES 2/3 (TBD). `OVERDUE_TASK` không đổi.
- OpenAPI **149** (không public route mới). Migration **V39 → V40**.
## DEC-096 — Contribution flowchart graph is a separate GET; SAGA mixer unchanged

- Ngày: 2026-08-16; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- Context: mockup flowchart (tiêu chí → SP → student → peer → %) cần API node/edge. Không copy hệ số mockup (CODE×2.0 / DESIGN / DOCS). Công thức giữ DEC-092.
- Quyết định: `GET /api/v1/teams/{teamId}/contribution-graph`. Cùng auth DEC-095 (LECTURER exact Course instructor, STUDENT exact Team LEADER). ADMIN/MEMBER/MENTOR 403. Node `CRITERION` (CODE/TEST/DOCUMENT/RESEARCH) + `STUDENT`; cạnh tiêu chí→sinh viên kèm `storyPoints`, `weightedSlice = SP × weightRatio`, `tasks[]` (title/externalKey/sprint) để drill-down. `P` = `stars_i / teamStars`. Không GHOSTING, không publish/snapshot, không `/api/analytics/*`.
- Query `sprintId` tùy chọn (additive, không thêm route): bỏ trống = cả Project; có = flowchart đúng Sprint thuộc Project của Team. Node STUDENT dùng slice / `P_s` / `%` của Sprint đó, không dùng override giảng viên cả dự án. Sprint không thuộc Project → 404 `Sprint not found`.
- Radar/bar/line vẫn đọc evaluation (DEC-094). Mixer, Peer Review, override, activity graphs không đổi.
- OpenAPI **149 → 150**; query `sprintId` giữ OpenAPI **152**. Không migration.

## DEC-095 — Contribution evaluation / graph is Lecturer and Student Leader only; ADMIN cannot read

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- **Supersede DEC-074 (read access only):** `GET /api/v1/teams/{teamId}/contribution-evaluation` không còn cho ADMIN. LECTURER exact Course instructor và STUDENT exact Team LEADER giữ nguyên. MEMBER/MENTOR/cross-Team vẫn 403. Anonymous 401. Team thiếu: LECTURER/STUDENT 404.
- `POST .../contribution-override` không đổi (ADMIN/LECTURER). Activity graphs (heatmap/overview/interactions/burndown) không đổi.
- OpenAPI **149**. Không migration.

## DEC-094 — Contribution evaluation exposes per-sprint recognized story points for graph charts

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- Context: scoring (DEC-092) đã chốt. Graph đóng góp (radar / stacked bar / line % sprint) không cần route `/api/analytics/*` mới. Heatmap, overview, interactions, burndown đã có dưới `/api/v1/courses/{courseId}/teams/{teamId}/...` và là **activity graphs**, không đổi công thức Contribution.
- Quyết định: reuse `GET /api/v1/teams/{teamId}/contribution-evaluation`. Read access: LECTURER exact Course instructor và STUDENT exact Team LEADER (**ADMIN không đọc** — DEC-095). Additive fields trên `sprintBreakdowns[]`: `codeStoryPoints`, `testStoryPoints`, `documentStoryPoints`, `researchStoryPoints` = Σ SP được công nhận trong sprint đó (sau label + DOCUMENT/RESEARCH file/link gate). Không phải %. Không nhân trọng số — `sliceScore` vẫn là Σ SP × weight. FE không nhân peer lần hai.
- Mapping FE: radar = `code/test/document/researchContributionPercentage` (project-level share); so sánh thành viên = `finalContributionPercentage`; line theo sprint = `sprintBreakdowns[].contributionPercentage`; stacked bar tiêu chí = bốn `*StoryPoints`.
- Không mở MEMBER đọc Contribution. Không đổi mixer arithmetic, Peer Review, override, heatmap scoring. OpenAPI **149** (không thêm operation). Không migration.

## DEC-093 — Students attach files/images/links to a Jira Task through SAGA

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- `POST /api/v1/projects/{projectId}/tasks/{taskId}/attachments` uploads files to Jira `POST /rest/api/3/issue/{id}/attachments` (`X-Atlassian-Token: no-check`, multipart `file`) and/or submits an `http`/`https` `link` via Jira `POST /rest/api/3/issue/{id}/remotelink`. Then canonical `getIssue` + upsert persists `task_attachment` metadata (no bytes, no content URL). Links persist in `task_web_link` (URL + optional Jira remote-link id). Do **not** store links in `task_attachment` — issue upsert replace-all would delete them.
- Authorization: **STUDENT team member only** — not Lecturer, not Admin, not Leader-only. Scope `write:jira-work`. Idempotency-Key like other Jira writes. Max 5 files / 10MB; images + common documents. `link` max 2048 chars. At least one file or a link required (`JIRA_EVIDENCE_REQUIRED`).
- DOCUMENT/RESEARCH story points count when the Task has ≥1 Jira file **or** ≥1 submitted web link.
- OpenAPI **148 → 149**. Migration **V38** (`task_attachment`) + **V39** (`task_web_link`). `TASK_ATTACHMENT` is STRING enum on `jira_write_operation`.

## DEC-092 — Contribution % is absolute weighted slice × peer; project final sums slices then applies project P

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- **Supersede DEC-091:** không còn `(điểm sv / tổng team)×weight` trong từng tiêu chí, không `normalizeForActiveSlices`, không trung bình đều % sprint.
- `slice = (Σ SP_code)×Wc + (Σ SP_test)×Wt + (Σ SP_doc)×Wd + (Σ SP_research)×Wr` với trọng số dạng 0.40. `% sprint = (slice × P_s) / Σ adjust`. `% cuối = (Σ_s slice × P) / Σ adjust`, `P` = sao cá nhân / sao team cả dự án. Sprint chưa peer: `P_s = 1`. Cả dự án chưa peer: `P = 1`.
- Spec + ví dụ từng task: `docs/CONTRIBUTION_CALCULATION_SPEC.md`. Mixer: `SprintFirstContributionMixer`. Evaluation API returns pre-peer `sliceScore` / `sliceContributionPercentage` plus after-peer `finalContributionPercentage`. OpenAPI **149**. Không migration.

## DEC-091 — Contribution % is sprint-first: mix + peer per sprint, then average to the project final

- Ngày: 2026-08-15; trạng thái: SUPERSEDED by DEC-092.
- **Supersede the project-pool mix in DEC-090's evaluation path:** `finalContributionPercentage` is no longer `(project C/T/D/R mix) × project peer`. For each sprint with recognized criteria, compute slice shares from **that sprint's** recognized story points, `normalizeForActiveSlices` **inside that sprint**, mix with Course/Team weights, multiply by **that sprint's** peer coefficient `P_s`, then normalize the sprint to 100%. Project final = **equal average** of those sprint percentages (then existing override normalize to 100%). Project peer is **not** multiplied again.
- Unscheduled (null-sprint) tasks **do not score**. Each sprint mixes criteria then multiplies by `P_s` (`P_s = 1.0` while that sprint has no peer yet — typical for an in-progress sprint). The last sprint is included in the same way, using its peer when present. Project final = equal average of those sprint percentages.
- `sprintBreakdowns[].contributionPercentage` is the per-sprint result. Radar `code/test/document/researchContributionPercentage` stays project-level slice share. `peerReviewScore` stays project-level `stars_i / teamStars` (display only).
- `SprintFirstContributionMixer` is shared by `TeamContributionService` and `ContributionCalculationService`. OpenAPI **148** (one new field on an existing response record, no new route). No migration.

## DEC-090 — Labels are the only Task→criterion authority; DOCUMENT/RESEARCH story points require a Jira attachment

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime migration **NOT_YET_UNTIL_DEPLOY**.
- **Supersede DEC-089's keyword fallback:** `TaskContributionClassifier` routes a DONE Task into CODE/TEST/DOCUMENT/RESEARCH **only** via exact reserved labels `saga:code`/`saga:test`/`saga:document`/`saga:research` (`ReservedContributionMarkerClassifier`, trim + case-sensitive exact match). No `classifyTaskSlice` keyword/title/`TaskType` fallback. Unlabeled or `AMBIGUOUS` Tasks enter no criterion. `adjustedSprintScore` still counts those story points.
- **DOCUMENT/RESEARCH attachment gate:** story points (`storyPoint ?? 1.0`) count toward the criterion **only if** the Task has at least one `TaskAttachment`. Extra files do not add points. Without an attachment, that Task's story points are not recognized for DOCUMENT/RESEARCH (`adjustedSprintScore` still counts them). CODE/TEST ignore attachments. SAGA `Document` rows are **not** a Contribution evidence source.
- **Jira attachment metadata ingestion:** `JiraProviderClientImpl` requests field `attachment`. Missing/null/empty = empty list. `JiraIssueUpsertService` replace-all syncs `task_attachment` after `saveAndFlush` (skip if Task id is still null). Persist id/filename/mimeType/sizeBytes/authorExternalId only — no file download, no content URL. `V38__add_task_attachment.sql` (`uk_task_attachment_external`, `ON DELETE CASCADE`). GitHub attachments remain **not implemented**.
- **Contracts:** OpenAPI **148** (unchanged). Migration head **V37 → V38**. Peer Review / Rubric / individual override / Course-Team weights / Jira-GitHub sync routes **unchanged**.

## DEC-089 — Task is the sole numeric Contribution authority; explicit reserved markers (saga:code/test/document/research) classify Task criterion; legacy DESIGN weight folded into DOCUMENT

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST (foundation only — provider ingestion for external evidence remains gated, see below); runtime migration **NOT_YET_UNTIL_DEPLOY**.
- **Foundation scope, not the full milestone:** the originally-requested milestone also asked for Jira attachment ingestion and GitHub Issue/comment attachment extraction as new evidence sources. Those remain **not implemented** — blocked on unresolved provider-runtime TBDs (Jira attachment content endpoint not smoke-tested; actual GitHub App permission grant unconfirmed at runtime; private-repo attachment CDN auth path unproven). This decision covers only the provider-independent foundation: a typed `ContributionCriterion` model, exact-match reserved-marker classification, marker precedence over the legacy keyword classifier, and the Task-is-sole-numeric-authority double-count rule — all buildable and testable without any new provider call.
- **`ContributionCriterion` enum** (`src/main/java/com/saga/be/service/contribution/ContributionCriterion.java`) = `CODE/TEST/DOCUMENT/RESEARCH`, deliberately separate from `TaskType` (a Jira business/issue-type enum persisted as a native MySQL `ENUM` column — extending it, as `V29` had to for `REQUEST`, requires a physical column migration) and from `DocumentType`. Contribution classification is its own concern, never overloads either.
- **Exact reserved markers** (`ReservedContributionMarkerClassifier`): `saga:code`, `saga:test`, `saga:document`, `saga:research` — case-sensitive exact string match only (no substring, no fuzzy, no AI inference; `saga:test-extra` and `SAGA:TEST` both fail to match). More than one conflicting reserved marker on the same Task's labels is `AMBIGUOUS` (`ContributionMarkerClassification.conflicting()`) — the Task is excluded from all four criteria until the conflict is fixed; it is never pick-first-resolved. Unrelated ordinary Jira labels never trigger a conflict.
- **Precedence, layered additively in front of the existing (untouched) legacy classifier:** a new `classifyTaskContribution(Task)` wrapper in both `TeamContributionService` and `ContributionCalculationService` checks the reserved marker first; if none is found, it falls through unchanged to that service's own existing `classifyTaskSlice` (title/description/labels/components keyword matching, unchanged CODE/DOCUMENT-only outcome, unchanged pick-first behavior on keyword conflicts, unchanged CODE default for unlabeled ordinary Tasks). The two services' legacy classifiers were already structurally slightly different before this change (different text-combination order, different short-circuit on `TaskType`) — this is pre-existing technical debt, reported but **not unified**, since neither test nor source proves unifying them is safe.
- **`TASK_WEIGHT_CONFIG_SCORING_AUTHORITY = NO`, confirmed by audit:** `TaskWeightConfig` (`task_weight_config` table, keyed by `taskType`) was investigated as a candidate "task scored by labels" system per a teammate's description, but its only consumer is `CourseService.softDeleteCourse`'s delete-dependency guard — it is never read by either Contribution scoring service and was left untouched.
- **Task is the sole numeric Contribution authority when evidence is Task-linked (double-count prevention) — a real, intentional semantic change to how Contribution is sourced, not a no-op:**
  `NUMERIC_TASK_FORMULA_CHANGED = NO` — the per-Task formula itself is unchanged: `taskScore = storyPoint ?? 1.0`, gated on `status == DONE` and correct assignee, byte-identical to before.
  `COMMIT_NUMERIC_CONTRIBUTION_CHANGED = YES` — the per-commit scoring loop that previously ALSO added `commit.getTask()`'s storyPoint into the CODE/DOCUMENT bucket (via the now-recognized-as-dead `commit.task` FK — confirmed by audit that no production upsert path ever writes it) has been **removed entirely** from both services. A commit linked to a Task now contributes exactly zero additional score, where it previously contributed its full weight; it is supporting/provenance evidence only from this decision forward.
  `OVERALL_CONTRIBUTION_SOURCE_SEMANTIC_CHANGED = YES` — this is a deliberate product decision to close a real latent double-count path in the pre-existing formula (a Task that was both DONE-and-assigned AND had a `commit.task`-linked commit would previously have had its storyPoint counted twice), and to pre-empt the identical risk once the (not-yet-built) `GitIssueCommitLink → TaskGitIssueLink` traceability path is later wired into scoring. It must not be described as "the formula is unchanged" in any summary — only the Task-level formula is unchanged; the overall set of what counts as scoring evidence has changed.
- **Standalone evidence with no Task relation is completely unaffected:** `Document` scoring (`documentRepository.countByProjectIdAndAuthorId` / `documents.stream().filter(author)`) has no Task dependency at all and was not touched. `commitDataRepository.findByAuthorIdAndProjectIdAndTaskIsNotNull` already structurally excludes commits with no Task link — standalone commits were never counted before and are not counted now.
- **`TEST`/`RESEARCH` now have a real evidence source for the first time:** a DONE Task carrying `saga:test` or `saga:research` routes its full (unchanged) `taskWeight` into `testScoreByStudent`/`researchScoreByStudent`, which now feed `normalizeForActiveSlices` and the response's `testContributionScore/Percentage` / `researchContributionScore/Percentage` fields with real, non-zero values when such a Task exists — superseding DEC-087/DEC-088's blanket `TEST_SLICE_CLASSIFICATION = TBD_PRODUCT_RULE` / `RESEARCH_SLICE_CLASSIFICATION = TBD_PRODUCT_RULE` **only for the Task-marker evidence path**; provider-sourced (attachment/commit-via-traceability) TEST/RESEARCH evidence remains TBD pending the provider-ingestion phase.
  `TASK_MARKER_4_CRITERIA_STATUS = IMPLEMENTED` (DONE Task + exact `saga:*` marker is the only evidence source live today). `EXTERNAL_EVIDENCE_PIPELINE_STATUS = PARTIAL` — `JIRA_ATTACHMENT_INGESTION = NOT_IMPLEMENTED`, `GITHUB_ATTACHMENT_INGESTION = NOT_IMPLEMENTED`. Do not describe the "external evidence + 4-criteria scoring" milestone as complete; only the Task-marker slice of it is real. No Test Case attachment, Research report attachment, or GitHub Issue/comment evidence has been ingested — there is no code path that could ingest one yet.
- **`V37__fold_legacy_design_weight_into_document.sql` (new, does not rewrite V34/V35/V36):** `V34/V35/V36`'s applied status could not be confirmed with runtime/Flyway-history evidence, so per the safe default under that uncertainty they were left untouched and this is a new migration after the current head. Exact product-given formula (not an invented redistribution): `document = document + design; design = 0`, for both `course` and `project_group_weight_config`. `code`/`test`/`research` columns are **never** written by this migration.
  **Corrected safety guard (audited before finalizing, caught before any deploy):** the first draft folded every row with non-zero `design`, which is unsafe — audit found that neither `CourseContributionWeightService#updateCurrentWeights` nor a `ProjectGroupWeightConfigService#update` call against an *existing* row ever zeroes the legacy design column (only a brand-new `ProjectGroupWeightConfig` row gets it zeroed at creation). A row can therefore already have a fully, validly configured active 4-field sum (100 for Course, 1.0 for Team) sitting next to a stale non-zero legacy `design` value — naively folding would push that row's active sum above 100/1.0. The fix: only fold a row where `code+test+document+research+design` still equals exactly 100/1.0 — i.e. where `design` is genuinely the missing piece of an otherwise-untouched legacy total (this re-tests the exact invariant that was the only validated sum rule before DEC-088, not an invented condition). If the four active fields already sum to 100/1.0 on their own, the row is left untouched — `design` stays inactive exactly as `ContributionSliceWeightResolver` already treats it. Proven by an executed test (`LegacyDesignWeightFoldMigrationContractTest`, runs the real V37 SQL against an isolated in-memory H2 database — a string-content assertion cannot prove this conditional fold/no-fold behavior) covering: untouched-legacy row folds correctly, already-4-slice-configured row is left alone, `design = 0` is a no-op, and the Team/Project 0..1 scale equivalents of the first two cases.
- **Contracts:** OpenAPI operation count **unchanged (151)** — no controller/route was added or removed this turn. Migration head **V36 → V37**. Peer Review / Rubric / individual override / Student progress authorization / AI Agent / Jira-GitHub sync / ProjectType / Course-Team contribution mode (COURSE/TEAM, DEC-088) **all unchanged**.

## DEC-088 — Contribution weight authority is Course-default with optional exclusive Team override (COURSE/TEAM mode); DESIGN retired as a Contribution criterion

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime migration reset **NOT_YET_UNTIL_DEPLOY**.
- **Supersede DEC-087 hoàn toàn (không sửa DEC-087, chỉ ghi đè bằng quyết định mới hơn):** DEC-087 mô tả model "Course-only, mọi Team luôn dùng Course weight, không có override nào khác". Product owner đã làm rõ đây **không phải** model cuối — model cuối là mỗi Course có đúng một **`ContributionConfigMode`** đang active: `COURSE` (mọi Team dùng chung Course weight) hoặc `TEAM` (**mọi** Team hiện tại của Course bắt buộc phải có `ProjectGroupWeightConfig` riêng hợp lệ; không Team nào được thiếu). **Không có mode thứ ba** kiểu "Team override nếu có, không thì fallback Course" — đó là hành vi bị cấm tường minh; không bao giờ có Course trộn lẫn một phần Team dùng Course-weight, một phần dùng Team-weight.
- **Criteria universe đổi từ CODE/TEST/DOCUMENT/DESIGN (DEC-087) sang CODE/TEST/DOCUMENT/RESEARCH.** DESIGN không còn là Contribution criterion active — nó vẫn tồn tại như một ProjectType catalog value (`DESIGN_ARCHITECTURE`, xem DEC-086) nhưng hai khái niệm hoàn toàn độc lập: ProjectType không quyết định hay khoá Contribution weight. Evidence trước đây gán nhãn DESIGN (cả `DocumentType.DESIGN` và design-keyword task classification có sẵn) nay gộp thẳng vào bucket **DOCUMENT** — deterministic remap của dữ liệu đã có sẵn, không phải taxonomy mới. Không có mapping DESIGN → RESEARCH nào được invent.
- **`ContributionSliceWeightResolver` mode-aware:** `mode = COURSE` → luôn trả `ContributionSliceWeights.fromCourse(team.getCourse())`, mọi Team trong cùng Course resolve giống hệt nhau. `mode = TEAM` → tra `ProjectGroupWeightConfig` theo đúng `projectId` **và** xác nhận `team` sở hữu row đó; thiếu override hoặc override thuộc Team khác → **fail-closed**, ném `IntegrationException` (`TEAM_WEIGHT_CONFIG_INCOMPLETE`), **không bao giờ** âm thầm fallback về Course. Điều này áp dụng cả cho Team tạo mới sau khi TEAM mode đã active — Team mới không tự động thừa hưởng Course weight, phải được cấu hình override trước khi Contribution có thể tính cho Team đó.
- **Mode switch fail-safe, atomic, không xoá cấu hình cũ:** `PUT /api/v1/courses/{courseId}/contribution-config-mode` (LECTURER instructor đúng Course, không mở rộng ADMIN/Student) chuyển sang `TEAM` chỉ khi **toàn bộ** Team hiện tại (chưa xoá) của Course đã có `ProjectGroupWeightConfig` hợp lệ — audit trước, activate atomic sau (không có trạng thái partial). Thiếu dù chỉ một Team → 409 `TEAM_MODE_CONFIGURATION_INCOMPLETE`, mode giữ nguyên `COURSE`. Chuyển ngược về `COURSE` chỉ cần Course weight hợp lệ (luôn đúng vì `@PrePersist` default); `ProjectGroupWeightConfig` của các Team **không bị xoá**, chỉ trở thành historical/inactive — có thể tái sử dụng nếu Course quay lại TEAM mode sau này.
- **`PUT /api/projects/{projectId}/group-weights` được hồi sinh** (đã bị DEC-087 xoá) — `ProjectGroupWeightConfigController`/`Service`/request-response DTO quay lại, giờ có 4 field `{codeWeight, testWeight, documentWeight, researchWeight}` (0..1 scale, tổng đúng 1.0, không đổi unit so với Course API 0..100 — normalize ở tầng service). Authorization giữ nguyên hẹp như thiết kế gốc: ADMIN hoặc đúng LECTURER phụ trách Course sở hữu Team — **không** mở rộng cho Student/Leader dù `ProjectIntegrationAuthorizationService.requireTeamManager` cho phép Leader (route dùng authorization riêng, hẹp hơn, cố ý). Ghi override khi Course đang ở `COURSE` mode vẫn được chấp nhận như "draft" — resolver chỉ đọc nó sau khi mode chuyển sang `TEAM`.
- **Course API bốn field mới:** `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` đổi `designWeight` → `researchWeight` (0..100 scale, tổng 100). Legacy `PolicyOverrideRequest` approve flow: áp code/document trực tiếp vào cột active, ghi giá trị `design` cũ (nếu có) verbatim vào cột `design_contribution_weight` đã inactive (giữ lại, không discard, không suy diễn), ép `testContributionWeight = 0` — an toàn vì `ContributionSliceWeights.fromCourse` luôn renormalize theo tổng active slice, không phụ thuộc tổng raw đúng 100.
- **Team-menu read mới:** `GET /api/v1/courses/{courseId}/contribution-team-weights` (ADMIN/LECTURER) trả về mode hiện tại của Course cùng danh sách mọi Team với effective weight + `source` (`COURSE` hoặc override — chưa có nhãn `INCOMPLETE` tường minh trong response hiện tại, xem "Vấn đề còn mở"). Route này hoàn toàn mới, trước đây không tồn tại dưới bất kỳ tên nào.
- **Migration:** `V35__add_course_contribution_config_mode_and_weights.sql` (đổi tên từ `V35__add_course_test_contribution_weight.sql` của DEC-087, viết lại nội dung) chỉ `ADD COLUMN` — `test_contribution_weight`, `research_contribution_weight`, `contribution_config_mode` (default `'COURSE'`) — **không UPDATE** bất kỳ Course row nào đã tồn tại, giữ nguyên tuyệt đối giá trị Lecturer đã cấu hình trước đây (kế thừa nguyên tắc "no data-resetting migration" đã học từ correction trước). `V36__add_test_research_weight_to_project_group_weight_config.sql` (mới) thêm `test_weight`/`research_weight` vào `project_group_weight_config`, cũng chỉ `ADD COLUMN DEFAULT 0`, không rewrite historical rows, `design_weight` giữ lại nguyên vẹn làm historical/inactive.
- **`LEGACY_DESIGN_WEIGHT_MIGRATION = TBD`** — không có safe formula để tự động chuyển giá trị `design_contribution_weight`/`design_weight` cũ (Course lẫn ProjectGroupWeightConfig) sang `research`; giá trị đó bị bỏ qua khi tính Contribution (đã được resolver mới loại trừ), chỉ còn ý nghĩa lịch sử. Không nghĩ ra công thức redistribute nếu chưa có product decision.
- **Contracts:** OpenAPI operation count **148 (DEC-087) → 151** (hồi sinh 1 PUT `group-weights`, thêm mới 1 PUT `contribution-config-mode` + 1 GET `contribution-team-weights`). Migration head **V35 → V36**. Peer Review, Rubric, individual contribution override, Student progress authorization, AI Agent, Jira/GitHub **không đổi**. `TEST_SLICE_CLASSIFICATION`/`RESEARCH_SLICE_CLASSIFICATION` vẫn `= TBD_PRODUCT_RULE` như DEC-087 đã xác lập cho TEST — chưa có nguồn dữ liệu deterministic nào cho RESEARCH evidence ngoài phần đã remap sang DOCUMENT; `testContributionScore`/`researchContributionScore` trong response Team Contribution luôn `0.0`, **không tuyên bố 4-tiêu-chí scoring là COMPLETE**.

## DEC-087 — Contribution weight authority becomes Course-wide 4-slice (Code/Test/Document/Design) — SUPERSEDED BY DEC-088, kept verbatim for history

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime migration reset **NOT_YET_UNTIL_DEPLOY**.
- Context: product quyết định Lecturer không còn chỉnh trọng số Contribution riêng từng Project/Team. Lecturer chỉnh một bộ tiêu chí `CODE/TEST/DOCUMENT/DESIGN` (tổng 100%) cho **Course** mình phụ trách; bộ trọng số này áp dụng cho **mọi Team** thuộc Course đó. `Class` không phải authority — scope vẫn là exact Course từ URL.
- **Supersede DEC-083 (chỉ phần Course weight — 3 slice → 4 slice):** `PUT /api/v1/courses/{courseId}/contribution-slice-weights` route giữ nguyên nhưng body/response nay có bốn field `{codeWeight, testWeight, documentWeight, designWeight}` thay vì ba; validation vẫn mỗi field `>= 0` và tổng 100 ± 0.01. Legacy request/decision flow (`POST .../contribution-slice-weight-requests`, `PUT .../decision`) **không mở rộng** với `testWeight` (deprecated, không còn consumer FE mới) — khi Admin duyệt, backend đặt `testWeight = 0` để giữ đúng tổng bốn cột Course = 100.
- **Supersede Project V1 / DEC-082 (phần `ProjectGroupWeightConfig` precedence):** `ContributionSliceWeightResolver` không còn đọc `ProjectGroupWeightConfig` — resolver luôn trả về trọng số của `Team.course`. `PUT /api/projects/{projectId}/group-weights` (và `ProjectGroupWeightConfigController`/`Service`/request-response DTO) **bị xóa**; không endpoint thay thế theo Team. Entity `ProjectGroupWeightConfig` và repository của nó **được giữ lại** cho historical rows — không drop table, không hard-delete data. Regression test xác nhận: historical row tồn tại trong DB vẫn bị resolver bỏ qua hoàn toàn.
- **TEST slice classification = TBD_PRODUCT_RULE (audited, revised):** không có `TaskType.TEST`, `DocumentType.TEST`, hay bất kỳ authoritative source/product rule nào định nghĩa testing/QA marker trước milestone này. Một danh sách keyword TEST ban đầu được thêm vào `classifyTaskSlice` nhưng bị xác định là **tự invent** (không có nguồn domain/repository chứng minh) nên đã bị **gỡ bỏ** khỏi cả `TeamContributionService` và `ContributionCalculationService`. `ContributionSlice` (internal enum) quay lại đúng ba giá trị `CODE/DOCUMENT/DESIGN` như trước milestone — không có cơ chế phân loại Task/commit nào gán TEST. Course-level `testWeight` vẫn được chấp nhận, lưu và đọc lại (schema backward-safe) nhưng **luôn được coi là inactive** khi tính Contribution (`normalizeForActiveSlices` nhận `testActive = false` cố định) nên ngân sách của nó được phân bổ lại cho Code/Document/Design đúng theo cơ chế "slice không có evidence" đã có sẵn. `testContributionScore`/`testContributionPercentage` trong response luôn là `0.0`. **Không tuyên bố TEST evidence scoring là COMPLETE** — cần product rule cụ thể (field/taxonomy nào là authority) trước khi implement classification thật.
- **Migration `V35__add_course_test_contribution_weight.sql` (audited, revised):** chỉ `ALTER TABLE course ADD COLUMN test_contribution_weight DOUBLE NOT NULL DEFAULT 0`. **Không UPDATE bất kỳ Course row nào đã tồn tại** — `code_contribution_weight`/`document_contribution_weight`/`design_contribution_weight` của Course cũ giữ nguyên y hệt giá trị Lecturer đã cấu hình (hoặc default cũ 33.33 mỗi phần); cột mới backfill `0` cho các row đó nên tổng bốn cột vẫn đúng 100 (`CODE + DOCUMENT + DESIGN + TEST(0) = 100`) mà không cần suy diễn công thức redistribute. Course **mới tạo sau V35** nhận default `25/25/25/25` từ tầng application (`Course#applyDefaultContributionWeights`, `@PrePersist`), không phải do migration ghi đè — migration chỉ thêm cột an toàn cho dữ liệu cũ.
- **Contracts:** OpenAPI operation count **149 → 148** (xóa đúng 1 public PUT, không thêm route mới). Migration head **V34 → V35**. Peer Review, individual contribution override, Rubric, Student progress authorization, AI Agent, Jira/GitHub **không đổi**.

## DEC-086 — ProjectType becomes a fixed migration-seeded canonical SAGA catalog

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime DB reset **NOT_YET_UNTIL_DEPLOY**.
- Context: product owner quyết định ProjectType không còn là catalog động do ADMIN tạo. ProjectType trở thành fixed canonical SAGA taxonomy, seed bởi Backend migration. Đây là taxonomy nội bộ của SAGA, không phải chuẩn IEEE/ISO.
- **Codes/names cuối cùng (đã audit lại và chỉnh sửa in-place trong V34 vì migration chưa từng deploy — xem DEC-088 phần Contribution criteria universe để phân biệt rõ ProjectType catalog vs Contribution criteria, hai khái niệm độc lập):** `DESIGN_ARCHITECTURE`/"Design & Architecture", `RESEARCH`/"Research", `TESTER`/"Tester", `DOCUMENT`/"Document" (4 UUID literal cố định không đổi). ProjectType **không quyết định hay khoá** Contribution weight — kể cả `DESIGN_ARCHITECTURE` vẫn tồn tại như ProjectType dù DESIGN không còn là Contribution criterion active.
- **Supersede DEC-082 (ProjectType bootstrap/management semantic only):** `POST /api/project-types` (ADMIN create) bị xóa khỏi `ProjectTypeController`; không có endpoint thay thế, không PUT/PATCH/DELETE. `GET /api/project-types` giữ nguyên contract (authenticated ADMIN/LECTURER/STUDENT, không CSRF, không Bearer) nhưng nay luôn trả đúng 4 canonical rows thay vì `[]`/dynamic set. Phần Project create (`POST /api/teams/{teamId}/projects` bắt buộc `projectTypeId`; thiếu → `PROJECT_TYPE_REQUIRED`; không tồn tại → `PROJECT_TYPE_NOT_FOUND`) **không đổi**.
- **Migration `V34__replace_project_type_with_canonical_catalog.sql`:** null hóa `project.project_type_id` trước, `DELETE FROM project_type`, rồi insert đúng 4 canonical row với UUID literal cố định và codes/names ở trên. Không drop table/FK, không đổi schema `project_type`, không hard-delete Project. Existing Project mất old ProjectType reference và đọc lại `projectType=null` (legacy-compatible contract) — đây là product-approved reset catalog, không phải data loss ngoài ý muốn.
- **LocalDemoDataSeeder:** không còn find-or-create ProjectType tùy ý; resolve canonical `DESIGN_ARCHITECTURE` qua `ProjectTypeRepository.findByCode` (hằng số `ProjectType.CODE_DESIGN_ARCHITECTURE`), fail rõ (`IllegalStateException`) nếu catalog thiếu thay vì tạo type thứ 5.
- **Contracts:** OpenAPI operation count giảm từ **150** xuống **149** (xóa đúng 1 public POST operation, không thêm route mới). Migration head **V33 → V34**. Contribution formula, Peer Review, Rubric, ProjectGroupWeightConfig, AI Agent, Lecturer/Admin Dashboard, Jira/GitHub, Student progress authorization **không đổi**.

## DEC-085 — Student progress LEADER auth uses exact Team relation, not Course uniqueness

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime smoke **TBD**.
- Context: `GET /api/v1/courses/{courseId}/students/{studentId}/progress` trả 409 `BUSINESS_CONFLICT` khi STUDENT LEADER xem Student khác. Throw site: `LecturerAnalyticsAuthorizationService.requireUniqueCourseMembership` / `requireActorCourseMembership` (`size != 1`). Handler generic-hóa message. Runtime Course `61ce8420-bb6b-4e0f-b424-81d07d6cc404`: target `82c68f64-4277-4b07-84c5-e2de99d07bdc` vừa MEMBER Group 2 vừa LEADER SAGA Local Demo Team. Actor Group 2 LEADER chỉ có 1 membership. 409 vì **target** multi-membership, không phải duplicate row cùng Team.
- Quyết định: STUDENT `/progress` không còn yêu cầu actor/target unique trong toàn Course. LEADER được đọc khi target có `TeamMember` thuộc **union các Team actor đang LEADER**. Trả đúng membership của exact Team đó. MEMBER vẫn chỉ self; MEMBER nhiều Team không LEADER → 409 (progress DTO cần đúng một Team). Target nằm trên nhiều Team actor cùng lead → 409. Cross-Team / cross-Course / MENTOR → 403. ADMIN/LECTURER `requireStudentInCourse` uniqueness **không đổi**. Không mở Course-wide Leader read. Không mở activities / contribution-detail / early-warnings / dashboard. Không migration, không xóa membership production.
- **Class/Course scope (audit 2026-08-15):** `Class` entity tồn tại (`class` / `class_code`). Course create `CourseRequest.classId` → persist `Course.clazz` (`@JoinColumn(name = "class_id")`). Course HTTP response là entity nên JSON field `clazz`. Không có Student–Class enrollment. Student vào Course chỉ qua `TeamMember → Team → Course`. `/progress` chỉ `{courseId, studentId}` — **không** thêm `classId`. Auth = request `courseId` + exact `TeamMember` + `roleInTeam`. LEADER không global và không Class-wide. LEADER Course A không áp cho Course B trừ khi actor independently LEADER exact Team của Course B. Cùng Class, khác Course → 403.
- Contracts: OpenAPI count vẫn **150**. Migration head vẫn **V33**. GET session, no CSRF, no Bearer.

## DEC-084 — Lecturer teams-progress supports parallel active Sprints

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- Context: Jira/SAGA đã persist nhiều Sprint `active` trên một Project (Parallel Sprints). `teams-progress` fail-closed 409 khi `>1` active, làm đỏ Lecturer Dashboard dù trends/velocity/burndown/progress đã parallel-safe. Không invent primary Sprint; không aggregate metrics.
- Quyết định MODEL B: `TeamProgress.activeSprints[]` là authority khi multiple active. Filter giữ `deletedAt == null` và `state equalsIgnoreCase "active"`. Order deterministic = existing `sprintOrder()` (`startDate` nullsLast, rồi `id`) — **không** là primary.
- Semantic: 0 active → `activeSprints=[]`, `currentSprint=null`, legacy `currentSprint*` = 0. 1 active → `activeSprints` size 1 và `currentSprint` + legacy counters giữ behavior cũ. `>1` active → `activeSprints` per-sprint metrics, `currentSprint=null`, legacy counters = 0, **không 409**.
- Không đổi trends 409 “Multiple Teams reference the same Project”, Student progress, burndown (`sprintId` explicit), velocity, Jira upsert/start/close, auth/session/CSRF, OpenAPI operation count, migration.
- FE: `activeSprints.length > 1` thì list/picker; burndown dùng `activeSprints[i].sprintId`. Không dùng `currentSprint` làm authority khi list `>1`.

## DEC-083 — OIDC avatar, Student progress Team access, Lecturer direct Course slice weights

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; Cognito Google login avatar runtime **TBD_DEPLOYMENT_SMOKE**.
- Context: product yêu cầu (1) đồng bộ avatar từ OIDC `picture`, (2) STUDENT MEMBER/LEADER đọc progress hiện hữu, (3) Lecturer sửa trực tiếp Course Code/Document/Design weights. Không đổi Contribution formula, Peer Review, Rubric, individual contribution override hay ProjectGroupWeightConfig. DEC-082 giữ nguyên như snapshot OpenAPI 149 / V32.
- **Avatar:** login đọc standard OIDC claim `picture`. URL HTTP(S) bounded/safe được persist/update vào nullable `avatar_url` trên Student, Lecturer và Admin (V33). Picture absent/invalid: login vẫn thành công, **không wipe** avatar đã lưu. Không nhận avatar từ browser, không download image, không lưu Google/Cognito token, không hardcode Google hostname. `GET /api/auth/me` trả `avatarUrl` từ session `SagaPrincipal`. Student Basic Info đọc `Student.avatarUrl` (nullable). Operator AWS Console: Google IdP scopes `openid email profile` và mapping Google `picture` → Cognito `picture` = **CONFIRMED_CONSOLE_CONFIGURATION**. Claim có mặt trên login runtime = **TBD_DEPLOYMENT_SMOKE**.
- **Student progress:** giữ `GET /api/v1/courses/{courseId}/students/{studentId}/progress`. Actor chỉ từ `SagaPrincipal.localProfileId`. ADMIN giữ access; LECTURER chỉ exact Course instructor. STUDENT MEMBER: self 200, teammate 403. STUDENT LEADER: self và exact same Team 200; cross-Team/cross-Course 403. MENTOR / no membership 403; anonymous 401. Ambiguous multiple Team membership trong một Course: **409 CONFLICT** (fail-closed). **Không** mở STUDENT cho activities, contribution-detail, early-warnings hay Lecturer Dashboard. GET: `JSESSIONID`, không CSRF, không Bearer.
- **Course slice weights:** official FE mutation = `PUT /api/v1/courses/{courseId}/contribution-slice-weights` với `{codeWeight, documentWeight, designWeight}` thang 0–100, tổng 100 ± 0.01. Actor từ principal; **không** `lecturerId`. LECTURER exact instructor success; other Course / STUDENT / ADMIN direct PUT = 403. GET cùng resource: ADMIN mọi Course; LECTURER chỉ Course mình dạy. CSRF bắt buộc trên PUT. Legacy `POST .../contribution-slice-weight-requests` + Admin `PUT .../decision` **giữ backward-compatible / deprecated for new FE**. Direct PUT chỉ sửa Course fallback; precedence Project+Team `ProjectGroupWeightConfig` (0–1, tổng 1.0) trước, Course weights sau. Formula / Peer Review / Rubric unchanged.
- **Contracts:** OpenAPI operation count = **150** (local contract test PASS). Migration head = **V33**. Full clean **1019 tests / 23 failures / 8 errors / 0 skipped**. `FULL_SUITE_GREEN = NO`. 22 CSRF isolation (grouped rerun PASS) + 1 DEC-023 roster. 8 `MyCourseTeamMembersIntegrationTest.cleanUp` FK errors chỉ full-suite order, isolated rerun PASS → `TEST_ISOLATION_ORDER_DEPENDENT_TBD`. **NEW_STABLE_A_B_C_FEATURE_REGRESSION = NONE_PROVEN.**

## DEC-082 — Merged main Project/Lecturer/Admin/AI dashboard and OpenAPI/migration baseline

- Ngày: 2026-08-15; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST cho contract local; deployment/Swagger runtime **TBD**.
- Context: các lane `parallel/project-type`, `parallel/lecturer-dashboard`, `parallel/ai-agent-completion` và `parallel/admin-dashboard-v1` đã merge vào `main` (`bcba831` + OpenAPI/migration contract reconciliation). Docs phải phản ánh exact source/test hiện hành, không proposal.
- **ProjectType:** catalog động do ADMIN quản lý qua `POST /api/project-types` (CSRF). `GET /api/project-types` cho authenticated ADMIN/LECTURER/STUDENT, GET không CSRF. Không có canonical production seed; DB mới có thể trả `[]` cho tới khi ADMIN tạo loại đầu tiên. `POST /api/teams/{teamId}/projects` bắt buộc `projectTypeId`; thiếu → `PROJECT_TYPE_REQUIRED`; type không tồn tại → controlled not-found; response/detail gồm ProjectType.
- **Group weights:** `PUT /api/projects/{projectId}/group-weights` lưu exact Project+Team config với Code/Document/Design chuẩn hóa tổng = 1.0. Authorization hiện hành: ADMIN hoặc LECTURER instructor của Course owning Team. Contribution đọc exact Project+Team override trước, fallback Course-level slice weights khi thiếu. **Công thức Contribution, Peer Review và Rubric không đổi.** `criteriaConfig` trên ProjectType chỉ là metadata. Live HTTP Contribution path là `TeamContributionService`; `ContributionCalculationService` tồn tại/wired cho consistency nhưng không phải HTTP authority hiện hành.
- **Admin Dashboard V1:** `GET /api/admin/reports/anomalies` và `GET /api/admin/reports/graph-processing` là ADMIN session only; LECTURER/STUDENT 403; anonymous 401; GET không CSRF; không Bearer. Anomalies: `OVERDUE_TASK` SUPPORTED với count thật; `MSR`/`DEADLINE_PROCESS`/`SNA_ISOLATION` = `supportStatus=TBD` và `count=null` (không dùng 0). Graph-processing: `periodDays=7`, `historySupported=false`, `points=[]`; không fabricate lịch sử.
- **AI Agent trust boundary:** Browser → Backend `/api/v1/ai/**` bằng `JSESSIONID` (+ CSRF cho unsafe). Backend ↔ AI bằng service token nội bộ. AI không nhận browser session, không đọc SAGA business DB, không gọi Jira/GitHub trực tiếp và không trở thành business authority. Public OpenAPI có đúng 7 route `/api/v1/ai/**`; `/internal/ai/**` `@Hidden`, không phải FE contract. HF deployment/browser AI product smoke và commit-review production worker topology vẫn **TBD**.
- **OpenAPI / migration:** sau audit route-set, generated OpenAPI operation count = **149** (contract test PASS). Migration head = **V32** (`V30` AI delegation, `V31` project type, `V32` project group weight); không collision. Full clean sau reconciliation: **994 tests / 23 failures / 0 errors** — 22 CSRF test-isolation flakes (grouped reruns green) + 1 DEC-023 Course roster baseline. **Không ghi FULL_SUITE=PASS.** Không có stable feature regression mới được chứng minh sau OpenAPI/migration reconciliation.
- Evidence: `GeneratedOpenApiDocumentationIntegrationTest`, `RubricMigrationContractTest`, Admin/Project/Lecturer/AI controllers và targeted suites; full clean classification như trên. Shared docs sync không đổi production Java/migration trong lane docs-only.

## DEC-074 — External Jira issue delete dùng authenticated tombstone; generic sync sở hữu estimation projection

- Ngày: 2026-08-13; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime **TBD_DEPLOYMENT_SMOKE**.
- Context: webhook registration đã gồm issue create/update/delete, nhưng processor cũ gửi cả ba vào updated-window search. Cách đó hydrate được create/update nếu delivery + window thành công, nhưng issue đã delete không còn trong search để tombstone. Generic search cũ chỉ discovery Sprint field nên không bao giờ project Story Point dù canonical per-mutation GET đã hỗ trợ parser J1I.
- Quyết định create/update: giữ authenticated encrypted deduplicated receipt → shared canonical reconciliation. Không parse raw webhook thành canonical Task. Scheduler/manual reconciliation tiếp tục là fallback.
- Quyết định estimation: mỗi Jira sync discovery exact estimation field từ board configuration, yêu cầu field đó trong search và đánh dấu projection authoritative chỉ khi provider thật sự trả property. Whole non-negative string/number dùng normalization DEC-065. Explicit null replace/clear local; omitted property giữ local để tránh accidental clear. Không hardcode field ID.
- Quyết định delete: sau authentication, dedup và board resolution, `jira:issue_deleted` chỉ dùng minimal issue ID hoặc key để lookup Task trong đúng owning Project rồi set `deletedAt` UTC theo DEC-035. Stable ID thắng; key chỉ fallback khi ID thiếu. Unknown/already tombstoned là controlled no-op; không hard-delete/cascade hoặc cross-project lookup.
- Ordering: `JiraIssueUpsertService` không bao giờ clear `deletedAt`. Vì vậy delete tombstone là monotonic đối với generic canonical snapshots và snapshot cũ chạy sau delete không resurrect Task. Không thêm remote event-version ordering hoặc schema/migration.
- Diagnostics/operations: log chỉ receipt ID, local board ID, event/result và sync stage/count/category; không raw payload, external issue identity, token/secret. Existing Admin health được mở rộng local-only với latest safe receipt summary và latest Jira webhook-maintenance result. Mỗi maintenance attempt persist `SyncJobLog OTHER` stage `WEBHOOK_MAINTENANCE`, nên `/sync-history` có evidence success/failure mà không provider-live call. Targeted Jira/webhook/admin regression pass **13 suites / 184 tests / 0 failures / 0 errors / 0 skipped**. Full clean chạy **134 suites / 848 tests / 2 failures / 0 errors / 0 skipped**: baseline Course roster/DEC-023 đã biết và một notification ordering failure ngoài scope; notification suite rerun riêng pass **8/8**.

## DEC-073 — Gmail REST API HTTPS thay Gmail SMTP cho Student Course Invitation

- Ngày: 2026-08-11; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST, production delivery **TBD_DEPLOYMENT_SMOKE**.
- Deployment reason: Railway Trial đã chứng minh kết nối `smtp.gmail.com:587` timeout/`MailSendException`; bằng chứng này chỉ kết luận SMTP transport không dùng được trong deployment hiện tại, không kết luận Gmail password/App Password sai. Luồng mới là Railway → Google OAuth HTTPS → Gmail API HTTPS.
- **Decision:** giữ nguyên transactional outbox, `StudentInvitationProcessor`, composer/template và `StudentInvitationDeliveryAdapter`; production implementation chuyển sang `GmailApiStudentInvitationDeliveryAdapter`. Adapter POST OAuth refresh-token tới `https://oauth2.googleapis.com/token`, sau đó POST MIME Base64URL trong JSON `raw` tới Gmail `users.messages.send` qua HTTPS.
- Reuse Spring `RestClient` + JDK HTTP stack và `INTEGRATION_HTTP_CONNECT_TIMEOUT`/`INTEGRATION_HTTP_READ_TIMEOUT` hiện hữu; mặc định 3/10 giây. Không provider call lúc startup, không Gmail live health probe và không thêm Google client SDK.
- Cấu hình production dùng đúng năm biến bắt buộc: `GMAIL_API_CLIENT_ID`, `GMAIL_API_CLIENT_SECRET`, `GMAIL_API_REFRESH_TOKEN`, `GMAIL_API_SENDER_EMAIL`, `GMAIL_API_SENDER_NAME`. Thiếu bất kỳ biến nào chọn unavailable adapter nhưng backend/import vẫn start và commit bình thường. Scope cấp cho refresh token phải là least-privilege `https://www.googleapis.com/auth/gmail.send`.
- OAuth credential là backend-only; không expose qua FE/API/Swagger/actuator. FE vẫn chỉ nhận import response/outbox enqueue semantics, không có Bearer provider token hay generic send-email endpoint.
- Access token chỉ cache thread-safe trong memory, refresh trước expiry 60 giây và không persist. Secret/token/Authorization/form/raw MIME/raw provider response/recipient/body không được log; request body nhạy cảm được đưa vào `RestClient` dưới dạng byte array để debug log không serialize nội dung.
- MIME giữ UTF-8 `multipart/alternative` text + HTML; FROM dùng configured sender name/email, TO dùng message recipient; header CR/LF injection bị từ chối. Chỉ Gmail send success mới mark `SENT`.
- Network/429/5xx và 403 có reason rate/quota được phân loại retryable; invalid grant/client, malformed token response, 400/401 và 403 permission/sender là non-retryable. Outbox hiện không persist retryability, vì vậy processor vẫn mark tất cả failure `FAILED` và scheduler có thể retry tới max attempts. Không tuyên bố exactly-once: crash sau provider accept nhưng trước local `SENT` vẫn có thể tạo duplicate.
- Audit xác nhận Spring Mail/JavaMail chỉ phục vụ adapter invitation cũ, nên `spring-boot-starter-mail`, SMTP properties và Mail health surface được loại bỏ. `MANAGEMENT_HEALTH_MAIL_ENABLED` và toàn bộ `SPRING_MAIL_*` không còn là Railway contract.
- **Supersession:** DEC này supersede DEC-068 về provider/config/transport hiện hành. DEC-068 được giữ nguyên làm lịch sử. DEC-019 vẫn giữ nguyên toàn bộ outbox/dedup/transaction/retry/template/provisioning semantics; không đổi import business logic, authorization, session, CSRF hay `CourseService`.

## DEC-068 — Gmail SMTP là production adapter cho Student Course Invitation

- **Decision:** giữ nguyên transactional outbox, `StudentInvitationProcessor` và `StudentInvitationDeliveryAdapter`; production implementation dùng Spring Boot Mail `JavaMailSender` với Gmail SMTP, MIME UTF-8 text + HTML và FROM từ configured mail username.
- Adapter chỉ khả dụng khi host/port/username/password/auth/STARTTLS và sender bean đầy đủ. Thiếu cấu hình phải fail-safe thành unavailable delivery trong khi backend vẫn start; không fake/default credential và không startup SMTP connection test.
- Chỉ adapter success mới mark `SENT`; provider/config exception propagate tới processor để mark `FAILED`. Retry, max attempts, stale PROCESSING recovery, SENT no-resend, at-least-once semantics và không rollback Student/Team/TeamMember giữ nguyên.
- Linked CTA là `Đăng nhập SAGA`; unlinked CTA là `Đăng ký / Kích hoạt tài khoản SAGA`. URL lấy từ `STUDENT_INVITATION_LOGIN_URL`; wording generic, không hard-code OAuth callback/Google và không chứa password/token/UUID/Cognito subject.
- Safe log chỉ gồm `provider=GMAIL_SMTP`, stage, attempt, result, category và exception class. SMTP connect/read/write timeout bounded 5000/10000/10000 ms.
- **Supersession:** quyết định này chỉ supersede phần **production-provider TBD** của DEC-019. Mọi quyết định khác trong DEC-019 về outbox, dedup, transaction, retry, template intent và provisioning vẫn còn hiệu lực.
- **Evidence:** targeted invitation/import 37/37. Full `./mvnw.cmd clean test` chạy **116 suites / 753 tests / 1 failure / 0 errors / 0 skipped**; failure duy nhất là contract roster DEC-023 do `CourseService#getCourseRoster` baseline còn đọc invitation outbox, không thuộc Gmail delivery. Gmail giữ trạng thái **CONFIRMED_SOURCE_TEST**; toàn working tree chưa green và Gmail production là **TBD_DEPLOYMENT_SMOKE**.

## DEC-067 — Normal Update Priority dùng business enum, backend sở hữu provider-ID resolution

- Ngày: 2026-08-10; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- Context: DEC-066 đã chặn `priorityId` stale nhưng normal FE không có public contract để lấy provider ID. Create Task đã xác lập ownership đúng: FE gửi business enum, backend resolve metadata; Update cần cùng boundary.
- Quyết định: bổ sung optional `priority` (`LOW|MEDIUM|HIGH|CRITICAL`) vào sparse Update. Backend lấy `editmeta` đúng issue và dùng chung resolver J1C: dedup ID, unique exact canonical name thắng, unique semantic fallback chỉ khi không có exact; zero/multiple fail closed. `priorityId` giữ advanced override; gửi cả hai fail `JIRA_PRIORITY_INVALID` trước claim/provider call.
- Idempotency: fingerprint ghi riêng `priority` và `priorityId` theo raw request intent; không persist resolved ID, payload hoặc metadata. Canonical reconciliation/state machine không đổi.
- Boundary: không tạo editmeta API, không đổi components contract, không implement Issue Type change, không hardcode Jira ID/customfield, không Bearer/migration/isolation change. Runtime là `TBD_DEPLOYMENT_SMOKE`.

## DEC-066 — Update priority phải validate theo editmeta issue hiện tại

- Ngày: 2026-08-10; trạng thái: ACCEPTED.
- Evidence: update đã đọc `GET /rest/api/3/issue/{issueIdOrKey}/editmeta` nhưng trước đây chỉ kiểm tra key `priority`, rồi forward `priorityId` bất kỳ vào Jira PUT. Điều này giải thích 400 `JIRA_REQUEST_REJECTED` khi client gửi ID stale hoặc không được phép.
- Quyết định: `priorityId` update là provider ID scoped theo edit metadata của issue; khi có mutation, field phải editable và ID phải xuất hiện trong `priority.allowedValues`. Không tái dùng create resolution vì create metadata và editmeta là authority khác nhau.
- Hệ quả: invalid trả local `400 JIRA_PRIORITY_INVALID` trước provider mutation; valid giữ payload Jira `{ "fields": { "priority": { "id": "..." } } }` và canonical reconciliation hiện hữu. Issue Type update vẫn TBD/NOT_IMPLEMENTED; không hardcode ID, thêm endpoint, migration, Bearer hay thay đổi session/CSRF.

## DEC-065 — J1I canonical decimal normalization cho TASK_ESTIMATION

- Ngày: 2026-08-10; trạng thái: ACCEPTED.
- Evidence: tài liệu Jira Software Cloud mô tả Estimate issue for board trả `200` với `fieldId` và `value` string, ví dụ `"8.0"`. Source PUT hiện hữu không parse body; canonical issue GET cũ chỉ chấp nhận JSON integer cho discovered estimation field.
- Quyết định: không tạo PUT-response DTO hay dùng response value làm source of truth. Chuẩn hoá riêng canonical Story Point bằng `BigDecimal`, chỉ nhận decimal whole không âm nằm trong `Integer`; sau đó giữ fresh canonical target verification J1H.
- Hệ quả: 200 body malformed không quyết định completion; canonical GET mới quyết định. Canonical invalid sau remote success giữ `REMOTE_SUCCEEDED`, không `FAILED` và không replay PUT. Không hardcode customfield/Jira ID, không migration hay đổi isolation.

## DEC-064 — J1H finalization TASK_ESTIMATION theo target-aware canonical recovery

- Ngày: 2026-08-10; trạng thái: ACCEPTED.
- Evidence: `markRemoteSucceeded` dùng transaction riêng. Nếu orchestration object không nhận lại remote identity, canonical reconcile ném `JIRA_WRITE_OPERATION_IN_PROGRESS` dù Jira đã áp estimation.
- Quyết định: đồng bộ remote id/key/status trong object ngay sau remote success; canonical GET phải yêu cầu estimation field được discovery theo board, upsert và fresh-read xác nhận `storyPoint == request.value` rồi mới `complete`.
- Hệ quả: retry cùng key/request không replay mutation Jira; canonical failure hoặc mismatch giữ `REMOTE_SUCCEEDED`. Vì schema chỉ có fingerprint hash, scheduler/recovery nền không đủ target intent để complete `TASK_ESTIMATION` và phải giữ pending recovery. Không thêm migration, hardcode customfield/Jira ID, Bearer hay thay đổi isolation toàn cục.

## DEC-062 — Jira Task update chỉ gửi diff canonical có thể chứng minh (2026-08-10)

**Status: ACCEPTED.**

- `JIRA_EDIT_FIELD_NOT_ALLOWED` là local policy sau editmeta, không phải provider PUT 400; không retry provider mù.
- Chỉ suppress summary, priority có metadata name, dueDate, labels, component IDs khi canonical bằng nhau. Description ADF flatten không đủ để chứng minh equality.
- Main update không mở rộng type/assignee/Sprint/estimation/status; giữ endpoint riêng để không tạo partial-result contract mới.

# SAGA — Nhật ký quyết định kỹ thuật

## DEC-063 — A13 không mở API Admin advanced khi capability chưa khép kín (2026-08-10)

**Status: ACCEPTED.**

- Không thêm endpoint per-user audit: local actor ID chỉ forward-only, không đủ complete historical semantics/index/retention policy.
- Không thêm role/password/Course membership/notification/generic settings: thiếu lần lượt transition governance, Cognito Admin reset contract, TeamMember retention contract, notification schema-consumer lifecycle và global typed-setting contract.
- Admin cross-access giữ authorization explicit theo shared endpoint; không duplicate `/api/admin/courses/**`, không thêm dashboard charts, migration, Mongo backfill, Bearer hoặc Cognito Admin API.

## DEC-059 — A12 Admin closure boundary (2026-08-09)

**Status: ACCEPTED.** Admin core được đóng theo source/test hiện có: user list/status/import,
master-data CRUD có retention guard, typed active Semester, progress/export và
operational reads. Các gap còn lại là governance/business-contract blocker, không phải feature
được ngầm phê duyệt. Không thêm per-user audit, broadcast, impersonation, role/password mutation,
generic settings, membership mutation, Project DELETE, Bearer hoặc Cognito Admin API.

## DEC-049 — Account lifecycle Student và Lecturer

**Status: ACCEPTED / CONFIRMED bởi business decision, source và test.**

Student và Lecturer sở hữu `AccountStatus`; Admin không có status trong milestone. Lecturer cũ/mới mặc định ACTIVE qua V21. Admin có thể đặt Student/Lecturer sang ACTIVE, INACTIVE hoặc SUSPENDED; PENDING là Student provisioning-only. Business API browser-session dùng current local DB status mỗi request, còn auth me/csrf/logout được miễn để hiển thị/làm sạch session. Mutation không cascade Course ownership, TeamMember, Project, integration hay history; Cognito và role không bị thay đổi.

## DEC-048 — Course Update và Soft Delete có dependency guard

**Status: ACCEPTED / CONFIRMED bởi source và test.**

Course dùng tombstone `deletedAt` qua V20. Create/update phải resolve Subject, Class, Semester active trước khi ghi. DELETE chỉ đặt tombstone khi không có Team, Project, StudentCourseInvitation hay TaskWeightConfig trỏ tới Course; bất kỳ dependency nào trả 409 generic. Không hard-delete, cascade, detach hay sửa membership/import delivery. Course tombstone không xuất hiện active read và courseCode không được tái dùng. Import resolve Course active-only; Contribution mutation và resolver analytics/roster/Contribution giữ behavior baseline, cần audit retention riêng.

## DEC-047 — Semester Update và Soft Delete có dependency guard

**Status: ACCEPTED / CONFIRMED bởi source và test.**

Semester dùng cùng retention model với Subject/Class nhưng chỉ sau audit riêng: inbound reference duy nhất được chứng minh là `Course.semester`. `DELETE` đặt `deletedAt` qua V19, active reads loại tombstone, và `existsBySemesterId` fail closed 409 trước khi delete nếu còn Course. Không hard-delete, detach, cascade, hay sửa Course service. Code tombstone vẫn unique; SemesterRequest được tái dùng cho PUT nguyên khối. Evidence: Semester entity/repository/service/controller, CourseRepository, V19, SemesterUpdateSoftDeleteIntegrationTest.

## DEC-046 — Backend sở hữu Jira Task create metadata (2026-08-09)

**Status: ACCEPTED.**

**J1C clarification.** Dedup canonical provider ID là bước đầu. Khi nhiều ID semantic còn lại,
resolver ưu tiên đúng một provider name normalize trùng business enum; chỉ khi không có exact mới
dùng semantic fallback nếu còn đúng một ID. Nhiều exact hoặc fallback distinct tiếp tục fail closed,
không sort/pick-first và không làm FE gửi Jira numeric ID.

**Decision.** FE normal gửi `TaskType`/`Priority` business; backend lấy create metadata của đúng Jira Project cho từng request, resolve một candidate duy nhất rồi gửi provider ID canonical. `issueTypeId`/`priorityId` hiện hữu được giữ optional làm advanced override và phải được validate trong metadata trước mutation.

**Rationale.** Jira numeric IDs là provider/project-specific. Gọi create-fields với issue type override chưa validate có thể trả 404 generic; priority stale trước đây có thể bị forward tới `POST /issue`.

**Consequences.** Không hardcode hoặc cache cross-project metadata. Zero/multiple auto candidate fail closed; explicit invalid trả lỗi local specific. Không đổi authorization, credential, idempotency state machine, session/CSRF, entity hay migration.

## DEC-045 — Jira simple-board capability bằng read-only Sprint probe (2026-08-07)

**Status: PARTIAL — source/test CONFIRMED; SDP production probe TBD. Supersedes giả định chọn board từ `boardFeature=SPRINTS`.**

**Context.** SDP có board `35`, `type=simple`, association `10034/SDP`; Board Features rỗng và Project Features không expose Sprint identifier hữu dụng. Hai endpoint metadata không đủ evidence để quyết định capability, nên không hardcode identifier/localized text.

**Decision.** Giữ `scrum` là candidate trực tiếp. Với `simple`, gọi read-only 3LO `GET /rest/agile/1.0/board/{boardId}/sprint?maxResults=1`, cần scope `read:sprint:jira-software`. 200 với page object có `values` array, kể cả rỗng, là evidence `SPRINT_ENDPOINT_SUPPORTED`; chỉ một candidate được persist. Probe không parse/persist Sprint và không thêm public endpoint.

**Failure semantics.** 400 trả fail-closed `JIRA_SPRINT_CAPABILITY_UNCONFIRMED`; 401, 403, 404, 429, 5xx/network và malformed 2xx lần lượt map `JIRA_ACCESS_REVOKED`, `JIRA_ACCESS_FORBIDDEN`, `JIRA_BOARD_NOT_FOUND`, `JIRA_RATE_LIMITED`, `JIRA_PROVIDER_UNAVAILABLE`, `JIRA_RESPONSE_INVALID`.

**Diagnostics và verification.** Log chỉ có project/board/type, HTTP result probe, candidate reason/selection; không raw response, Sprint name hoặc credential. Full `./mvnw.cmd clean test` pass 99 suites / 586 tests / 0 failures / 0 errors / 0 skipped. Production phải relink SDP để xác nhận probe 35; không suy diễn rằng mọi simple board đều hỗ trợ Sprint.

**Metadata diagnostics.** Parser Board/Project Features chỉ giữ facts machine-safe nullable và chỉ báo invalid khi root/features/item/type thực sự sai contract. Metadata không quyết định simple-board capability. Link preflight gồm cả scope read Sprint cần cho probe.

**Consequences.** Không migration hay đổi 3LO/session/CSRF/retained-row/mutation policy. Production outcome vẫn **TBD** đến deploy và relink SDP có diagnostics an toàn.

## DEC-044 — Jira relink là provider-identity-aware upsert (2026-08-07)

**Status: ACCEPTED.**

**Context.** `jira_board` có hai identity độc lập: ownership local `project_id` và provider identity unique `(cloud_id, jira_project_id)`. Disconnect cố ý giữ row như history anchor. Lookup chỉ theo Project có thể không thấy canonical provider row trong race/legacy retention path rồi tạo entity mới; `saveAndFlush` khi đó vi phạm `uk_jira_cloud_project` và gây HTTP 500.

**Decision.** Sau fresh OAuth grant, accessible-resource validation, link-scope preflight, canonical Jira Project và Scrum board discovery, local service khóa/resolve cả hai identity trong transaction ngắn. Không row thì insert. Row cùng Project và provider identity thì update/reuse cùng `JiraBoard.id`. Provider identity của Project khác trả `409 JIRA_PROJECT_ALREADY_LINKED` với message an toàn; ownership không chuyển. Retained Project có provider identity khác trả `409 JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED`; không overwrite history anchor. Provider I/O và webhook không giữ DB lock.

**Race/error policy.** `DataIntegrityViolationException` chỉ là fallback race: request mở transaction mới reload/upsert canonical row. Same Project/provider coalesce; different Project thành conflict. Race không reconcile được trả `409 JIRA_BOARD_UPSERT_CONFLICT`; API/log không lộ SQL, constraint, token, credential, cookie/CSRF hoặc raw provider body.

**Consequences.** Giữ `uk_jira_cloud_project`; không migration, hard-delete, detach/move Task/Sprint/history hoặc thay đổi mutation policy. Browser session + CSRF và fresh OAuth grant giữ nguyên. Production runtime còn **TBD** đến deploy/smoke.

**Evidence.** `JiraBoardLinkPersistenceService`, locking queries của `JiraBoardRepository`, `ProjectIntegrationServiceJiraLinkTest`, `JiraBoardLinkPersistenceServiceTest`, `JiraBoardLinkConcurrencyIntegrationTest`, Jira OAuth/scope/discovery/disconnect/sync/write regressions; full Maven 99 suites / 560 tests / 0 failures / 0 errors / 0 skipped.

## DEC-043 — Xác thực Jira site scope trước link và chuẩn hóa 3LO gateway (2026-08-07)

**Status: ACCEPTED.**

**Context.** OAuth grant mới có thể hợp lệ nhưng thiếu Jira Software Agile scope. SAGA discover Scrum board trong `/jira/link`; vì thế HTTP 401 ở Agile API trước đây có thể bị hiểu nhầm là `JIRA_ACCESS_REVOKED` dù nguyên nhân là scope. 3LO cũng yêu cầu URL site-specific qua `api.atlassian.com/ex/jira/{cloudId}`.

**Decision.** `accessible-resources` là nguồn xác thực cloudId/site scope sau token exchange. Link chỉ dùng resource đã match và preflight scope đúng với provider operation của link; capability Sprint/Task khác được preflight tại operation của chúng. Thiếu scope trả `JIRA_SCOPE_INSUFFICIENT`. `JIRA_ACCESS_REVOKED` giữ nghĩa upstream 401 xảy ra sau preflight. URI provider được tập trung qua builder gateway, reject cloudId/path không hợp lệ, và không dùng FE site URL cho bearer request.

**Scope matrix (as-built).** Project/search/issue metadata read dùng `read:jira-work`; issue mutation dùng `write:jira-work`; dynamic webhook dùng `read:jira-work` + `manage:jira-webhook`; refresh cần `offline_access`; board discovery cần `read:board-scope:jira-software` + `read:project:jira`; board configuration dùng `read:board-scope.admin:jira-software` + `read:project:jira`; Sprint read/create-update-delete dùng lần lượt `read:sprint:jira-software`, `write:sprint:jira-software`, `delete:sprint:jira-software`; task backlog move/estimation dùng `write:board-scope:jira-software` và `write:issue:jira-software` theo operation hiện có.

**Consequences.** Atlassian Developer Console phải bật đúng toàn bộ scope matrix và backend authorization request phải cùng bộ scope. `offline_access` nằm trong authorization request để nhận refresh token, nhưng không được yêu cầu trong `accessible-resources.scopes`. Sau thay đổi scope, deploy không đủ: người dùng phải bắt đầu OAuth consent mới; grant/access/refresh token cũ không được giả định có scope mới. Scope chỉ mở khả năng app, không vượt Jira permission của người dùng.

**Evidence.** Jira Provider URI/resource tests, Jira link least-privilege preflight test và full Maven 97 suites / 549 tests / 0 failures / 0 errors / 0 skipped. Runtime production vẫn **TBD** đến deploy và smoke test.

## DEC-042 — Jira hydration fail-isolated, Sprint 404 retention và fresh-grant relink

- Ngày: 2026-08-06; trạng thái: ACCEPTED.
- Quyết định: reconciliation hợp Sprint candidate từ issue batch (`ISSUE_BATCH`) và local active Sprint (`LOCAL_SPRINT`) để canonical hydration vẫn xảy ra khi Jira search trả 200/0 issues. Lỗi một Sprint được cô lập; Sprint khác tiếp tục, job finalizes `PARTIAL_FAILURE` và không advance cursor.
- Quyết định: Jira Agile Sprint 404 được biểu diễn bằng `JIRA_SPRINT_NOT_FOUND`, không map thành credential revoked và không tự tombstone/hard-delete Sprint, Task hoặc history. Cleanup chỉ được bổ sung sau khi có evidence/policy retention an toàn.
- Quyết định: disconnect giữ `jira_board` như history anchor nhưng retire credential, expiry, scopes và webhook state. Relink dùng fresh OAuth grant trong session, khóa retained row và callback state cũ của cùng project bị vô hiệu; không reuse credential cũ hay tạo duplicate row.
- Quyết định: log hydration failure chỉ chứa structured diagnostics an toàn (board ID, numeric external board ID hoặc `NOT_CONFIGURED`, projectKey, externalSprintId, upstream status/category, job/stage/source). Không log raw provider body, Authorization, token hoặc credential.
- Hệ quả: `DISCONNECTED` bị scheduler/claim/state-write loại trừ và worker recheck trước `getSprint`. Concurrent relink có lock ở source nhưng chưa có integration test đa luồng thực sự. ExternalSprintId/upstream status của incident lịch sử vẫn TBD.
- Evidence: `AutomaticSyncDispatcherImpl`, `JiraProviderClientImpl#getSprint`, `ProjectIntegrationService`, `JiraBoardStateWriteService`, `OAuthStateService`, `JiraBoardRepository`, và targeted/full Maven tests.

## DEC-041 - Chuẩn hóa Swagger/OpenAPI tiếng Việt tại thời điểm sinh tài liệu

- Ngày: 2026-08-06. Trạng thái: ACCEPTED / CONFIRMED từ source và generated OpenAPI test.
- Quyết định dùng `OperationCustomizer` và `OpenApiCustomizer` để bổ sung summary, description, tag, parameter/response/schema metadata cho toàn bộ operation sinh bởi springdoc 3.0.3. Cách này chỉ thay đổi OpenAPI document, không thay đổi HTTP behavior hay DTO JSON.
- Browser session `JSESSIONID` và global CSRF Swagger interceptor được giữ nguyên. Không thêm Bearer scheme, không thêm OAuth token input, không lặp `X-XSRF-TOKEN` vào operation. Webhook có `Authorization` vì đó là contract chữ ký provider có evidence, không phải auth header cho frontend.
- Evidence: `OpenApiConfig`, `VietnameseOpenApiDocumentationConfiguration`, `GeneratedOpenApiDocumentationIntegrationTest`, `SwaggerUiCsrfIntegrationTest`. Generated document có 96 operation; full Maven pass 97 suites / 538 tests.

## DEC-040 - Project GitHub reads, reconnect, and sync-history

- Date: 2026-08-06. Status: ACCEPTED / CONFIRMED by source and local tests; production provider runtime remains TBD.
- Dashboard is local-state only and reuses project read authorization. It excludes soft-deleted tasks and does not manufacture provider freshness.
- Branches and commits are fetched only by the backend using installation credentials. The frontend sends the branch as a query parameter, including slash-containing branch names; it never receives credentials.
- Reconnect is a manager-only, CSRF-protected state transition from `DISCONNECTED` to `BACKFILLING`. A pessimistic local-repository lock and the existing initial-backfill claim prevent duplicate active jobs; active work is coalesced rather than dispatched twice.
- Sync history is the paged manager-only route `/sync-history` with optional `targetSystem`, `status`, and `jobType` filters. `/sync-status` is retained as the legacy compact top-20 view, not the history API.
- Project DELETE remains blocked. Existing references include Team, Task, GitRepo, JiraBoard, JiraWriteOperation and additional historical/assessment/document records; no cascade or deletion policy has been verified.
- Evidence: `ProjectDashboardStatsService`, `GitHubProjectReadService`, `ProjectIntegrationService`, `GitHubSyncJobService`, `SyncJobLogRepository`, V18 and related tests. Full Maven: 96 suites / 537 tests / 0 failures / 0 errors / 0 skipped.

## DEC-039 — Sprint time dùng Instant UTC và board Scrum dùng external numeric ID

- Ngày: 2026-08-06. Trạng thái: ACCEPTED / CONFIRMED từ source và test local; runtime production TBD.
- HTTP Sprint nhận Instant ISO-8601 có offset, response trả Instant UTC; entity `LocalDateTime` giữ UTC semantics. Không đổi schema/JVM timezone, không cộng cứng UTC+7.
- Link Jira và lazy Create Sprint discover Agile boards theo project canonical. Chỉ đúng một `scrum` board được persist vào `jira_board.jira_board_id`; UUID entity local không phải Jira board ID.
- Zero/multiple Scrum board fail closed với `JIRA_SCRUM_BOARD_NOT_FOUND`/`JIRA_BOARD_SELECTION_REQUIRED`; malformed ID được repair, numeric valid ID không re-discover. Invalid config không tạo operation/provider mutation; recovery, idempotency và canonical fetch/upsert giữ nguyên.
- Không migration vì cột đã tồn tại. Full Maven tại `c770438`: 94 suites / 529 tests / 0 failures / 0 errors / 0 skipped.

Tài liệu ghi lại quyết định đã được code/runtime fact chứng minh và các đề xuất còn mở. `ACCEPTED` không có nghĩa production đã được kiểm chứng; evidence của từng quyết định xác định phạm vi xác nhận.

> Metadata audit hiện tại: branch `main`, HEAD thực tế `4f3dee9` (`4f3dee969ebd7ee03a94eb1b8133987ad622c66d`). Các SHA cũ bên dưới là checkpoint lịch sử. Full Maven gần nhất: 90 suites, 504 tests pass, 0 failures/errors/skips.

> Các DEC cũ có diễn đạt “HEAD hiện tại `200d866`” là mô tả tại thời điểm quyết
> định lịch sử; không thay thế metadata audit hiện tại ở trên.

> Audit ID: DEC-028 bị trùng trong lịch sử. Không renumber; ID lớn nhất đã dùng là
> DEC-031, vì vậy các quyết định ngày 2026-08-06 bắt đầu từ DEC-032.

## DEC-032 — Master Data DELETE dùng soft delete

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED cho Subject và Class; TBD cho Semester/Course.
- Quyết định: `DELETE` Subject/Class đặt `deletedAt`, không hard delete/cascade;
  active reads loại tombstone, Course dependency chặn delete và code tombstone
  không được tái sử dụng. Không suy rộng sang Semester/Course khi source chưa có.
- Hệ quả: quan hệ lịch sử được giữ; FE phải coi 409 dependency là domain guard.
- Evidence: `SubjectService`, `ClassService`, `SubjectRepository`, `ClassRepository`,
  `CourseRepository`, `SubjectUpdateSoftDeleteIntegrationTest`,
  `ClassUpdateSoftDeleteIntegrationTest`, V15, V16.

## DEC-033 — Jira Task/Sprint mutation dùng write-through

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED từ source và test.
- Quyết định: Jira là source of truth; mutation remote xảy ra trước, sau đó backend
  fetch canonical Jira issue/Sprint và upsert local. FE dùng session/JSESSIONID và
  CSRF, không Bearer; actor lấy từ `SagaPrincipal.localProfileId`.
- Hệ quả: SAGA database là canonical snapshot/read model cục bộ, không phải nguồn
  phát sinh trạng thái Jira độc lập. Production source discovery Jira field id và
  không hardcode `customfield_*`.
- Evidence: `ProjectTaskReadController`, `ProjectSprintController`,
  `JiraTaskWriteService`, `JiraSprintWriteService`, `JiraProviderClientImpl`,
  `JiraMutationControllerSecurityIntegrationTest`.

## DEC-034 — Jira mutation có persisted idempotency và recovery không blind retry

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED từ source và persistence tests.
- Quyết định: mọi Task/Sprint mutation bắt buộc `Idempotency-Key`; operation persist
  type, canonical SHA-256 fingerprint, actor, remote identity, trạng thái và safe
  error code. Không lưu token/raw provider payload. Nếu insert đụng unique constraint,
  transaction thứ nhất rollback hoàn toàn; transaction `REQUIRES_NEW` thứ hai reload
  theo project/key rồi kiểm type/fingerprint/status.
- Hệ quả: key dùng lại khác request trả conflict. `PENDING`/`UNKNOWN` không bị replay;
  recovery chỉ reconcile operation `REMOTE_SUCCEEDED`, không blind retry Create,
  Delete hoặc Transition có remote outcome chưa rõ.
- Làm rõ 2026-08-09: Task Create chỉ chuyển `COMPLETED` sau canonical local Task
  được xác nhận có thể trả response. Canonical fetch/upsert/xác nhận thất bại giữ
  `REMOTE_SUCCEEDED` và trả recovery-required; cùng key chỉ canonical recovery,
  không POST Jira lại. DEMO-8/DEMO-9 xác nhận WARN cũ sau completed_at không chứng
  minh DB còn `REMOTE_SUCCEEDED`; object log trước đây có thể giữ status cũ.
- Evidence: `JiraWriteOperationService`, `JiraWriteRecoveryService`,
  `JiraWriteOperation`, `JiraWriteOperationStatus`,
  `JiraWriteOperationServiceTest`, `JiraWriteOperationPersistenceTest`, V17.

## DEC-035 — Task/Sprint delete giữ dữ liệu liên quan bằng tombstone/cleanup

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED từ source và tests.
- Quyết định: Task delete gọi Jira trước rồi đặt `Task.deletedAt`. Sprint delete gọi
  Jira, set `Task.sprint = null`, flush association rồi đặt `Sprint.deletedAt`.
  Recovery `REMOTE_SUCCEEDED` áp dụng cùng local semantics.
- Hệ quả: read paths active-only không trả tombstone; không hard-delete audit,
  Contribution hoặc Peer Review data và không phá foreign-key bằng xóa Sprint vật lý.
- Evidence: `JiraTaskWriteService#delete`, `JiraSprintWriteService#delete`,
  `JiraWriteRecoveryService`, `Task`, `Sprint`, write/recovery tests, V17.

## DEC-036 — Chỉ canonical Jira Sprint được replace dates

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED; concurrency ordering limitation được ghi nhận.
- Quyết định: full Agile Sprint response có quyền cập nhật hoặc clear `startDate`,
  `endDate`, `completeDate`; provider normalize offset về UTC. Embedded Sprint từ
  Issue chỉ hỗ trợ association/reference/name và không clear canonical dates.
- Hệ quả: backfill, reconciliation và webhook dùng shared hydration; distinct Sprint
  id được fetch tối đa một lần/job, kể cả local row có date null. Do Jira response
  không có remote updated/version, canonical snapshots cạnh tranh theo
  last-processed-wins.
- Evidence: `JiraProviderClientImpl#toSprint`, `#parseSprintDateTime`,
  `JiraSprintUpsertService`, `JiraIssueUpsertService#resolveSprint`,
  `AutomaticSyncDispatcherImpl`, provider/upsert/dispatcher tests.

## DEC-037 — UUID scalar của JiraWriteOperation tuân theo convention JDBC CHAR

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED từ entity, migration và persistence test.
- Quyết định: UUID scalar `actorProfileId` cần explicit JDBC `CHAR` để khớp
  `actor_profile_id CHAR(36)`; chuỗi `requestFingerprint` cũng dùng JDBC `CHAR` để
  khớp `request_fingerprint CHAR(64)`. Giữ nguyên V17, không tạo V18 chỉ để sửa ORM.
- Hệ quả: startup/schema validation và persistence dùng cùng convention CHAR hiện có.
- Evidence: `JiraWriteOperation`, `JiraWriteOperationPersistenceTest`, V17.

## DEC-038 — Course Student Basic Info dựa trên Team membership

- Ngày: 2026-08-06.
- Trạng thái: ACCEPTED / CONFIRMED; avatar data source và enrollment độc lập là PARTIAL.
- Quyết định: `GET /api/v1/courses/{courseId}/students/{studentId}` xác định membership
  bằng `TeamMember -> Team -> Course`. ADMIN đọc mọi Course; assigned LECTURER được
  đọc; STUDENT 403; anonymous 401. Không có membership trả 404, nhiều legacy
  membership trả 409 và không tự mutate dữ liệu.
- Hệ quả: response trả basic account/team info; `accountStatus` là trạng thái tài
  khoản, không phải enrollment status. `team` không nullable; Student chưa Team chưa
  được hỗ trợ vì không có `CourseEnrollment`. `avatarUrl` nullable và hiện luôn null.
- Evidence: `CourseController#getCourseStudent`, `CourseService#getCourseStudentBasicInfo`,
  `CourseStudentBasicInfoResponse`, `TeamMemberRepository`,
  `CourseStudentBasicInfoIntegrationTest` (7 tests pass).

## DEC-028 — Contribution calculation reads source-of-truth; unresolved policies fail closed

- Date: 2026-08-04
- Status: ACCEPTED (working tree, not committed)
- **CONFIRMED:** the read-only calculation service uses project-scoped commit and
  document aggregates, DONE Jira task story points (null is one), and peer-review
  multipliers from `PeerReviewConfig`. All arithmetic is `BigDecimal`; no result
  snapshot is persisted and no HTTP API is introduced.
- **CONFIRMED:** Jira Task snapshots now include canonical plain-text description
  and replace-all component snapshots (`id`, `name`); V9 adds nullable
  `description` and `components_json` columns.
- **TBD/PARTIAL:** the source contains both Subject-null and Subject-specific
  peer-review configs without precedence evidence. Ambiguity or a missing
  multiplier is rejected instead of silently selecting a value. There is no
  persisted Contribution override model. Per-value negative or above-100
  overrides, all-overridden remainder, positive remaining budget with zero base,
  and rounding residuals remain Product Owner policy decisions.
- **SUPERSEDED 2026-08-06:** field discovery hiện đã được triển khai; production
  source không hardcode `customfield_*`. Các policy Contribution còn lại vẫn mở.

## DEC-001 — Dùng Spring Security OAuth2/OIDC và server-side session

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Backend là OAuth2/OIDC confidential client với Cognito.
- Quyết định: Sau OIDC callback, backend thay authentication bằng `SagaPrincipal` không chứa provider token và lưu security context trong HTTP session.
- Lý do: Giữ token provider phía backend, cung cấp browser session cho FE.
- Hệ quả: API cần `JSESSIONID`; session lifecycle thuộc backend.
- Rủi ro: Chưa thấy shared session store; redeploy/multi-instance cần kiểm chứng.
- Evidence: `SecurityConfig#securityFilterChain`, `CognitoAuthenticationSuccessHandler#replaceWithTokenFreeSessionAuthentication`, `NoStoreOAuth2AuthorizedClientRepository`.
- Việc cần theo dõi: Session persistence trên Railway.

## DEC-002 — Frontend gửi cookie bằng credentials include

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: FE và backend có thể khác origin; CORS cho phép credentials.
- Quyết định: Browser fetch/XHR tới API backend phải dùng `credentials: "include"` (hoặc client tương đương `withCredentials`).
- Lý do: Session cookie không được gửi mặc định trong cross-origin fetch.
- Hệ quả: Origin phải nằm trong `FRONTEND_ORIGINS`; wildcard bị cấm.
- Rủi ro: Browser có thể block third-party cookie trong mô hình localhost→Railway.
- Evidence: `CorsConfig#corsConfigurationSource`; runtime topology do người dùng cung cấp.
- Việc cần theo dõi: E2E trên browser thật.

## DEC-003 — Frontend không tự lưu Cognito access/refresh token

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Token-bearing OIDC authentication chỉ tồn tại trong callback processing.
- Quyết định: FE không nhận/đọc/lưu access token, ID token hoặc refresh token; không dùng localStorage cho chúng.
- Lý do: Success handler chuyển sang token-free `SagaPrincipal`; authorized client storage bị vô hiệu hóa.
- Hệ quả: API authentication dựa trên session.
- Rủi ro: FE implementation nằm ngoài repo nên tuân thủ thực tế cần kiểm tra riêng.
- Evidence: `CognitoAuthenticationSuccessHandler#replaceWithTokenFreeSessionAuthentication`, `NoStoreOAuth2AuthorizedClientRepository`.
- Việc cần theo dõi: Audit frontend khi có repository FE.

## DEC-004 — Cognito OAuth callback thuộc Backend

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Spring Security OAuth2 client xử lý authorization-code callback.
- Quyết định: Callback đăng ký là `{baseUrl}/login/oauth2/code/{registrationId}`, với registration `cognito` tạo path `/login/oauth2/code/cognito`.
- Lý do: Backend đổi code và thiết lập session.
- Hệ quả: Frontend callback chỉ là success redirect sau khi backend hoàn tất login.
- Rủi ro: Public base URL/forwarded headers phải chính xác.
- Evidence: `application.properties` OIDC registration; `SecurityConfig#securityFilterChain`.
- Việc cần theo dõi: Cognito allowed callback URL trên hạ tầng.

## DEC-005 — Login thành công redirect về Frontend

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Backend cần đưa browser trở lại UI sau provisioning.
- Quyết định: Redirect tới absolute HTTP(S) URI từ `AUTH_SUCCESS_REDIRECT_URI`.
- Lý do: Tách backend callback khỏi FE route.
- Hệ quả: Sai environment value làm startup/login fail.
- Rủi ro: Route frontend thực tế chưa nằm trong repo này.
- Evidence: `CognitoAuthenticationSuccessHandler#onAuthenticationSuccess`, `#requireHttpUri`.
- Việc cần theo dõi: Runtime fact dự kiến `http://localhost:3000/auth/callback`.

## DEC-006 — Application roles gồm ADMIN, LECTURER, STUDENT

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Cognito groups được ánh xạ thành một application role.
- Quyết định: Ba role là `ADMIN`, `LECTURER`, `STUDENT`; nếu nhiều group thì priority ADMIN→LECTURER→STUDENT.
- Lý do: Đây là enum và thứ tự resolver hiện hành.
- Hệ quả: Một principal chỉ mang một application role được chọn.
- Rủi ro: Group assignment governance trên Cognito là TBD.
- Evidence: `ApplicationRole`, `CognitoRoleResolver#resolve`.
- Việc cần theo dõi: Kiểm tra group configuration thực tế.

## DEC-007 — Team LEADER là domain role, không phải application role

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Student có thể có vai trò khác nhau theo team.
- Quyết định: `LEADER`, `MEMBER`, `MENTOR` thuộc `RoleInTeam`; LEADER có thể là điều kiện team-manager nhưng không nâng application role.
- Lý do: Tách quyền toàn hệ thống khỏi membership từng team.
- Hệ quả: Authorization cần cả principal role và TeamMember relation.
- Rủi ro: Không thấy rule riêng cho MENTOR.
- Evidence: `RoleInTeam`, `TeamMember`, `ProjectIntegrationAuthorizationService#requireTeamManager`.
- Việc cần theo dõi: Xác định quyền MENTOR nếu business cần.

## DEC-008 — CRUD authorization được xét theo từng endpoint

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Security hiện không có policy “mọi CRUD chỉ Lecturer”.
- Quyết định: Đọc annotation, URL rule và service ownership của từng route; không mặc định Lecturer-only.
- Lý do: Create master data là ADMIN; read master data chỉ authenticated; project integration dùng team-manager; import dùng course scope riêng.
- Hệ quả: Endpoint matrix là nguồn kiểm tra thay vì giả định theo HTTP verb.
- Rủi ro: Route mới dễ thiếu protection nếu chỉ dựa `anyRequest().authenticated()`.
- Evidence: `SecurityConfig#securityFilterChain`, các master-data controller, `ProjectIntegrationAuthorizationService`, `CourseController#importStudents`.
- Việc cần theo dõi: Authorization tests cho mọi mutation.

## DEC-009 — Webhook có mô hình CSRF khác browser API

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Jira/GitHub không có browser session/CSRF cookie.
- Quyết định: chỉ `POST /api/webhooks/github` và `POST /api/webhooks/jira` public ở URL security và được miễn CSRF; service xác thực provider token/JWT/signature. Không exempt wildcard `/api/webhooks/**`.
- Lý do: Provider-to-server request dùng authenticity mechanism riêng.
- Hệ quả: Không được permit webhook mà bỏ verification service.
- Rủi ro: Misconfiguration secret/public URL làm ingest fail hoặc mất an toàn.
- Evidence: `SecurityConfig#securityFilterChain`, `WebhookIngestionService`, `GitHubWebhookSignatureVerifier`, `JiraWebhookAuthenticator`.
- Việc cần theo dõi: Rotation và delivery/replay monitoring.

## DEC-010 — Local frontend và Railway backend là cross-origin

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Runtime fact do người dùng cung cấp: `http://localhost:3000` và backend Railway HTTPS.
- Quyết định: Coi topology này là cross-origin và cross-site trong kiểm thử browser.
- Lý do: Scheme/host/port khác nhau; cookie policy áp dụng.
- Hệ quả: Cần CORS credentials, explicit origin, correct SameSite/Secure và CSRF flow.
- Rủi ro: Third-party cookie blocking.
- Evidence: Runtime fact người dùng; `CorsConfig#corsConfigurationSource`, production cookie profile.
- Việc cần theo dõi: Browser E2E; runtime fact không thay thế code evidence.

## DEC-011 — Cookie production dùng Secure và SameSite phù hợp cross-site

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Prod profile có cross-site capable defaults.
- Quyết định: `SESSION_COOKIE_SECURE` default true và `SESSION_COOKIE_SAME_SITE` default none ở prod; CSRF cookie dùng cùng customizer secure/same-site.
- Lý do: Browser yêu cầu Secure cho SameSite=None.
- Hệ quả: Production phải chạy HTTPS.
- Rủi ro: Browser vẫn có thể chặn third-party cookie.
- Evidence: `application-prod.properties`; `SecurityConfig#csrfTokenRepository`.
- Việc cần theo dõi: Environment thực tế và Set-Cookie E2E.

## DEC-012 — Endpoint GET /api/auth/csrf đã được triển khai

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: FE khác domain không thể đọc backend cookie bằng `document.cookie`.
- Quyết định: Authenticated endpoint trả token/header/parameter CSRF từ Spring Security; DTO redact token trong `toString`.
- Lý do: Cho FE nhận token qua credentialed response body.
- Hệ quả: FE gửi token trong `X-XSRF-TOKEN` và không log/lưu như credential dài hạn.
- Rủi ro: Vẫn phụ thuộc session/third-party cookie.
- Evidence: `AuthController#csrf`, `CsrfTokenResponse#from`.
- Việc cần theo dõi: E2E mutation từ origin FE.

## DEC-013 — CORS hỗ trợ credential và mutation preflight

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Browser cross-origin mutation cần preflight.
- Quyết định: Explicit allowed origins; credentials true; GET/POST/PUT/PATCH/DELETE/OPTIONS; allowed request headers là `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`.
- Lý do: Hỗ trợ API session + CSRF và Jira Task/Sprint mutation bắt buộc `Idempotency-Key`; browser cross-origin phải được preflight allow header này trước khi request thật tới controller.
- Hệ quả: `FRONTEND_ORIGINS` là config bắt buộc, không wildcard.
- Rủi ro: Origin thiếu/sai scheme hoặc port sẽ bị từ chối.
- Evidence: `CorsConfig#corsConfigurationSource`, `SecurityIntegrationTest#corsAllowsTheConfiguredFrontendToSendTheCsrfHeaderWithCredentials`, preflight regression cho Jira Task/Sprint.
- Việc cần theo dõi: Đồng bộ danh sách origins theo môi trường.

## DEC-014 — Railway deploy không tự động deploy Cognito Lambda

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Railway config chỉ build/start Spring Boot jar; Lambda có package/deployment riêng.
- Quyết định: Xem Lambda account-linking là deployment độc lập với Railway.
- Lý do: Không có bước Lambda deploy trong `railway.json`.
- Hệ quả: Merge/deploy backend không cập nhật Lambda.
- Rủi ro: Backend và Lambda code/runtime có thể lệch version.
- Evidence: `railway.json`; `infra/lambda/cognito-account-linking/package.json` và README.
- Việc cần theo dõi: CI/deployment/versioning Lambda.

## DEC-015 — Source code và ba tài liệu Markdown là nguồn sự thật chính

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED
- Bối cảnh: Lịch sử chat có thể cũ hoặc thiếu context.
- Quyết định: Executable source/config là bằng chứng mạnh nhất; ba tài liệu phản ánh snapshot và decision state, phải cập nhật cùng thay đổi kiến trúc.
- Lý do: Giảm suy diễn và context drift.
- Hệ quả: Runtime facts phải được gắn nhãn; tài liệu không được ghi đè behavior trái code.
- Rủi ro: Tài liệu stale nếu không cập nhật.
- Evidence: Quy ước repository được xác lập trong task này; metadata commit ở ba tài liệu.
- Việc cần theo dõi: Review docs trong PR có thay đổi auth/API/deployment.

## DEC-016 — Hoàn thiện import Excel trước khi coi là production-ready

- Ngày: 2026-08-02
- Trạng thái: PARTIAL
- Bối cảnh: Endpoint/service import cơ bản đã được bổ sung scope authorization và integration test, nhưng validation/identity contract chưa hoàn chỉnh.
- Quyết định: Giữ implementation hiện tại ở mức PARTIAL; chưa quảng bá là hoàn chỉnh cho production.
- Lý do: Đã chặn unauthorized import và duplicate membership theo application check, nhưng vẫn còn identity conflict và input contract.
- Hệ quả: FE chưa nên tích hợp contract hiện tại như API ổn định.
- Rủi ro: Malformed spreadsheet và concurrent TeamMember duplicate vẫn chưa được harden đầy đủ; identity bind đã có contract.
- Evidence: `CourseController#importStudents`, `CourseImportAuthorizationService#requireImportAccess`, `ExcelImportService#importStudentsToCourse`, `CourseImportSecurityIntegrationTest`.
- Việc cần theo dõi: Chốt validation/error DTO, provider email và database/concurrency safeguards.

## DEC-017 — Policy phân quyền import sinh viên theo Course

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED (authorization import đã có tại checkpoint lịch sử `90b1852`; vẫn có trong HEAD hiện tại `200d866`)
- Bối cảnh: Import sinh viên là mutation có thể tạo Student, Team và TeamMember nên không đủ an toàn nếu chỉ yêu cầu authenticated session.
- Quyết định: ADMIN được import mọi Course; LECTURER chỉ import khi `SagaPrincipal.localProfileId` bằng `Course.instructor.id`; STUDENT bị từ chối. Method security chặn role tổng quát, service chịu trách nhiệm ownership và 404 Course.
- Lý do: Tái sử dụng model `SagaPrincipal`/authority session và pattern ownership hiện có; không đọc Cognito token hoặc raw group trong controller.
- Hệ quả: Browser vẫn dùng JSESSIONID + CSRF; master-data endpoints không đổi quyền. Import service chỉ chạy sau authorization.
- Rủi ro: Account status chưa được enforce toàn hệ thống; validation spreadsheet và production email provider vẫn PARTIAL.
- Evidence: `CourseController#importStudents`, `CourseImportAuthorizationService#requireImportAccess`, `CourseImportSecurityIntegrationTest`.
- Việc cần theo dõi: Chốt policy identity/concurrency; full Maven suite đã pass 186/186 sau khi test context được cách ly.

Không có secret hoặc thông tin đăng nhập thật trong decision log này.

## DEC-018 — Bind Imported Student theo cặp email và student code

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED (working tree, chưa commit)
- Quyết định: Với role STUDENT, ưu tiên `cognitoSub`; nếu không có, chỉ bind khi email verified đã normalize và student code từ rule hiện có cùng định danh một Student chưa có subject. Partial/split match, subject/profile khác, subject cũ khác, INACTIVE/SUSPENDED đều conflict 409.
- Hệ quả: Bind dùng transaction + pessimistic row lock, chỉ ghi subject và `PENDING → ACTIVE`; không đổi email/code, không đụng TeamMember/Team/Course/RoleInTeam. Phần historical no-match tạo Student mới đã được DEC-078 supersede: accepted STUDENT authentication hiện tạo `ACTIVE`.
- Evidence: `AuthenticatedProfileService`, `StudentRepository#findForIdentityBindingById`, `ImportedStudentProvisioningIntegrationTest`.

## DEC-019 — Invitation email qua transactional outbox

- Ngày: 2026-08-02
- Trạng thái: PARTIAL (working tree, chưa commit)
- Quyết định: Import tạo outbox `student_course_invitation`, dedup theo Student/Course/type, phát event AFTER_COMMIT. V6 tạo outbox/unique key; V7 thêm optimistic `Student.version` với default/backfill. Processor claim/lock record, delivery qua adapter, ghi `SENT`/`FAILED`, retry tối đa năm lần và chỉ reclaim `PROCESSING` stale theo timeout cấu hình; email failure không rollback membership.
- Hệ quả: Linked Student nhận wording sign-in; Student chưa bind nhận wording sign-in/register bằng đúng email và Google nếu deployment Cognito hỗ trợ. Login URL lấy từ `STUDENT_INVITATION_LOGIN_URL`.
- TBD: Chưa chọn/configure provider production; default adapter báo unavailable an toàn, không claim mail production hoạt động.
- Evidence: `StudentInvitationOutboxService`, `StudentInvitationProcessor`, V6 migration, invitation tests.

## DEC-020 — Swagger UI dùng CSRF interceptor cùng origin

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED (working tree, chưa commit)
- Quyết định: Swagger UI giữ `withCredentials`, bootstrap/read cookie `XSRF-TOKEN` qua cùng origin và gắn `X-XSRF-TOKEN` chỉ cho POST/PUT/PATCH/DELETE cùng origin. Không thêm Bearer scheme hay header lặp trên từng controller.
- Hệ quả: Mutation đầu tiên chờ `GET /api/auth/csrf` nếu cookie chưa có; GET/HEAD/OPTIONS không gắn header. Swagger cùng origin mới đọc được cookie; FE khác origin vẫn dùng contract JSON `/api/auth/csrf`.
- Logout: `POST /api/auth/logout` vẫn do Spring Security quản lý; CSRF hợp lệ trả 302 Cognito, thiếu/sai trả 403. Swagger fetch có thể hiện `Failed to fetch` khi theo redirect Cognito cross-origin; client browser dùng top-level form/navigation.
- Evidence: `SwaggerUiCsrfConfiguration`, `application.properties`, `OpenApiConfig`, `SwaggerUiCsrfIntegrationTest`, `SecurityIntegrationTest`.

## DEC-021 — Team roster authorization không dùng rule Project LEADER-only

- Ngày: 2026-08-02
- Trạng thái: ACCEPTED (có tại checkpoint lịch sử `90b1852`; vẫn có trong HEAD hiện tại `200d866`)
- Quyết định: `GET /api/v1/courses/{courseId}/teams/{teamId}/members` trả `Page<TeamMemberResponse>` sau khi kiểm tra Team thuộc Course URL. ADMIN xem mọi Team; Lecturer chỉ Course mình dạy; Student chỉ Team mình có TeamMember, bất kể LEADER hay MEMBER.
- Hệ quả: mismatch Course/Team hoặc Team không tồn tại là 404; anonymous 401; session hợp lệ nhưng không đủ scope 403. Response không chứa email, `cognitoSub` hay version.
- Evidence: `TeamRosterController`, `TeamRosterService`, `TeamRosterSecurityIntegrationTest`.

## DEC-022 — Railway migration fact được giữ ở mức runtime TBD

- Ngày: 2026-08-02
- Trạng thái: TBD
- Runtime fact do người dùng cung cấp: deployment Railway từng fail vì schema thiếu `student.version`.
- Quyết định: V6/V7 phải chạy trước Hibernate `ddl-auto=validate`; không ghi trạng thái production migration là CONFIRMED khi repository không chứa dashboard/log production.
- Evidence: V6/V7 source migrations và `Student.version`; production log không có trong repository.

## DEC-023 — Course roster dùng membership hiện tại, không dùng invitation outbox làm enrollment

- Ngày: 2026-08-03
- Trạng thái: PARTIAL
- Quyết định: `GET /api/v1/courses/{courseId}/students` chỉ materialize row từ `TeamMember -> Team -> Course`. `student_course_invitation` là transactional outbox/event history, không phải nguồn enrollment hiện tại.
- Hệ quả: `hasTeam` chỉ nhận `all`, `with`, `without`; do repository chưa có quan hệ Student–Course không Team, `without` hiện rỗng. Đây là giới hạn được nêu rõ, không tạo entity/migration enrollment mới.
- Query: roster whitelist `studentCode`, `fullName`, `email`, `teamName`, `projectName`; lecturer options whitelist `fullName`, `email`; direction chỉ `asc`/`desc`; invalid trả 400. Lecturer keyword không còn tìm `cognitoSub`.
- Evidence: `CourseService`, `CourseRosterAndLecturerOptionsIntegrationTest`.

## DEC-024 — Một Student tối đa một Team trong mỗi Course

- Ngày: 2026-08-03
- Trạng thái: ACCEPTED (Product Owner; implementation được đưa vào tại checkpoint lịch sử `52a8c71`, vẫn có trong HEAD hiện tại `200d866`)
- Bối cảnh lịch sử: trước quyết định này, rule nhiều Team trong một Course là TBD. Dữ liệu legacy không hợp lệ có thể vẫn tồn tại và chỉ được đọc không crash; không được xem là business contract hợp lệ.
- Quyết định: Student có thể thuộc nhiều Course, nhưng trong mỗi Course tối đa một Team. `RoleInTeam` độc lập theo Team/Course; Student có thể tham gia Project khác nhau ở Course khác. Một Course có thể có nhiều Team; mỗi Team tối đa một Project; nhiều Team/Project trong cùng Course hợp lệ khi mỗi Project thuộc Team khác.
- Write-path behavior: `ExcelImportService` là production write path duy nhất tạo TeamMember. Service lấy `PESSIMISTIC_WRITE` trên đúng Student row, sau đó query membership Student+Course. Không có membership thì tạo; đúng Team thì idempotent, không duplicate/không tự đổi role; Team khác cùng Course trả conflict 409 và không move/delete/update membership cũ; Course khác hợp lệ. Local seed phải không tạo dữ liệu trái rule.
- Concurrency/database: test dùng hai thread và hai transaction độc lập, có latch/barrier/timeout, rồi query transaction mới và xác nhận đúng một membership. Application guard là CONFIRMED cho write path tuân thủ guard; database chưa có invariant trực tiếp `UNIQUE(student_id, course_id)`, nên enforcement DB là PARTIAL.
- Email exposure: roster hiện trả email Student cho ADMIN/Lecturer owner và lecturer options trả email Lecturer cho ADMIN; actor ngoài scope bị authorization chặn, response không chứa `cognitoSub`, version, token hay credential. Business/UI justification cho hai email field vẫn TBD; quyết định này không chấp nhận policy email mới.
- Evidence: `ExcelImportService#importStudentsToCourse`, `StudentRepository#findForTeamMembershipWriteById`, `TeamMemberRepository#findByStudentIdAndTeamCourseId`, `LocalDemoDataSeeder#seed`, `CourseTeamMembershipGuardIntegrationTest`, `CourseRosterAndLecturerOptionsIntegrationTest`.

## DEC-025 — Student tự resolve Team trong Course qua endpoint self-scoped

- Ngày: 2026-08-03
- Trạng thái: ACCEPTED (Product Owner; implementation đã được commit tại `250f514`, vẫn có trong HEAD hiện tại `200d866`)
- Quyết định: thêm `GET /api/me/courses/{courseId}/team/members` cho STUDENT, dùng browser session/SagaPrincipal và không nhận `studentId` hoặc `teamId`. Backend lấy Student từ `SagaPrincipal.localProfileId`, kiểm tra Course, rồi query tất cả TeamMember theo Student+Course.
- Hệ quả: không có membership trả 404; đúng một membership trả teamId/teamName/role hiện tại, Project id/name nullable và `Page<TeamMemberResponse>`; legacy nhiều membership trả 409, không chọn Team đầu tiên hay sửa/xóa/merge dữ liệu. GET không cần CSRF. ADMIN/LECTURER 403, anonymous 401.
- Reuse: endpoint gọi logic page members dùng chung trong `TeamRosterService`; endpoint roster cũ giữ nguyên contract ADMIN/LECTURER/STUDENT exact-Team. Project authorization LEADER/MEMBER không thay đổi.
- Privacy: response không có email, `cognitoSub`, Student.version, session/CSRF/provider token hay credential. teamId được trả để FE đi tiếp flow Project/integration.
- Evidence: `MyCourseTeamController`, `TeamRosterService#getCurrentStudentTeamMembers`, `TeamMemberRepository#findByStudentIdAndTeamCourseId`, `MyCourseTeamMembersIntegrationTest`, `TeamRosterSecurityIntegrationTest`.

## DEC-026 — Privacy Policy public là HTML route độc lập với OAuth integration

- Ngày: 2026-08-03
- Trạng thái: ACCEPTED (được commit tại `07ffa38`; vẫn có trong HEAD hiện tại `200d866`)
- Quyết định: thêm đúng `GET /privacy`, public cho anonymous và mọi application role, trả HTML UTF-8 từ `static/privacy.html`. Không dùng redirect/login, wildcard matcher hay feature flag integration. `POST /privacy` không có controller mapping và không được CSRF exempt.
- Contact: policy thay `{{CONTACT_URL}}` bằng `app.privacy.contact-url` (`PRIVACY_CONTACT_URL`) sau khi validate URL absolute `http`/`https`, host không rỗng và không có userinfo. Thiếu/sai cấu hình trả lỗi controlled 503; phải cấu hình URL contact thật trước deploy. Test chỉ dùng URL example domain.
- Hệ quả: không sửa OAuth callback, scope, credential/encryption, `SagaPrincipal`, JSESSIONID, CORS, CSRF hoặc hai webhook exemptions. Policy nêu data/use/sharing/retention/choices/security/children/changes nhưng không hiển thị secret, token hay credential.
- Evidence: `PrivacyPolicyController#getPrivacyPolicy`, `static/privacy.html`, `SecurityConfig#securityFilterChain`, `PrivacyPolicyIntegrationTest`, `PrivacyPolicyControllerTest`, `SecurityIntegrationTest`, `SwaggerUiCsrfIntegrationTest`.

## DEC-030 — Timestamp vận hành của SyncJobLog là UTC, HTTP trả Instant

- Ngày: 2026-08-04; trạng thái: ACCEPTED tại HEAD `a43f05d`, vẫn có tại HEAD hiện tại.
- Quyết định: giữ entity `SyncJobLog` và cột `DATETIME(6)` là `LocalDateTime` với UTC semantics; write path job dùng `Clock.systemUTC()` và chuyển `Instant` sang UTC `LocalDateTime` có chủ đích.
- Quyết định: `SyncStatusResponse.Job` trả `Instant`; JSON có offset `Z`. FE format `Instant` theo timezone giao diện, không nối `Z` hay cộng cứng +7.
- Hệ quả: không đổi JVM/Railway timezone, `JIRA_TIME_ZONE`, entity/schema hay mọi `LocalDateTime` business khác.
- Evidence: `JiraSyncJobService`, `GitHubSyncJobService`, `SyncJobFinalizationService`, `SyncStatusResponse`, `SyncStatusResponseTest`.

## DEC-031 — Claim GitHub theo repository và finalization độc lập

- Ngày: 2026-08-04; trạng thái: ACCEPTED tại HEAD `0bc30be`.
- Quyết định: initial backfill và reconciliation claim cùng một `GitRepo` bằng database `PESSIMISTIC_WRITE`; active non-stale job coalesce, repository khác vẫn chạy song song.
- Quyết định: complete/degrade nhận id, reload row managed trong `REQUIRES_NEW`; job terminal finalize theo jobId trong `REQUIRES_NEW`, lock job và không ghi đè terminal state. Lỗi degrade không cản finalization.
- Quyết định: stale recovery xử lý chỉ GitHub `IN_PROGRESS` quá `SYNC_JOB_STALE_AFTER`, chạy theo `STALE_SYNC_JOB_RECOVERY_DELAY_MS`, không reclaim job fresh và idempotent khi lặp lại.
- Hệ quả: không migration, endpoint mới, retry toàn bộ provider sync, in-memory lock, OAuth/callback/session/JSESSIONID/CSRF/CORS/scope/webhook/encryption change. External writer production cụ thể và kết quả row cũ sau deploy là **PARTIAL/TBD**.
- Evidence: `GitHubSyncJobService`, `GitRepoStateService`, `SyncJobFinalizationService`, `SyncJobStaleRecoveryScheduler`, `GitHubSyncJobServicePersistenceTest`, `AutomaticSyncDispatcherImplTest`.

## DEC-029 — OAuth completion callbacks hand off via session-bound opaque result

- Ngày: 2026-08-04; trạng thái: ACCEPTED.
- Jira/GitHub completion callbacks keep existing callback URL, state validation, exchange, authorization and webhook behavior, but return `302` to `app.integration.callback-redirect-uri` with only a secure random opaque `resultId` query parameter.
- A safe success/failure summary is stored only in current `HttpSession`, bound to Cognito subject/local profile, TTL default `PT5M`, bounded and read-once through authenticated, CSRF-protected `POST /api/integrations/callback-results/{resultId}/consume`. Missing/replayed/invalid state remains fail-closed; consume rechecks Student or current project-manager access.
- Tokens, authorization codes, state, secrets, session ids and raw provider payload are neither persisted nor exposed in URL/API/log contract.

## DEC-027 — Jira labels là Task snapshot replace-all, không phải Label domain riêng

- Ngày: 2026-08-04
- Trạng thái: ACCEPTED (labels/components/description và Contribution được commit tại `b9968dc`; tài liệu liên quan được commit tại `200d866`)
- Quyết định: Jira search yêu cầu `labels`; provider parse `List<String>` immutable, missing/null/empty thành empty và invalid type trả provider response invalid. `Task.labels_json` là TEXT chứa JSON array, ánh xạ bằng converter defensive; V8 thêm cột nullable nên Task cũ đọc empty.
- Hệ quả tại thời điểm quyết định: Jira upsert replace toàn bộ labels mỗi snapshot,
  empty snapshot clear local và webhook chỉ trigger shared reconciliation. Claim
  “không có Task HTTP/API write” đã bị DEC-033 và trạng thái 2026-08-06 supersede;
  quyết định không tạo Label entity/bảng normalized vẫn giữ nguyên.
- Evidence: `JiraProviderClientImpl#searchIssues/#toIssue`, `JiraIssueSnapshot`, `JiraIssueUpsertService#upsert`, `Task`, `StringListJsonConverter`, `V8__add_task_jira_labels_snapshot.sql`, labels tests.
## DEC-028 — Lecturer Analytics là read-only, course-scoped và deterministic

- Ngày: 2026-08-05; trạng thái: ACCEPTED trong working tree milestone.
- API chỉ nhận resource ID, không nhận lecturer/admin actor ID; actor lấy từ session-backed
  `SagaPrincipal`. ADMIN xem mọi Course, LECTURER phải là instructor, STUDENT bị chặn.
- Không dựng AI/NLP/risk prediction. Metric thiếu dữ liệu được đặt tên theo semantic hiện có:
  `currentPlannedPoints`, aggregate Contribution hiện tại, null severity/không có heatmap level.
- Evidence: `LecturerAnalyticsController`, `LecturerAnalyticsAuthorizationService`, các query
  service và `LecturerAnalytics*Test`.
# Quyết định 2026-08-07 — P1 response/error semantics

- Quyết định: optional child chưa được tạo không tự động là 404. Chỉ endpoint Team Sprint có evidence runtime và được đổi trong milestone này sang success state `PROJECT_NOT_CREATED`.
- Quyết định: authorization Team Sprint phải chạy trước nhánh `project == null`, tránh lộ state Team cho actor không có quyền.
- Quyết định: generic/framework error được serialize an toàn theo `ApiErrorResponse`; provider/domain error từ `IntegrationException` không bị map sang code generic.

## DEC-050 — V22 chỉ repair upgrade database đã baseline

- Quyết định: thêm `V22__make_rubric_subject_nullable.sql` với đúng một thay đổi
  `rubric_template.subject_id CHAR(36) NULL`; không sửa V10/V13 có checksum runtime
  production, không seed data và không cleanup duplicate FK trong cùng migration.
- Phạm vi: **EXISTING_BASELINED_DB_UPGRADE**. Trước V22, V10/V13 đã thành công trên
  runtime được báo cáo nhưng schema chưa khớp JPA nullable.
- Hệ quả: **REPLAY_FROM_EXTERNAL_V1_BASELINE** phải có baseline legacy và decision
  riêng vì V13 chèn global `NULL`; **TRUE_EMPTY_DATABASE_BOOTSTRAP** vẫn blocked do
  V1 không nằm trong repository. Không bật `outOfOrder`, không đổi validation/ignore
  pattern để chèn migration trước V13.
- Evidence: V10, V13, V22, `RubricTemplate`, `application.properties`, runtime facts
  do người dùng cung cấp và `RubricMigrationContractTest`.
- Runtime verification 2026-08-09: V19/V20/V21/V22 đều `SUCCESS`; nullable/column
  state production khớp V19–V22 và duplicate FK không chặn V22. Không cleanup FK,
  seed rubric hoặc mở CRUD từ fact này.

## DEC-051 — Admin global rubric M4B

**Status: SUPERSEDED / ROLLED_BACK_BY_SCOPE_OWNERSHIP (2026-08-10).**

- Quyết định M4B về CRUD `/api/admin/peer-review-rubrics`, soft-delete và resolver
  active-only không còn thuộc backend ownership hiện tại; code, API, test behavior và
  tài liệu contract đã được rollback về baseline trước M4B.
- V23 đã áp dụng production nên không bị xóa, đổi, rename hoặc tái sử dụng. Cột additive
  `rubric_template.deleted_at` vẫn tồn tại nhưng code baseline không dùng nó; không tạo
  reverse migration hoặc thay đổi dữ liệu historical.

## DEC-052 — Tổng quan tiến độ Admin chỉ công bố local current counts theo Course

- Quyết định: `GET /api/admin/course-progress-overview` là endpoint GET ADMIN-only,
  phân trang/filter ngay tại DB local. Không dùng provider hay chạy contribution calculation
  theo toàn hệ thống.
- Response chỉ gồm identity/snapshot Course, lecturer summary và count Team, Student distinct,
  Project, Sprint active/non-deleted theo state Jira local, PeerReview. Không thêm grade,
  assessment finalization, completion percentage hay contribution finalized.
- Lý do: Assessment không có application lifecycle/HTTP; candidate PeerReview cho phép
  reviewer thấy các member khác nhưng không chứng minh obligation denominator hoặc completion.

## DEC-053 — Course report export là XLSX local snapshot, không phải bảng điểm

- Quyết định: dùng Apache POI `poi-ooxml` hiện hữu để tạo attachment XLSX nhiều sheet
  cho `GET /api/admin/reports/courses/{courseId}/export`; không thêm dependency hoặc
  endpoint download thứ hai.
- Phạm vi dữ liệu: Course, Team Member, Sprint/Task active canonical local và raw
  PeerReview không comment. Loại Assessment và Contribution calculation do thiếu lifecycle
  grade/finalization hoặc chi phí toàn Course.
- Privacy: không export email, Cognito subject, provider/external ID, token, secret,
  raw payload hay comment Peer Review. Filename chỉ dùng Course code đã sanitize.

## DEC-054 — Global user import chỉ pre-provision Student và Lecturer local

- Quyết định: dùng duy nhất `POST /api/admin/users/import` với multipart `role` enum
  `STUDENT|LECTURER`; workbook không mang role tự do. ADMIN import không mở khi chưa có
  governance bulk pre-provision.
- Student schema exact `studentCode,email,fullName`; Lecturer `email,fullName`. Parse,
  validate/header/formula/duplicate và preflight cross-profile hoàn tất trước mọi write;
  transaction không partial success. Invalid file 400, identity conflict 409, success là
  summary không chứa row identity.
- Reuse không merge/overwrite profile/status/Cognito subject. Student mới PENDING, Lecturer
  mới ACTIVE, subject null. Không gọi Cognito Admin API và không tạo/mutate Course, Team,
  TeamMember, invitation/outbox, membership, role hay group.

## DEC-055 — Active Semester là singleton typed explicit và delete fail-closed

- Quyết định: dùng `active_semester_setting` singleton id `1`, với `semester_id` nullable FK. V24 tạo bảng additive và seed setting unset; không dùng JSON/generic system settings, không hardcode Semester ID và không thêm field vào `semester`.
- ADMIN quản lý qua `GET`/`PUT /api/admin/settings/active-semester`; PUT chỉ nhận `semesterId`, cần browser session + CSRF. Default không được suy từ date; Semester selected phải active. GET cùng route được thêm vì FE cần đọc default filter hint, vẫn ADMIN-only.
- Retention: `SemesterService.softDeleteSemester` có guard explicit khi setting đang reference Semester, trả 409. Không clear setting âm thầm, không cascade/hard-delete và không mutate Course. Active Semester chỉ là hint; backend không áp global Course filter.

## DEC-056 — Không mở Admin notification broadcast khi notification chưa có consumer/schema contract

- Ngày: 2026-08-09; trạng thái: ACCEPTED (audit-only, BLOCKED implementation).
- Evidence: `Notification` chỉ có mapping JPA `recipientId`, `recipientRole`, `title`,
  `message`, `isRead`; không có repository, type enum, service/controller, route, producer,
  consumer hoặc test. Không có read endpoint, polling, WebSocket/SSE/email delivery. Invitation
  outbox chỉ phục vụ course invitation, không là transport notification.
- Schema: V1 là legacy baseline không có trong repository; V2–V24 không tạo/thay đổi
  `notification` và Hibernate chỉ validate. Không khẳng định được production FK, constraint,
  index, nullable hoặc giới hạn nội dung. `admin`/`lecturer`/`student` là profile tables riêng,
  không có common user table hay FK đa hình an toàn.
- Quyết định: không tạo `POST /api/admin/notifications/broadcast`, migration, generic
  `system_setting`, delivery/provider call hay fanout chỉ để insert. Contract tiếp theo phải
  chốt audience enum/role hỗ trợ, policy PENDING/INACTIVE/SUSPENDED, text bounds, read lifecycle,
  retention và audit metadata. Nếu cần state đọc theo user, đề xuất broadcast master + receipt
  per recipient trong schema versioned riêng.

## DEC-057 — Integration health Admin chỉ là snapshot local, audit theo user fail-closed

### Cập nhật A11A — 2026-08-09

- Event mới có `actorLocalProfileId` UUID-text nullable và `actorRole` nullable khi producer có
  exact local profile/role; `actorId` không đổi nghĩa Cognito subject.
- Không backfill Mongo. Vì vậy quyết định không mở user-scoped audit history vẫn giữ nguyên cho
  complete historical coverage.

- Ngày: 2026-08-09; trạng thái: ACCEPTED.
- Quyết định: thêm `GET /api/admin/integrations/health` cho ADMIN session, không CSRF.
  Contract trả enabled flag và state/count đã lưu: JiraBoard/GitRepo connection status,
  linked project, Jira webhook-id presence, GitHub installation status, webhook receipt
  status và latest persisted sync timestamp. Không gọi provider, không diễn giải thành
  provider-live health và không trả credential, secret, webhook ID, raw payload, URL hay subject.
- Quyết định: không thêm `GET /api/admin/users/{id}/audit-logs`. `SystemAuditLog.actorId`
  là Cognito subject; `localProfileId` chỉ nằm không đồng nhất trong `newValues` của một
  phần producer, nên không có mapping stable/local-profile semantics cho mọi historical log.
- Hệ quả: không triển khai impersonation, token/Bearer/JWT, role mutation, password reset
  hoặc manual Course student add/remove trong M10. Các capability này cần contract riêng.

## DEC-058 — Harden Course import theo contract workbook hiện hữu, không đổi provisioning

- Ngày: 2026-08-09; trạng thái: ACCEPTED.
- Quyết định: chỉ harden `POST /api/v1/courses/{courseId}/import-students`; giữ success text, authorization scope, browser-session/CSRF, Team/role semantics, invitation outbox và M7 global import. Không thêm preview/validate/template endpoint, migration/entity hay Cognito Admin API.
- Workbook accepted chỉ là XLSX, sheet đầu tiên; file tối đa 1 MiB, tối đa 1.000 data rows; header exact theo thứ tự `Class,RollNumber,Email,MemberCode,FullName,Group,Leader`. Formula ở mọi ô có liên quan bị reject thay vì được tính.
- Quyết định transactional: parse, duplicate và bulk identity/Team/membership preflight trước write; local partial/split identity hoặc Team khác cùng Course trả conflict. Existing Student được reuse không overwrite profile/status/subject; same Team idempotent giữ role.
- Error contract an toàn chỉ lộ category/code, không echo workbook/cell value: 400 `MALFORMED_WORKBOOK`, `FILE_TOO_LARGE`, `INVALID_HEADER`, `FORMULA_NOT_ALLOWED`, `INVALID_ROW`, `DUPLICATE_IN_FILE`, `ROW_LIMIT`; 409 `IDENTITY_CONFLICT`, `COURSE_TEAM_MEMBERSHIP_CONFLICT`.

## DEC-059 — Admin managed users và timestamp audit có timezone semantic

- Ngày: 2026-08-09; trạng thái: ACCEPTED.
- Quyết định: `GET /api/admin/users` chỉ phục vụ lifecycle `STUDENT`/`LECTURER`. SQL union không gồm bảng `admin`, vì vậy content, `totalElements` và `totalPages` đều không tính Admin. `role=ADMIN` vẫn parse theo enum hiện hữu và cho kết quả rỗng; PATCH Admin status vẫn bị từ chối.
- Quyết định: `SystemAuditLog.timestamp` dùng `Instant.now()`. Spring Data Mongo lưu `Instant` thành BSON Date epoch-milliseconds; DTO Admin trả UTC ISO-8601 có `Z`. BSON Date lịch sử được đọc theo epoch-millis, không rewrite/backfill hoặc cộng offset backend.
- Hệ quả FE: parse ISO timestamp rồi dùng `Intl.DateTimeFormat` với `Asia/Ho_Chi_Minh` (hoặc timezone người dùng đã chốt); không substring timestamp hay cộng +7 thủ công.

## DEC-061 — J1F finalization TASK_SPRINT theo target-aware canonical recovery

- Ngày: 2026-08-10; trạng thái: ACCEPTED.
- Evidence: DEMO-24 có `TASK_SPRINT=REMOTE_SUCCEEDED`, remote `10026`/`DEMO-24`, nhưng response `JIRA_WRITE_OPERATION_IN_PROGRESS`. `markRemoteSucceeded` commit operation trong transaction riêng; object operation của normal Sprint request vẫn thiếu remote id khi gọi canonical reconcile.
- Quyết định: sau remote success, đồng bộ remote identity vào object orchestration rồi chỉ GET canonical Jira issue/upsert. Target Sprint/backlog được áp trong transaction `REQUIRES_NEW`; fresh canonical read phải xác nhận association trước `complete`.
- Hệ quả: không replay POST Jira Agile move, không đổi provider endpoint/scope/idempotency state machine/global isolation. Operation chỉ lưu fingerprint, không lưu target intent; recovery nền phải giữ `TASK_SPRINT` ở `REMOTE_SUCCEEDED`, còn retry cùng key/request làm target-aware canonical recovery.

## DEC-060 — J1D confirmation Task canonical bằng transaction mới

- Ngày: 2026-08-10; trạng thái: ACCEPTED.
- Evidence production MySQL `REPEATABLE_READ` và source cho thấy outer `JiraTaskWriteService#create` đọc dữ liệu trước khi `JiraIssueUpsertService` child `REQUIRES_NEW` commit. Lookup Task tiếp theo trong outer transaction có thể không thấy row vừa commit.
- Quyết định: dùng bean `JiraCanonicalTaskReadService`, `@Transactional(propagation = REQUIRES_NEW, readOnly = true)`, sau canonical upsert cho cả create và recovery Task flow. Không self-invocation, không đổi global isolation hay clear EntityManager.
- Chỉ complete write operation sau fresh confirmation. Failure sau remote success giữ `REMOTE_SUCCEEDED`; retry cùng idempotency key chỉ canonical recovery, không remote POST mới. Không dùng sleep, polling, scheduler hay FE retry để che lỗi.
## DEC-069 — User-owned notification bell with Firebase FID realtime delivery

- **Decision:** SAGA relational DB is the source of truth for notification content and read state. Firebase Cloud Messaging is only a realtime delivery channel. New tables are `user_notification`, `firebase_installation`, and durable `notification_delivery`; the unknown legacy `notification` table is not altered or claimed.
- **Ownership:** notification and installation access is scoped only by authenticated `SagaPrincipal.localProfileId + ApplicationRole`. FID is globally unique; same-owner registration/reactivation and unregister are idempotent, while a foreign-owner FID conflicts. No request accepts an owner/profile/role override.
- **HTTP/security:** `GET /api/me/notifications`, `GET /api/me/notifications/unread-count`, `PATCH /api/me/notifications/{notificationId}/read`, `POST /api/me/firebase-installations`, and `DELETE /api/me/firebase-installations/{installationId}` use the existing Cognito OIDC session. Mutations retain global CSRF enforcement; Bearer authentication is not introduced.
- **Delivery:** Firebase Admin Java 9.10.0 sends to Firebase Installation ID through `Message.Builder#setFid`. Notification creation and one delivery row per active installation commit atomically. AFTER_COMMIT processing plus scheduled bounded retry records `PENDING/PROCESSING/SENT/FAILED`, recovers stale processing, and deactivates an installation only for `UNREGISTERED`. Provider/config failure cannot remove the DB notification or stop notification APIs from starting.
- **Credential contract:** production builds service-account credentials only in memory from the separate `FIREBASE_*` environment variables documented in the runtime constraints. `FIREBASE_PRIVATE_KEY` accepts real multiline input and normalizes literal `\\n` when present. No Base64 service-account JSON, repository credential file, temporary file, credential value logging, or hardcoded project ID is allowed. Local fallback to Application Default Credentials occurs only when `GOOGLE_APPLICATION_CREDENTIALS` is configured.
- **Producer scope:** only the already-authorized grouped Course import path emits `COURSE_MEMBERSHIP_ADDED`, and only after it actually creates a new `TeamMember`. Invitation-only, ungrouped, and idempotent existing-membership paths do not emit it. Course roster, enrollment meaning, grouping, DEC-023, Cognito provisioning, and session/CSRF semantics remain unchanged.
- **DEC-056 relationship:** superseded only for user-owned notification infrastructure. `POST /api/admin/notifications/broadcast` and broadcast audience/governance remain **BLOCKED**.
- **Verification:** Notification/Firebase targeted tests pass **16/16** with no real Firebase calls. Full suite runs **122 suites / 769 tests / 1 failure / 0 errors / 0 skipped**. The sole failure is `CourseRosterAndLecturerOptionsIntegrationTest#courseRosterHasTeamContractIsExplicitAndDoesNotTreatOutboxAsEnrollment`, classified **PREEXISTING_BASELINE_SOURCE_CONFLICT_WITH_DEC_023** and not changed/disabled. A real Railway/Firebase delivery remains **TBD_DEPLOYMENT_SMOKE**.
## DEC-070 — Manual notification broadcast and confirmed personal integration producers

- **Supersession:** DEC-056/M9 is superseded only where it blocked broadcast because notification infrastructure did not exist. DEC-069/V25 provides DB truth, FID delivery and durable delivery rows; V26 adds an idempotent broadcast master and V27 adds safe per-recipient event dedup. Historical DEC entries remain unchanged.
- **DB and delivery:** SAGA DB remains authoritative. A broadcast persists one `notification_broadcast` master and one `user_notification` per recipient, with any number of `notification_delivery` rows for active FIDs. FCM is delivery only; provider failure cannot roll back the broadcast notification or make Bell APIs unavailable.
- **Admin contract:** `POST /api/admin/notifications/broadcast` is ADMIN-only, session + CSRF, requires `Idempotency-Key`, and accepts typed `STUDENTS`, `LECTURERS`, or `ALL_USERS` plus bounded plain-text title/message. `ALL_USERS` includes Student + Lecturer only. `ALL_USERS_INCLUDES_ADMIN = TBD_PRODUCT_RULE`; no status filtering is inferred because Product did not define an AccountStatus audience policy.
- **Lecturer contract:** `POST /api/v1/courses/notifications/broadcast` is LECTURER-only, session + CSRF, requires `Idempotency-Key`, and accepts a non-empty unique-normalized set of Course IDs plus bounded plain text. Every Course must be active and assigned to the current Lecturer before any fanout. Recipient truth is distinct `TeamMember -> Team -> Course` Students only; invitation outbox is never enrollment/audience truth. ADMIN does not inherit this route.
- **Reliability:** a sender-scoped idempotency key with a content/scope fingerprint makes retries replay the same broadcast or fail conflict for changed intent. Fanout reads recipient IDs in batches of 200. The per-broadcast recipient unique constraint prevents duplicate Bell notifications; existing notification delivery logic handles one delivery per active FID and retry separately.
- **Automatic producers:** confirmed personal identity-link success emits `JIRA_LINK_SUCCEEDED` and `GITHUB_LINK_SUCCEEDED` after verified mapping persistence. Confirmed project Jira-board and GitHub-installation link success notifies the initiating actor only, after persistence/audit success, with V27 per-recipient event dedup. Failed callbacks do not notify. Task, Sprint, and deadline reminder recipients/windows are not inferred: their explicit policy remains TBD.
- **Security/content:** request bodies never accept sender/admin/lecturer/recipient IDs, FIDs, provider payloads, external action URLs, or Cognito identifiers. Responses omit recipient identity, FID and provider state. Broadcast content is text-only, bounded, and angle-bracket markup is rejected.
- **Runtime:** Firebase remains optional/fail-safe and has no live health probe. Production broadcast/FCM evidence remains **TBD_DEPLOYMENT_SMOKE**.

## DEC-071 — Jira Task/Sprint completed-write notifications

- **Status:** ACCEPTED and implemented in the current working tree, 2026-08-11.
- **Success boundary:** emit only after durable `JiraWriteOperation=COMPLETED`. `REMOTE_SUCCEEDED`, FAILED, UNKNOWN, reconciliation, background canonical sync, and webhook snapshots do not emit a SAGA user mutation success event. Same-key completed replay does not produce a second logical event; V27 recipient/event uniqueness is the final duplicate guard.
- **Task recipient:** resolve from the canonical local Task after completion. A non-null assignee is the sole recipient. Only a truly null assignee falls back to Student `TeamMember`s of the unique Team owning the Project. Never guess Team fallback when assignee resolution fails. Exclude the actor only when that actor is a Student recipient; do not accidentally exclude a same-valued UUID from the separate Admin/Lecturer profile space.
- **Sprint recipient:** all Student `TeamMember`s of the unique owning Team, excluding a Student actor. Do not auto-notify Lecturer/Admin and do not use invitation, Course roster workaround, or broadcast audience resolution. AccountStatus behavior is unchanged.
- **Event set:** Task supports Created, Updated, Assignee Changed, Sprint Changed, Estimation Changed, Status Changed, and Deleted. Sprint supports Created, Updated, Started, Closed, and Deleted. One write operation produces at most one notification per recipient/type; multiple changed fields in one Task/Sprint update remain one event.
- **Dedup:** mutation event identity is `JiraWriteOperation.id + NotificationType`; due reminders use task id + canonical due-date revision + reminder type, combined with recipient profile/role in the V27 unique constraint. Raw `Idempotency-Key` is not persisted as notification identity or logged.
- **Date model:** Jira `duedate` is date-only. HTTP create/update DTOs use `LocalDate`; the canonical Jira parser converts the provider date to start-of-day and the legacy JPA/DB representation is `LocalDateTime`. `JIRA_TIME_ZONE` is the existing calendar authority. Only Due Tomorrow, Due Today, and Overdue are implemented; no due instant, 3-hour, or 24-hour claim is permitted.
- **Eligibility/schedule:** exclude null due date, tombstones, DONE, and CANCELLED. The hourly-by-default scan is bounded/config-driven, uses injected `Clock`, survives per-Task failure, and relies on DB uniqueness for restart/concurrent safety.
- **Firebase/transactions:** SAGA Bell DB is truth; FCM remains delivery-only through existing durable `notification_delivery` and after-commit processing. Zero FIDs does not suppress Bell persistence. Producer/FCM failure cannot roll back canonical Task/Sprint or change Jira operation `COMPLETED` to failed.
- **API/security:** no public automatic-send API exists. Existing session, CSRF, authorization, and Idempotency-Key mutation contracts are unchanged. `actionUrl` is null because no canonical internal FE route is confirmed.
- **Unchanged/TBD:** DEC-023, roster, enrollment, invitation, Cognito, and account lifecycle policy remain unchanged. V25-V27, scheduler execution, Bell display, and real FID delivery/retry on Railway remain **TBD_DEPLOYMENT_SMOKE**.
## DEC-072 — Traceability Jira Task đến GitHub code là explicit local graph

- **Quyết định:** `Task <-> GitIssue`, `GitIssue <-> PullRequest` và `GitIssue <-> CommitData`
  dùng normalized many-to-many link tables từ V28. Task–Issue là relation SAGA explicit do Project
  manager link/unlink; unique pair và service check cấm cross-project. Không canonicalize bằng title,
  description, Jira-key coincidence, issue number, commit message hoặc AI/NLP.
- **Authorization:** mutation reuse exact Project Integration Manager rule; read reuse exact Project
  read rule. Browser `JSESSIONID`; unsafe method cần CSRF; không Bearer, không actor ID từ request và
  không invent `Idempotency-Key` cho local pair mutation. Duplicate link trả cùng linked state;
  repeated unlink là 204.
- **Read source:** local DB là source cho FE. Issue list/detail, Task traceability và bounded Project
  timeline không gọi provider. Timeline dùng Task/GitIssue/PR external updated timestamp và Commit
  committed timestamp; null timestamp không được đưa vào chronology, `createdAt` không đại diện
  remote-created time.
- **Provider boundary:** current GitHub provider snapshot không có authoritative PR/Commit linked or
  closing-Issue relation. Normalized seams có type `REFERENCE|CLOSING_REFERENCE|MANUAL`, nhưng sync
  không tự populate hoặc suy `#42`; trạng thái này là **PARTIAL**. Legacy nullable single FKs được giữ
  compatibility và không phải truth mới.
- **Ngoài scope:** không GitHub Issue CRUD/permission change/provider write, notification hay
  Contribution từ GitIssue. DEC-023, Course roster/invitation, session/CSRF và no-Bearer giữ nguyên.
## DEC-074 — Student Team Leader chỉ đọc Contribution của chính Team

- Ngày: 2026-08-12; trạng thái: ACCEPTED / CONFIRMED_SOURCE_TEST.
- Quyết định: reuse `GET /api/v1/teams/{teamId}/contribution-evaluation`. Controller cho
  `ADMIN|LECTURER|STUDENT` đi qua coarse gate; service lấy actor duy nhất từ
  `SagaPrincipal.localProfileId` và enforce ADMIN global, LECTURER là instructor của Course,
  hoặc STUDENT có exact `TeamMember` role `LEADER` của Team đang yêu cầu.
- `LEADER` không trở thành application role/Cognito group. MEMBER, MENTOR, Student không thuộc
  Team và Leader Team khác fail 403, kể cả cùng Course. Resource Team thiếu giữ 404.
- Privacy audit cho phép reuse DTO: chỉ có định danh học vụ tối thiểu và Contribution aggregate;
  không có email, Cognito subject, reviewer/comment, token, credential, secret hay raw provider
  payload. Không tạo response phân nhánh theo role.
- Quyền mới chỉ là read. `POST contribution-override`, Course slice-weight mutation, Peer Review
  mutation và toàn bộ công thức/normalization Contribution giữ nguyên.
- Evidence: targeted Contribution/authorization regressions 53/53 PASS. Full clean chạy
  132 suites / 831 tests / 1 failure / 0 errors / 0 skipped; failure duy nhất là baseline DEC-023
  ngoài scope và `CourseService` không có diff.
## DEC-075 — Jira Task Issue Type Update uses exact-issue editmeta and business TaskType

- Date: 2026-08-13; status: ACCEPTED / CONFIRMED_SOURCE_TEST; runtime **TBD_DEPLOYMENT_SMOKE**.
- Context: normal FE offered Bug, Feature, Request, Story and Task, but main Task Update had no `type`, so type-only requests became `JIRA_TASK_UPDATE_EMPTY`. FE does not own Jira provider IDs. Source also lacked `REQUEST` in `TaskType`, causing Jira Request to collapse to TASK and making the UI intent unrepresentable.
- Decision: add optional `type` to the existing sparse update request using the same SAGA `TaskType`, and add exact enum value `REQUEST`. Backend owns provider-ID resolution from `editmeta.fields.issuetype.allowedValues` of the exact issue; edit never uses create metadata as authority.
- Resolution: reuse the established normalize/deduplicate/exact-name-first/unique-semantic-fallback algorithm. Zero and multiple distinct provider IDs fail closed; never hardcode, cache cross-project, sort/pick-first, guess, or expose a provider ID to FE. Only after full local validation does one sparse Jira PUT contain `fields.issuetype.id` plus other actual diffs.
- No-op/hierarchy: same canonical type is suppressed and an otherwise all-no-op update retains `JIRA_TASK_UPDATE_EMPTY`. EPIC/SUBTASK hierarchy crossing fails locally with no Move Issue, parent mutation, or hierarchy workaround.
- Idempotency/recovery: raw business `type` is part of the fingerprint; resolved ID/metadata is not persisted. Remote 2xx only marks remote success. Canonical GET/upsert/fresh read must confirm the requested `TaskType` before completion; mismatch/failure remains `REMOTE_SUCCEEDED`. Because persisted fingerprints are one-way, background recovery leaves `TASK_UPDATE` pending for same-body/same-key target-aware recovery and never replays provider PUT.
- Unchanged: Assignee, Sprint, Estimation, Transition, Delete, authorization/scopes, browser session, CSRF, CORS and required Idempotency-Key. No Bearer, schema migration, sensitive logging, `CourseService` change, commit, or push.
- Evidence: targeted J1K suites pass **240/240**. Full clean ran **868 tests with 4 failures**: known DEC-023 Course roster plus unrelated stable OpenAPI count and Lecturer Analytics failures. J1K introduced no targeted failure and `CourseService` has no diff.

## DEC-076 — Persisted TaskType.REQUEST requires the physical MySQL enum

- Date: 2026-08-13; status: ACCEPTED / CONFIRMED_RUNTIME_SCHEMA_MISMATCH / IMPLEMENTED_SOURCE_TEST.
- Incident evidence: Jira search returned HTTP 200 and fetched provider issues, then canonical persistence failed at `UPSERT_ISSUES` through `JiraIssueUpsertService.upsertAttempt -> TaskRepository.saveAndFlush` with MySQL 1265 `Data truncated for column 'type'`. Read-only runtime metadata reported `task.type = enum('BUG','EPIC','FEATURE','STORY','SUBTASK','TASK')`, nullable `YES`, default `NULL`; `REQUEST` was absent.
- Correction to DEC-075: adding `TaskType.REQUEST` changed the persisted value contract. The earlier “no schema migration” statement is superseded only for this physical enum requirement; editmeta ownership, sparse Jira mutation, canonical confirmation and every other J1K boundary remain unchanged.
- Decision: V29 modifies only `task.type` to the exact current Java values `BUG, EPIC, FEATURE, REQUEST, STORY, SUBTASK, TASK`, preserving nullable/default semantics. It does not update/delete Task rows or change indexes, foreign keys, unrelated columns, Jira contracts, session/CSRF, or `CourseService`.
- Regression gate: migration SQL values must equal `TaskType.values()` as a set, every Java TaskType must round-trip through persistence, canonical Jira Request upsert remains `REQUEST`, and reconciliation with a Request issue must complete with one processed and zero failed items.
- Verification: targeted J1K.1/Jira suites pass 114/114. Full clean ran 880 tests with 4 failures and 0 errors; all four are the existing DEC-075 baseline gaps (OpenAPI operation count, Course roster, and two Lecturer Analytics assertions), while every J1K.1 and migration-index suite passes.
- Deployment: Flyway remains schema authority. Apply V29 only through the normal deployment migration mechanism; do not issue an ad-hoc production ALTER. Jira Web and manual reconciliation smoke remain required after deploy.

## DEC-077 — Authoritative Course TeamMember activates a linked PENDING Student (superseded by DEC-078)

- Date: 2026-08-14; status: **SUPERSEDED_BY_DEC_078**. This entry preserves the historical decision and its evidence; DEC-078 replaces its account-activation authority.
- Incident: register-first created a local Student with `cognitoSub` and `PENDING`. A later Course import reused the same Student and created TeamMember/invitation, but the existing-subject login branch never revisited status, so business APIs remained `403 ACCOUNT_STATUS_ACCESS_DENIED` despite legitimate enrollment.
- Decision: the successful, authorized provisioning transaction is activation authority only after exact identity validation and a current `TeamMember -> Team -> Course` membership has been created or validated. If the locked Student is `PENDING` and already has a nonblank Cognito subject, grouped Course import/manual provisioning changes it to `ACTIVE` before invitation enqueue in the same transaction. Failure later in the transaction rolls activation and membership back together.
- Ordering convergence: import-first/login-later retains DEC-018 exact email + student-code bind and `PENDING -> ACTIVE`; register-first/import-later now reaches the same Student ID, membership and ACTIVE result. Existing membership role, idempotent same-Team membership and multi-Course memberships are preserved; no new Student-Course relation exists.
- Recovery: on existing-subject Student login, the same Student row is pessimistically locked. A legacy `PENDING + cognitoSub` row becomes ACTIVE only when an inner-joined authoritative Course membership exists. Login never creates TeamMember and never uses `StudentCourseInvitation` as evidence.
- Safety: PENDING without authoritative membership stays PENDING; ACTIVE stays ACTIVE; INACTIVE/SUSPENDED never auto-reactivate. Invitation remains informational/CTA delivery and is not enrollment or activation truth. AccountStatus enforcement is not weakened, and manual Admin activation is not part of the normal provisioned Student flow.
- Unchanged: Cognito role classification/groups, Pre Sign-up/Pre Token Lambda behavior, Google IdP, browser JSESSIONID/CSRF, Course read model and `CourseService` behavior.
- Verification: targeted identity/import/account-status tests pass **64/64** across six suites, including transaction rollback and existing Course visibility. Full clean ran **138 suites / 887 tests / 4 failures / 0 errors**; all four are the previously classified DEC-023 roster, OpenAPI count and Lecturer Analytics route/role baselines. Runtime affected-row inspection and deployed browser flow remain **TBD_DEPLOYMENT_SMOKE**.

## DEC-078 — Successful STUDENT authentication activates account independently from Course membership

- Date: 2026-08-14; status: ACCEPTED / IMPLEMENTED / CONFIRMED_SOURCE_TEST; runtime **TBD_DEPLOYMENT_SMOKE**.
- Supersession: this decision supersedes DEC-077 only where DEC-077 made TeamMember/Course provisioning the account-activation authority. Existing `Student -> TeamMember -> Team -> Course` membership, invitation ordering/delivery, identity matching, role preservation and one-Team-per-Course rules remain in force.
- Account lifecycle: `PENDING` means an imported/pre-provisioned placeholder without a completed accepted authenticated identity bind. Successful accepted STUDENT authentication creates or returns `ACTIVE` regardless of Course membership. AccountStatus is not an enrollment status.
- Ordering convergence: import-first creates an unlinked PENDING Student, TeamMember and invitation; exact first login binds/activates that same Student and retains role/memberships. Login-first with no local match creates linked ACTIVE and no TeamMember; later exact import reuses the same ID, preserves ACTIVE, creates/reuses TeamMember, enqueues the invitation and exposes the Course immediately.
- Legacy recovery and safety: existing-subject `PENDING + cognitoSub` is row-locked and changes to ACTIVE on successful same-subject STUDENT authentication without a membership prerequisite. ACTIVE stays ACTIVE. INACTIVE/SUSPENDED never auto-reactivate. Partial/split, ambiguous and cross-profile identity stays a conflict; authentication never creates TeamMember.
- Membership and invitation: Course membership authority remains TeamMember; no enrollment status/entity was introduced. Invitation is informational outbox delivery, not identity, activation or enrollment truth, and no click/Admin activation is required in the normal flow.
- Unchanged: Cognito role classification/groups and Lambdas, ADMIN/LECTURER logic, browser JSESSIONID/CSRF, AccountStatus enforcement, TeamMember model, Course read model and `CourseService` behavior.
- Verification: targeted auth/OIDC/import/TeamMember/invitation/account-status tests pass **86/86 across 11 suites**. Full clean ran **138 suites / 888 tests / 5 failures / 0 errors / 0 skipped**: four stable previously classified baselines (DEC-023 roster, OpenAPI 131/133, Lecturer Analytics route and role) plus one unrelated notification newest-first assertion that passed immediate isolated rerun **1/1** and is classified non-deterministic. No lifecycle test failed. Runtime affected-row inspection and deployed browser flow remain **TBD_DEPLOYMENT_SMOKE**.

## DEC-079 — SAGA Backend is the sole authoritative context source for AI commit review

- Date: 2026-08-14; status: ACCEPTED / IMPLEMENTED / CONFIRMED_SOURCE_TEST; real SAGA context plus real-model runtime remains **TBD_NO_SAFE_FIXTURE**.
- Boundary: `saga-ai-service` obtains commit-review context only through authenticated Backend HTTP. It does not read SAGA business tables, receive GitHub/Jira credentials, or call GitHub/Jira with SAGA credentials. Backend remains the owner of Project, GitRepo, CommitData, Task/GitIssue relations, and provider access.
- Internal contract: `GET /internal/ai/v1/projects/{projectId}/github/repositories/{repositoryId}/commits/{commitSha}/review-context`, version `saga-commit-review-context-v1`. Identity is exact Project UUID + GitHub provider repository ID + full 40-hex commit SHA. A dedicated immutable DTO omits actor, email, Cognito subject, browser session, OAuth/provider token, installation ID, webhook secret, and raw authorization data.
- Authentication: the internal namespace uses environment property `app.internal-ai.service-token=${SAGA_AI_SERVICE_TOKEN:}` and header `X-SAGA-AI-Service-Token`, with fail-closed minimum configuration and constant-time comparison. It is separate from `JSESSIONID`/CSRF and grants no access to browser APIs; the browser contract and CORS allowlist are unchanged.
- Commit content: local CommitData proves membership and metadata; because it does not persist patches, the existing Backend-owned GitHub client fetches exact commit detail. Response data is sanitized and bounded to 50 changed files, 20,000 patch characters per file, and 100,000 total context characters, with explicit truncation metadata. No provider credential leaves Backend.
- Traceability: only normalized `GitIssueCommitLink` and `TaskGitIssueLink` rows are authoritative. Legacy nullable links, title/description, Jira-key, issue-number, commit-message, or AI/NLP coincidence never creates a relation. Missing explicit links return empty evidence and `NOT_PROVEN`; Issue-to-Commit population remains partial as stated by DEC-072.
- Reliability: Backend maps provider failures through the existing safe GitHub taxonomy and never exposes raw GitHub bodies. AI maps temporary timeout/connection/429/5xx context failures to bounded durable retry and maps invalid identity/auth/contract failures to controlled terminal failure. A valid durable `CONTEXT_SNAPSHOT` is reused after recovery without refetching Backend.
- Schema: no Backend business migration and no AI migration are introduced. Backend/Flyway and AI/Alembic ownership remain separate; there is no cross-domain foreign key.
- Verification: targeted Backend M5/auth/browser/OpenAPI-isolation regressions pass **47/47**. Full clean runs **909 tests / 4 failures / 0 errors / 0 skipped**; all four are the previously documented OpenAPI 131/133, DEC-023 Course roster, and two Lecturer Analytics baseline assertions. AI deterministic suite with production context adapter passes **147 tests**, with four real tests deselected by default. Real M5 execution is not claimed without an explicit safe Project/repository/SHA fixture and runtime internal-auth configuration.

## DEC-080 — Student exact-Team read access for four Team graph routes

- Date: 2026-08-14; status: ACCEPTED / IMPLEMENTED / CONFIRMED_SOURCE_TEST.
- This decision supersedes the Lecturer Analytics rule in DEC-028 only for `overview`, `heatmap`, `students/{studentId}/interactions`, and `sprints/{sprintId}/burndown`. Team graph read is no longer Lecturer-exclusive; every other Lecturer Analytics route keeps its existing authorization.
- ADMIN retains all-Team access. LECTURER retains own-Course access. STUDENT is resolved only from `SagaPrincipal.localProfileId` and must have an exact `TeamMember` for the Team in the URL. `LEADER` and `MEMBER` have equal graph-read permission; `MENTOR`, no membership, cross-Team, and cross-Course access fail closed.
- Course→Team nesting is mandatory. Interaction and optional heatmap targets must be members of the same Team, while Sprint must belong to that Team's Project. These checks preserve existing 403/404 anti-enumeration semantics.
- No application `LEADER` role is introduced, and no graph calculation, DTO, AccountStatus filter, browser session/CSRF contract, `CourseService`, or unrelated authorization is changed. Targeted regressions pass 50/50. Isolated full clean runs 936 tests with the four existing OpenAPI-count, DEC-023 roster, historical missing interaction-route, and Student-progress expectation failures; no graph milestone test fails.

## DEC-081 — Role-aware AI Agent keeps Backend as current business authority

- Date: 2026-08-14; status: ACCEPTED / IMPLEMENTED_SOURCE_TEST; deployment runtime **TBD_DEPLOYMENT_SMOKE**.
- Browser communicates only with `/api/v1/ai/**` through the existing session and CSRF contract. Backend→AI has a distinct `X-SAGA-Backend-Service-Token`; AI→Backend keeps the M5 token and adds a short-lived opaque conversation-bound actor context. Directional service credentials are not browser credentials and are not interchangeable by design.
- AI stores conversation/tool/pending-action/generated-artifact state but does not read Backend business tables or receive Jira/GitHub credentials. Backend reloads current actor/account status and applies existing domain authorization for every typed tool. Role snapshots and model claims never authorize.
- Task Create and the existing sparse Task Update are the only V1 business write capabilities. Both are two-phase proposals; the browser confirms through session + CSRF, Backend reauthorizes, and existing `JiraTaskWriteService` owns canonical provider write, recovery, notification, and idempotency behavior. No delete or account/role/Course mutation is exposed. **DEC-100 (narrow):** `TASK_CREATE` Confirm may compose existing `TASK_SPRINT` when the proposal has `sprintId`; normal Confirm claim stays one-shot; `EXECUTING` re-entry is recovery-only for that composite, not a generic repeated Confirm.
- Commit Review is reused as an asynchronous chat skill without changing its durable runner, fencing, checkpoint, provider, structured-output, or evidence contracts. SRS canonical source is AI-durable, evidence-mapped, and regenerated as DOCX in memory after current Backend authorization.
- N8N and vector DB are not introduced. Hugging Face Docker Space is the AI deployment target, with external AI DB as durable truth and no local-disk durability assumption. Actual deployment, frontend implementation, and browser smoke remain TBD.
- Verification: Backend Agent targeted tests pass **21/21**. Full clean runs **944 tests / 5 failures / 0 errors / 0 skipped**; four are the stable pre-milestone OpenAPI/DEC-023/Lecturer Analytics baselines and the previously documented notification ordering assertion passes immediate isolated rerun **1/1**. AI full deterministic suite passes **194 tests**, with four real-provider tests deselected.

## DEC-101 — AccountStatus disables the current browser session at the request boundary

- Date: 2026-08-17; status: ACCEPTED / IMPLEMENTED / CONFIRMED_SOURCE_TEST.
- This narrowly supersedes the AccountStatus enforcement part of DEC-049/M3B. For an authenticated STUDENT or LECTURER, each `/api/**` request reads the current local DB status. `INACTIVE` or `SUSPENDED` invalidates the current `HttpSession`, clears `SecurityContext`, and returns the existing `ApiErrorResponse` with `401 ACCOUNT_DISABLED` before a controller or business mutation runs. ADMIN is not status-filtered.
- `GET /api/auth/me` is also gated, so a disabled session cannot bootstrap authenticated FE state. `GET /api/auth/csrf` and `POST /api/auth/logout` remain exempt to preserve the existing CSRF/logout flow; a CSRF token does not bypass the status gate for `/me` or business APIs, and a CSRF failure is not rewritten as `ACCOUNT_DISABLED`.
- OIDC synchronization remains local-DB authority. When an existing Student or Lecturer resolves as `INACTIVE`/`SUSPENDED`, the success handler clears/invalidate any callback session and returns `401 ACCOUNT_DISABLED` without saving a SAGA authentication. PENDING Student provisioning behavior is unchanged. There is no Cognito Admin call, role mutation, or status reset on re-login.
- This is current-request-session invalidation only. The application still has no SessionRegistry/shared Spring Session infrastructure, therefore it does not claim immediate global or cross-instance revocation.
- Verification: `AdminAccountStatusIntegrationTest` and `CognitoAuthenticationSuccessHandlerTest` pass (10 tests). No migration, public operation, or public response schema changed.

## DEC-102 — Persisted graph-processing history is projection-work telemetry

- Date: 2026-08-17; status: ACCEPTED / IMPLEMENTED / CONFIRMED_SOURCE_TEST.
- `graph_processing_run` records one immutable run after each successfully built `CONTRIBUTION` or `INTERACTION` response. A run contains UTC `occurred_at`, graph kind, optional Course/Team/Student snapshot IDs, and the exact response `nodesBuilt` / `edgesBuilt` counts. It stores neither graph payload nor personal data/secrets and intentionally has no foreign keys, so source-row cleanup cannot block telemetry retention.
- Recording uses the injected `Clock` and an independent transaction. Telemetry persistence is fail-open: a sanitized warning is logged, while an already-built graph response remains successful. No scheduler, backfill, seed, provider call, AI behavior, contribution formula, or interaction-edge semantic changed.
- `GET /api/admin/reports/graph-processing` remains ADMIN session-only GET/no-CSRF/no-Bearer. It now returns `historySupported=true`, `coverageStart`, and only persisted daily buckets in the rolling seven local calendar days of `Asia/Ho_Chi_Minh`; it aggregates both kinds by `nodesBuilt`, `edgesBuilt`, and `runCount`. It never fabricates pre-cutover or zero points.
- Schema migration is `V44__add_graph_processing_run.sql`; it adds UTC timestamp and `(occurred_at)`, `(graph_kind, occurred_at)` indexes only. The public response schema replaces the obsolete created/updated counters with build/run counters.
