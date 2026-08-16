# Demo Data Reset — Phase 1 Audit (Read-Only)

Status: **AUDIT ONLY. NOT EXECUTED. NOT APPROVED FOR EXECUTION.**
Generated: 2026-08-17, against `main` at commit `81b5dc78aa5a87dc918f97af5e10124ae1f4c7a3` (working tree had unrelated uncommitted changes at audit time — none touched by this audit).

This document is the Phase 1 deliverable requested for a "safe demo data reset" before a
school defense/presentation. It contains: source-of-truth findings, the full FK dependency
graph for the business-demo cluster, a proposed (not executed) delete order, protected-data
rules, and open questions that require Product sign-off before any Phase 2 execution.

No DELETE / TRUNCATE / DROP / UPDATE statement in this document (or the companion
`demo_reset_preflight_selects.sql`) has been run against any database. `demo_reset_preflight_selects.sql`
contains **only** `SELECT COUNT(*)` statements — safe to run against production/demo as-is.

---

## 1. Source-of-truth findings

### 1.1 No existing "demo reset" procedure
None of the five required docs (`SAGA_SYSTEM_CONTEXT_FOR_AI.md`, `SAGA_CURRENT_STATE.md`,
`SAGA_DECISION_LOG.md`, `SAGA_BACKEND_REQUIREMENTS_DEPENDENCIES_CONSTRAINTS.md`,
`STUDENT_IMPORT_INVITATION_AND_PROVISIONING.md`) document a bulk data-reset or
environment-wipe procedure. Every decision entry about `DELETE` (DEC-032, DEC-035, DEC-047,
DEC-048, DEC-055, DEC-074) describes **soft-delete with a dependency guard returning 409**,
never cascade, never hard-delete, and always scoped to a single entity's own app endpoint —
not a bulk reset. This is new, unreviewed ground; there is no prior art to defer to.

### 1.2 No "DEMO" data-tagging convention exists
No doc defines a naming convention (Course code prefix, Semester name, Project name, Jira
project key, createdAt window, seed marker) for tagging demo rows. The string "DEMO" that
appears in `SAGA_SYSTEM_CONTEXT_FOR_AI.md` is a **real Jira issue/project key** (`DEMO-8`,
`DEMO-9`, `DEMO-24`) observed during Jira-integration verification against a live dev Jira
project — it is not a data-classification rule. **Do not infer a "DEMO" prefix as sufficient
evidence for deletion scope** (per standing guardrail: never invent a classification marker
without a grounded source). The only documented demo-environment toggle is
`LOCAL_DEMO_SEED_ENABLED` / `LocalDemoDataSeeder` (`application-local.properties`), which is a
local-dev **seeding** mechanism (creates data), with no wipe counterpart.

**Consequence**: Product must supply an explicit allowlist/denylist of Course IDs and/or
Semester IDs to reset. There is no schema-provable way to auto-detect "demo" rows.

### 1.3 Student / TeamMember / StudentCourseInvitation — confirmed separation
Grounded in `STUDENT_IMPORT_INVITATION_AND_PROVISIONING.md` and verified directly against
`ExcelImportService.java`, `CourseStudentManagementService.java`,
`CognitoAuthenticationSuccessHandler.java`:

- **Student is a global, reusable profile.** One Student row can belong to multiple Courses
  over time (`Student → TeamMember → Team → Course` is the *only* membership authority).
  A Course import creates a `PENDING` Student with no `cognitoSub` if no local match exists;
  first login (via any Course, or independently) binds `cognitoSub` and flips
  `PENDING → ACTIVE` in place, preserving the Student ID and all existing memberships.
  A second, Course-agnostic provisioning path also exists (`POST /api/admin/users/import`,
  M7 global admin import) that creates `PENDING` Students with **no Course/Team link at all**.
- **`StudentCourseInvitation` is delivery/history only** — "Invitation is informational
  delivery/history, not authentication, activation or enrollment truth." The app's own
  `CourseStudentManagementService.removeStudentFromCourse()` treats it exactly this way: it
  deletes `TeamMember` + `StudentCourseInvitation` rows for the course and leaves `Student`
  untouched — this is the reference pattern this audit follows.
- **Therefore**: `AccountStatus.PENDING` and `cognito_sub IS NULL` are **global** states, not
  evidence that a Student belongs only to the demo Course being cleared. Confirmed unsafe to
  use as sole deletion evidence, exactly as the task brief required.
- A known, acknowledged bug (`STUDENT_IMPORT_INVITATION_AND_PROVISIONING.md` L131, referencing
  DEC-023): `CourseService#getCourseRoster` still partially reads the invitation outbox.
  Clearing `student_course_invitation` rows for a demo Course could affect that roster view —
  flagged for Product awareness, not a blocker.

### 1.4 `active_semester_setting` — singleton, nullable FK, no cascade
`V24__add_active_semester_setting.sql` (full DDL):
```sql
CREATE TABLE active_semester_setting (
    singleton_id TINYINT NOT NULL,
    semester_id CHAR(36) NULL,
    CONSTRAINT pk_active_semester_setting PRIMARY KEY (singleton_id),
    CONSTRAINT ck_active_semester_setting_singleton CHECK (singleton_id = 1),
    CONSTRAINT fk_active_semester_setting_semester
        FOREIGN KEY (semester_id) REFERENCES semester(id)
);
INSERT INTO active_semester_setting (singleton_id, semester_id) VALUES (1, NULL);
```
Singleton is enforced by a `CHECK (singleton_id = 1)` on the primary key, not a business-key
unique index. `semester_id` is nullable and seeded NULL by default. The FK has **no
`ON DELETE`** clause (MySQL default `RESTRICT`) — deleting a referenced `Semester` while this
row still points at it will be rejected by MySQL. DEC-055 confirms the app-level guard already
follows this rule ("Không clear setting âm thầm, không cascade/hard-delete"): **the row itself
must never be deleted; only `semester_id` may be set to NULL, and only if it currently
references a Semester slated for cleanup.**

### 1.5 AI service — confirmed fully separate database
`SAGA_DECISION_LOG.md` (DEC-079 and the M5/V43 entries) states explicitly: *"Backend/Flyway and
AI/Alembic ownership remain separate; there is no cross-domain foreign key."* AI's own
`agent_conversation` table lives under its own Alembic migration (`20260819_0007`) in a
separately deployed database ("AI DB", referenced only via `SAGA_AI_AGENT_BASE_URL`, a Hugging
Face Space origin) — not in this repo's MySQL instance. A full-text search of all 41 Flyway
migrations and the entire entity package for `alembic_version`, `ai_job`, `agent_conversation`,
`agent_message`, `pending_action`, `ai_review` found **zero matches**. The only AI-adjacent
tables owned by *this* backend are `ai_agent_delegation_context` (V30, short-lived delegation
tokens) and `ai_agent_conversation_scope` (V43, course-scope binding for a conversation ID) —
both are Backend-owned operational/bookkeeping tables, not AI's conversation content.

**Conclusion: `AI_SERVICE_OWNED_TABLES` = none in this database.** No AI table needs
protecting here, and no AI table can be reset here — if Product wants AI demo conversation
state cleared, that is a separate Phase 2b to be scoped and executed against `saga-ai-service`'s
own database, by that service's own owners.

### 1.6 Personal Jira/GitHub identity + encrypted credentials — embedded in project-scoped rows (open issue)
`jira_board` (unique on `project_id`) stores `encrypted_access_token`,
`encrypted_refresh_token`, `webhook_secret_hash`, **and** `connected_by_student_id` /
`connected_by_cognito_sub` directly on the same row as the project's Jira connection.
`github_installation` stores `installed_by_student_id` / `installed_by_cognito_sub` alongside
the GitHub App installation record. The separate `identity_map` / `identity_mapping_history`
tables (the actual "personal identity mapping" entities named in the task brief) are
**student-level and provider-level only** — they do not hold encrypted tokens and are not
project-scoped, so they are cleanly protected/KEEP.

`jira_board` and `github_installation`, however, sit at the intersection of "demo Project
connector data" (in scope for cleanup) and "personal identity + encrypted credential" (protected
by the task brief). See §5 Open Issues — this needs an explicit Product decision, not an
inferred one.

### 1.7 Stale/contradictory doc sections (flagged, not resolved)
- `SAGA_BACKEND_REQUIREMENTS_DEPENDENCIES_CONSTRAINTS.md` lines 545/732 claim hard-vs-soft
  delete behavior for non-integration entities is "TBD" / unproven. This directly contradicts
  DEC-032/035/047/048 and the same document's own line 236, all of which confirm soft-delete
  with a dependency guard for Course/Semester/Subject/Class/Task/Sprint. Treated as a stale,
  unsynced section; the decision-log entries are authoritative.
- The same document's lines 326/710 describe `AccountStatus` as unenforced pre-API-call. This
  is superseded by DEC-101 (dated 2026-08-17, the newest decision in the log), which added
  session invalidation for `INACTIVE`/`SUSPENDED` accounts. Source code
  (`AccountStatusEnforcementFilter.java`, currently modified in the working tree per `git status`)
  is consistent with DEC-101, not with the stale doc section.

---

## 2. Schema source

- **MySQL** (JPA/Hibernate entities, `ddl-auto=validate`; Flyway is schema authority when
  enabled — `spring.flyway.enabled` defaults to `false` per `application.properties`).
  41 tracked migrations `V2`–`V43` (no `V1` file — reserved for the pre-Flyway legacy baseline
  per `V2` header comment; no `V14` file — unexplained gap, not investigated further, low risk).
  Core tables (`student`, `project`, `course`, `semester`, `class`, `subject`, `team`, `task`,
  `sprint`, `jira_board`, `git_repo`, `identity_map`, etc.) predate Flyway entirely — their
  `CREATE TABLE` DDL is **not in this repo**; only `ALTER TABLE` changes are tracked from V2
  onward. This audit relied on JPA entity mappings (59 `@Entity` classes) as the authority for
  those tables' current shape, cross-checked against every `ALTER`/`FOREIGN KEY` found in the
  tracked migrations.
- **MongoDB**: `system_audit_log` collection only, owned by this same backend (not by AI). KEEP
  by default per task brief.
- **Flyway bookkeeping table**: `flyway_schema_history` (default name; no override found in
  `application*.properties`). KEEP — never touch.

---

## 3. Protected data (default KEEP — no Product override obtained)

| Category | Tables |
|---|---|
| Account/Identity | `student`, `lecturer`, `admin` |
| Personal identity mapping | `identity_map`, `identity_mapping_history` |
| Migration/infra bookkeeping | `flyway_schema_history`; (AI's `alembic_version` is not in this DB at all) |
| Master/global data | `subject`, `class`, `project_type`, `rubric_template`, `peer_review_config`, `cam_config` |
| Audit | `system_audit_log` (MongoDB) |
| Notification/device state | `user_notification`, `notification_delivery`, `notification_broadcast`, `firebase_installation` — no schema-provable link to a specific Course/Project (recipient/owner fields are loose UUIDs, not FKs); KEEP unless Product supplies a verified join path |
| Operational/sync state | `sync_job_log`, `webhook_receipt`, `business_warning` — same loose-UUID issue; KEEP by default |
| Ephemeral AI-delegation bookkeeping (Backend-owned, not AI's data) | `ai_agent_delegation_context`, `ai_agent_conversation_scope` — low-risk either way (short-lived/expiring), default KEEP pending Product input |
| Policy/assessment | `assessment`, `assessment_evidence`, `policy_override_request` — no direct Course/Project FK found (scoped by `student`/`lecturer`/`class` only); KEEP, scope unproven |
| Active semester singleton | `active_semester_setting` **row** is always KEEP; only its `semester_id` value may be nulled |

**Explicit non-negotiable rule carried over from the task brief**: `AccountStatus.PENDING` and
`cognito_sub IS NULL` must never be used, alone or combined, as evidence to delete a `student`
row. Confirmed correct in §1.3 above — this is a global activation state, not course-scoped.

---

## 4. FK dependency graph — business-demo cluster

Built from all 59 `@Entity` classes plus every `FOREIGN KEY`/`ON DELETE` clause in the 41
tracked Flyway migrations. Full per-entity detail available in this audit's working notes;
summarized graph below (arrow = "child references parent"; child must be deleted, or the FK
value nulled, before the parent):

```
task_git_issue_link ────────────▶ task, git_issue
git_issue_commit_link ──────────▶ git_issue, commit_data
git_issue_pull_request_link ────▶ git_issue, pull_request
commit_file ─────────────────────▶ commit_data, file_module
commit_review_result ────────────▶ commit_review_intent, git_repo, commit_data
commit_review_intent ────────────▶ git_repo, commit_data
task_attachment, task_web_link ─▶ task                         (only ON DELETE CASCADE pairs, besides peer_review_detail)
peer_review_detail ──────────────▶ peer_review                 (ON DELETE CASCADE)
meeting_attendee ─────────────────▶ meeting_log, student(KEEP)
comment ──────────────────────────▶ task, git_issue, pull_request, comment(self)
pr_review ─────────────────────────▶ pull_request, student(KEEP)
pull_request ──────────────────────▶ git_repo, task, git_issue, student(KEEP)
commit_data ────────────────────────▶ git_repo, task, git_issue, pull_request, student(KEEP)
git_issue ───────────────────────────▶ git_repo, student(KEEP) x2
file_module ──────────────────────────▶ git_repo
git_repo ──────────────────────────────▶ project, github_installation
task_weight_config ─────────────────────▶ course, subject(KEEP)
peer_review ──────────────────────────────▶ sprint, student(KEEP) x2
document ───────────────────────────────────▶ project, student(KEEP)
meeting_log ─────────────────────────────────▶ team
ai_interaction_log, risk_alert ──────────────▶ project, student(KEEP)
jira_write_operation ─────────────────────────▶ project              (NOT NULL FK)
task ───────────────────────────────────────────▶ project, sprint, student(KEEP) x2, task(self: blocks_task_id)
sprint ────────────────────────────────────────────▶ jira_board
jira_board ─────────────────────────────────────────▶ project, student(KEEP)   [+ embedded encrypted credential, see §1.6]
github_installation ───────────────────────────────────▶ student(KEEP)         [+ embedded credential/identity, see §1.6]
project_group_weight_config ────────────────────────────▶ project, team        (both NOT NULL)
team_member ──────────────────────────────────────────────▶ team, student(KEEP)
student_course_invitation ──────────────────────────────────▶ student(KEEP), course   (both NOT NULL)
team ───────────────────────────────────────────────────────────▶ course, project (1:1, unique)
project ─────────────────────────────────────────────────────────▶ course, project_type(KEEP)
course ───────────────────────────────────────────────────────────▶ subject(KEEP), class(KEEP), semester, lecturer(KEEP)
semester ─────────────────────────────────────────────────────────────▶ (referenced by active_semester_setting — must null first)
```

**FK enforcement fact**: across all 41 migrations, exactly **three** `ON DELETE CASCADE`
constraints exist in the whole schema (`peer_review_detail→peer_review`,
`task_attachment→task`, `task_web_link→task`). Every other FK is MySQL default
`RESTRICT`/`NO ACTION`. A reset script **must** delete every table below explicitly, in
dependency order — nothing cascades automatically, and MySQL will reject an out-of-order
DELETE with a constraint violation (a safety net, not something to route around with
`FOREIGN_KEY_CHECKS=0`).

---

## 5. Proposed delete order (topologically verified — NOT EXECUTED)

Verified by hand-tracing every remaining reference after each round is "removed," so this is a
true dependency order, not just an entity-list guess. Numbers 1–24 have no ordering ambiguity.
Steps 29 and 35 carry explicit preconditions (see notes).

| # | Table | Scope filter | Note |
|---|---|---|---|
| 1 | `task_git_issue_link` | via `task.project_id` in demo scope | |
| 2 | `git_issue_commit_link` | via `git_issue.repo_id → git_repo.project_id` in scope | |
| 3 | `git_issue_pull_request_link` | via `git_issue`/`pull_request` in scope | |
| 4 | `commit_file` | via `commit_data` in scope | |
| 5 | `commit_review_result` | via `commit_review_intent`/`git_repo` in scope | |
| 6 | `task_attachment` | via `task` in scope | (also cascades automatically from `task` delete) |
| 7 | `task_web_link` | via `task` in scope | (also cascades automatically from `task` delete) |
| 8 | `peer_review_detail` | via `peer_review` in scope | (also cascades automatically from `peer_review` delete) |
| 9 | `meeting_attendee` | via `meeting_log.team_id` in scope | |
| 10 | `pr_review` | via `pull_request` in scope | |
| 11 | `ai_interaction_log` | via `project_id` in scope | |
| 12 | `risk_alert` | via `project_id` in scope | |
| 13 | `jira_write_operation` | via `project_id` in scope | |
| 14 | `task_weight_config` | via `course_id` in scope | this is the exact table DEC-202/DEC-048's app-level Course-delete guard checks — Phase 2 bypasses the app layer entirely via direct SQL, so this guard does not apply, but the FK still must be cleared first |
| 15 | `document` | via `project_id` in scope | |
| 16 | `project_group_weight_config` | via `project_id`/`team_id` in scope | |
| 17 | `team_member` | via `team.course_id` in scope | |
| 18 | `student_course_invitation` | via `course_id` in scope | |
| 19 | `comment` | via `task`/`git_issue`/`pull_request` in scope | self-referential (`parent_comment_id`) — null it out for in-scope rows before deleting, or delete leaf replies before root comments |
| 20 | `file_module` | via `repo_id → git_repo.project_id` in scope | |
| 21 | `meeting_log` | via `team_id` in scope | |
| 22 | `peer_review` | via `sprint_id → jira_board.project_id` in scope | |
| 23 | `commit_review_intent` | via `git_repo`/`commit_data` in scope | |
| 24 | `commit_data` | via `repo_id`/`task_id` in scope | |
| 25 | `team` | via `course_id` in scope | |
| 26 | `pull_request` | via `repo_id`/`task_id` in scope | |
| 27 | `git_issue` | via `repo_id` in scope | |
| 28 | `git_repo` | via `project_id` in scope | |
| 29 | `github_installation` | via installations only used by in-scope `git_repo` rows | **precondition**: verify (preflight query) that the installation is not also linked to any `git_repo` outside the demo scope — a GitHub App installation can cover multiple repos across multiple projects; do not delete if shared |
| 30 | `task` | via `project_id` in scope | **precondition**: `UPDATE task SET blocks_task_id = NULL WHERE project_id IN (...)` first, to break the self-referential FK before deleting |
| 31 | `sprint` | via `board_id → jira_board.project_id` in scope | |
| 32 | `jira_board` | via `project_id` in scope | **see §6 Open Issue — embeds encrypted credential + personal identity** |
| 33 | `project` | via `course_id` in scope | |
| 34 | `course` | explicit Course-ID allowlist from Product | |
| 35 | `semester` | explicit Semester-ID allowlist from Product | **precondition**: `UPDATE active_semester_setting SET semester_id = NULL WHERE semester_id IN (...)` first; never delete the `active_semester_setting` row itself |

Everything in §3 (Protected data) is excluded from this order entirely.

---

## 6. Open issues requiring Product decision before Phase 2

1. **`jira_board` / `github_installation` credential-vs-demo-data conflict** (§1.6). Product
   must choose one of: (a) hard-delete the row as part of demo Project cleanup — acceptable
   since it destroys only a *connector* record, not the Student's identity or the `identity_map`
   row; (b) redact just the encrypted-token/webhook columns and retain the row for audit trail;
   (c) treat as fully protected and exclude Project's Jira/GitHub connector state from this
   reset. No source evidence favors one option over another — this is a product call.
2. **`github_installation` sharing risk** (step 29). Must be verified per-installation before
   deletion; a shared installation across demo and non-demo projects must not be deleted.
3. **Demo scope definition**. No schema-provable "demo" marker exists (§1.2). Needs an explicit
   Course-ID and/or Semester-ID list from Product — either an allowlist of what to keep or a
   denylist of what to clear. Until supplied, this plan cannot be scoped to run against a real
   database.
4. **Notification/operational tables with loose UUID references** (`user_notification`,
   `notification_delivery`, `notification_broadcast`, `firebase_installation`, `business_warning`,
   `sync_job_log`, `webhook_receipt`, `ai_agent_conversation_scope`,
   `ai_agent_delegation_context`). None have enforced FKs to Course/Project, so scope cannot be
   proven from schema alone. Default KEEP per task brief; flag if Product wants a follow-up
   audit joining these by `recipient_profile_id`/`owner_profile_id`/`course_id` values at the
   data layer (which was out of scope for this schema-level Phase 1 audit).
5. **`assessment` / `assessment_evidence` / `policy_override_request` / `cam_config`**. These
   reference `student`/`lecturer`/`class`/`subject`/`admin` but never `course`/`project`
   directly. Unclear whether they are "demo business data" at all under this task's definition,
   or entirely separate policy/master features. Recommend excluding from Phase 2 pending
   explicit Product confirmation.
6. **`V14` migration gap**. No file exists for `V14` in the tracked migration sequence; no
   explanation found in docs. Purely informational — does not block this plan, flagged for
   awareness only.
7. **`ai_job_id` columns on `commit_review_intent`/`commit_review_result`**. These are plain
   string/UUID columns referencing state in the separate AI service DB (no real FK, per §1.5).
   Deleting the Backend-side rows does not delete anything in the AI service; if Product wants
   AI-side commit-review artifacts cleared too, that is Phase 2b (separate service, separate
   owners) as noted in §1.5.

---

## 7. Safety invariants Phase 2 must prove (from task brief §11, restated for this schema)

- `SELECT COUNT(*) FROM admin` unchanged.
- `SELECT COUNT(*) FROM lecturer` unchanged.
- `SELECT COUNT(*) FROM student` unchanged.
- The **set** of `student.id` values with non-null `cognito_sub` unchanged (authenticated
  identities untouched).
- `SELECT COUNT(*) FROM identity_map` and `identity_mapping_history` unchanged.
- `flyway_schema_history` row count and checksums unchanged (`SCHEMA_UNCHANGED = YES`).
- `subject`, `class`, `project_type` row counts unchanged unless Product separately approves
  master-data changes.
- `active_semester_setting` still has exactly 1 row (`singleton_id = 1`); `semester_id` is
  either unchanged or explicitly nulled per Product's Semester-cleanup approval — never
  silently cleared as a side effect.
- No Jira/GitHub/Cognito provider API call made (`NO_PROVIDER_DELETE_CALL = YES` — this audit
  made none; Phase 2 execution design must preserve that).

See `docs/ops/demo_reset_preflight_selects.sql` for the runnable read-only queries Product
should execute now, before any Phase 2 approval, to get the actual BEFORE counts.
