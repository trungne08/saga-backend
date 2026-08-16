## Extra Master (DEC-097) — 2026-08-16

OpenAPI **152**. Migration head **V42**. Public AI routes unchanged.

- Manual GitIssue↔Commit (Project Integration Manager, CSRF):
  - `POST /api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}`
  - `DELETE /api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}` → 204
- Artifact download still `GET /api/v1/ai/artifacts/{artifactId}/download`. Additional type `LEADER_TEAM_PROGRESS_REPORT` + scope `TEAM` reauthorizes current STUDENT who is exact `roleInTeam=LEADER` of that Team.
- Do not display fabricated warnings. Confirmed vs advisory vs unsupported remain three Backend categories.

## Capability matrix + warning-in-report + fail-closed AI (DEC-096) — 2026-08-16


OpenAPI **149** (unchanged). Migration head **V41**. Public AI routes unchanged.

- `/api/v1/ai/**` unauthenticated → 401 `Phiên đăng nhập đã hết hạn.` Forbidden AI tool/artifact → 403 `Bạn không có quyền truy cập hoặc thực hiện thao tác này.`
- Artifact download still `GET /api/v1/ai/artifacts/{artifactId}/download`. `LECTURER_PROGRESS_REPORT` + `COURSE` reauthorizes via existing Course analytics access (LECTURER instructor or ADMIN). `ADMIN_SYSTEM_REPORT` + `SYSTEM` remains ADMIN-only. `SRS_DOCX` + `PROJECT` unchanged (any authorized project reader, including Student team members).
- Do not display fabricated warnings. Supported report signals: `TASK_DUE_TOMORROW`, `TASK_DUE_TODAY`, `TASK_OVERDUE`, `OVERDUE_TASK`. Auto-review result warnings are **not** a frontend contract yet.

## Role-aware AI chat + report artifacts (DEC-095) — 2026-08-16

OpenAPI **149** (unchanged). Migration head **V41**. Public AI routes unchanged.

- Create/send still `{ title? }` / `{ content }`. Unknown identity fields (`actorId`, `applicationRole`, `studentId`, `lecturerId`) → **400**. Current actor comes from session. Do not prompt for name/MSSV to identify the logged-in user.
- Starter prompts may be role-aware on FE (`/api/auth/me.applicationRole` + Team role from existing membership APIs), but answers must come from chat/tools, not hardcoded FE copy.
- Artifact download: existing `GET /api/v1/ai/artifacts/{artifactId}/download`. SRS export unchanged. Lecturer Course progress report and Admin system report are additional `generatedArtifact` types served through the same route after Backend reauthorization. Cross-Course / cross-role download is forbidden.
- Internal AI tools and Backend→AI commit-review routes are **not** a frontend contract.
## Contribution flowchart graph (DEC-096) — 2026-08-16

OpenAPI **150**. Query `sprintId` additive (2026-08-16); OpenAPI still **152**. No new operation.

- `GET /api/v1/teams/{teamId}/contribution-graph` — flowchart nodes/edges. Same auth as evaluation (LECTURER exact Course / STUDENT exact Team LEADER). ADMIN 403. Session cookie, no CSRF, no Bearer.
- Optional `?sprintId=` filters the flowchart to that Sprint on the Team Project (slice, `P_s`, %, edges). Omit for the whole Project. Unknown/other-project Sprint → **404**.
- Formula stays SAGA (DEC-092). Do not apply mockup CODE×2.0 / DESIGN / DOCS. `peerCoefficient` is team-star share. `tasks[]` on each edge is drill-down only.
- Radar / member comparison / sprint line still use `GET .../contribution-evaluation` (DEC-094). Do not call `/api/analytics/*`.
- Chi tiết chọn API: `docs/CONTRIBUTION_EVALUATION_VS_GRAPH_API.md`.

## Jira task attachments from SAGA (DEC-093) — 2026-08-15

OpenAPI **149**. Migration head **V39**.

- `POST /api/v1/projects/{projectId}/tasks/{taskId}/attachments` multipart `files` and/or `link`. **STUDENT team member only.** CSRF + `Idempotency-Key`. Response `{taskId, attachments[], links[]}`. File metadata only; links stored in `task_web_link`.

## Absolute weighted slice × peer (DEC-092) — 2026-08-15

OpenAPI **148** (unchanged). No migration.

- `GET /api/v1/teams/{teamId}/contribution-evaluation`: `sliceScore` / `sliceContributionPercentage` = slice **trước** peer (`slice / Σ slice × 100`). `finalContributionPercentage` = `(Σ slice × project P) / team adjust`. Per-sprint `%` after peer stays `sprintBreakdowns[].contributionPercentage`; pre-peer is `sliceContributionPercentage` on the same row. `P = 1` if none yet. Tasks with no sprint do not score. `peerReviewScore` is still project-level peer (0..1); the final % already applied it — do not multiply again on FE.
- **DEC-094 graph fields:** `sprintBreakdowns[]` also has `codeStoryPoints`, `testStoryPoints`, `documentStoryPoints`, `researchStoryPoints` (recognized SP in that sprint). Use evaluation for radar / member comparison / sprint line / stacked criterion bars. Do not call `/api/analytics/*`.

## Sprint-first contribution % (DEC-091) — 2026-08-15

**SUPERSEDED by DEC-092.**

## Labels-only Task scoring + Jira attachment metadata (DEC-090) — 2026-08-15

OpenAPI **148** (unchanged). Migration head **V37 → V38**.

- Task criterion = **labels only**. Exact reserved markers: `saga:code`, `saga:test`, `saga:document`, `saga:research`. No keyword/title/type fallback — unlabeled or conflicting markers score into **no** criterion (sprint/task numeric score still counts those DONE tasks).
- **DOCUMENT / RESEARCH:** story points count **only if** that task has at least one Jira file attachment **or** one submitted web link. Evidence count does not add extra points. **CODE / TEST:** story points only.
- Jira attachment **metadata** is ingested on issue upsert and after `POST .../tasks/{taskId}/attachments`. No file download, no content URL. GitHub attachments remain unimplemented.
- FE can keep using the existing Jira label editing flow. Do not build an attachment gallery against SAGA.

## Contribution weight: Course-default + optional exclusive Team override (DEC-088, supersedes DEC-087) — 2026-08-15

OpenAPI **148 (DEC-087) → 151** (revived `PUT .../group-weights`, new `PUT .../contribution-config-mode`, new `GET .../contribution-team-weights`). Migration head **V35 → V36**. Current generated count is **148** after removing the 3 legacy Course slice-weight request/list/decision APIs.

- Each Course has exactly one active Contribution config mode — `Course.contributionConfigMode` = `COURSE` or `TEAM`. **There is no "Team override if present, else Course" hybrid** — a Course is entirely in one mode. Do not build a per-Team "override toggle" UI that silently falls back; if a Course is in TEAM mode and a Team has no override, that Team's Contribution is **not computable** (Backend returns `TEAM_WEIGHT_CONFIG_INCOMPLETE`), not "using Course weights."
- Criteria universe is now **`CODE/TEST/DOCUMENT/RESEARCH`** — `DESIGN` is retired as a Contribution criterion (it still exists as a ProjectType catalog value, `DESIGN_ARCHITECTURE`, but the two concepts are unrelated — ProjectType does not decide Contribution weight).
- **COURSE mode (default):** `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` — same route, body/response now `{codeWeight, testWeight, documentWeight, researchWeight}` (all required, `>= 0`, sum 100 ± 0.01). PUT still LECTURER exact instructor only, CSRF required, no `lecturerId`. Applies to every Team in the Course while in COURSE mode.
- **TEAM mode:** `PUT /api/projects/{projectId}/group-weights` is **revived** — `{groupId, codeWeight, testWeight, documentWeight, researchWeight, note?}`, 0..1 scale (not 0..100 — do not convert), sum must be exactly 1.0. Authorization: ADMIN or the exact Course-instructor LECTURER only — **never** the Team leader/student.
- **Mode switch:** `PUT /api/v1/courses/{courseId}/contribution-config-mode` with `{"mode": "COURSE"|"TEAM"}` — LECTURER exact instructor, CSRF required. Switching to `TEAM` validates that **every current Team** in the Course already has a valid `group-weights` override; if any is missing, the switch is rejected with 409 `TEAM_MODE_CONFIGURATION_INCOMPLETE` and the mode stays `COURSE` (atomic — no partial activation). FE flow: let the Lecturer configure each Team's weights first (as "drafts"), then call this endpoint to activate; show the 409's missing-Team detail if rejected. Switching back to `COURSE` always succeeds and does not delete the Teams' saved overrides (they become inactive/historical, reusable if TEAM mode is activated again later). A Team created after TEAM mode is already active is **not** automatically covered by Course weights — it needs its own override before Contribution can be computed for it.
- **Team menu read:** `GET /api/v1/courses/{courseId}/contribution-team-weights` (ADMIN/LECTURER) — new endpoint returning the Course's current mode plus each Team's effective weights and source. Use this to populate a "current effective weights per Team" screen instead of re-deriving it client-side.
- FE UI: one "Course Contribution Criteria" form (Code/Test/Document/Research, sum 100%) for COURSE mode, plus a per-Team weight screen (Code/Test/Document/Research, sum 1.0) for TEAM mode, gated by the Course's current mode and an explicit "activate Team mode" action that can fail with a clear "N Teams still need weights" message.
- **`testWeight`/`researchWeight` (DEC-090/093 update):** a DONE Task scores TEST via `saga:test` (story points always). RESEARCH via `saga:research` **only when the Task has at least one Jira file or submitted link**; DOCUMENT is the same gate on `saga:document`. Extra evidence does not add points. CODE/TEST attachments/links are ignored. If no recognized evidence, that slice stays `0` and the weight budget redistributes. GitHub attachments are still not ingested — do not present an attachment-import UI.
- **Existing Course and Team weight rows keep their prior Code/Document values** after these migrations (not reset). Only the new columns are backfilled to `0`/`COURSE` for them.

## Task-is-sole-numeric-authority + reserved Contribution markers (DEC-089, foundation only) — 2026-08-15

Migration head **V36 → V37**. No OpenAPI change (no new route).

- New reserved Jira labels FE can let Lecturers/Students see/set on a Task (via the existing Jira label editing flow — no new SAGA endpoint): `saga:code`, `saga:test`, `saga:document`, `saga:research`. Exact string match only; a typo like `saga:test-extra` or wrong case does nothing.
- If a Task carries more than one of these markers at once (e.g. `saga:test` + `saga:research`), Backend treats it as ambiguous and excludes it from all four criteria until the conflict is resolved — it does not silently pick one. There is no dedicated error surfaced to FE for this today (it's silent in the score, not a rejected write) — a Lecturer would only notice via an unexpectedly low/zero criterion score.
- A commit linked to a Task never adds extra score on top of that Task's own DONE contribution — the Task's story points are the only number that counts. This is an internal scoring-engine change with no new FE-visible field.
- Attachment/document ingestion from Jira stores **metadata only** (DEC-090). GitHub Issue/comment attachments are **not** ingested — do not build UI expecting SAGA to auto-import GitHub attachments.

## ProjectType fixed canonical catalog (DEC-086) — 2026-08-15

OpenAPI **150 → 149** (one public POST removed). Migration head **V33 → V34**.

- ProjectType is now a **fixed, migration-seeded canonical SAGA catalog** — not a dynamic ADMIN-managed one. There is no create/update/delete API or UI for it.
- `GET /api/project-types` — unchanged contract (authenticated ADMIN/LECTURER/STUDENT, no CSRF, no Bearer) — always returns exactly 4 rows: `DESIGN_ARCHITECTURE` ("Design & Architecture"), `RESEARCH` ("Research"), `TESTER` ("Tester"), `DOCUMENT` ("Document"). ProjectType does **not** decide Contribution weight (see the section above — the two are independent).
- `POST /api/project-types` **no longer exists**. Do not build an Admin "create ProjectType" UI.
- Project create is unchanged: `POST /api/teams/{teamId}/projects` still requires `projectTypeId` (missing → `PROJECT_TYPE_REQUIRED`; unknown → `PROJECT_TYPE_NOT_FOUND`). FE flow: `GET /api/project-types` first, then send the selected `projectTypeId`. Do not send `code`/`name` in place of the UUID.
- Existing projects created before this migration read back `projectType: null` (legacy-compatible) since their old catalog row no longer exists — this is a product-approved reset, not a bug.

## Self Profile V1 (DEC-103) — 2026-08-17

Profile screen uses browser session only: `credentials: "include"`, `JSESSIONID`, and
the existing `XSRF-TOKEN` / `X-XSRF-TOKEN` CSRF pair for the mutation. Do not use
Bearer authentication and do not send actor/profile/student/lecturer IDs.

| Method | Endpoint | Actor | Response |
| --- | --- | --- | --- |
| GET | `/api/auth/me` | authenticated session | canonical `AuthMeResponse` |
| PATCH | `/api/auth/me` | active STUDENT or LECTURER + CSRF | canonical `AuthMeResponse` |

```ts
type SelfProfileUpdateRequest = {
  fullName?: string;       // trim; non-blank if supplied; max 255
  avatarUrl?: string | null; // null clears local avatar; otherwise absolute HTTP(S), max 2048
};
```

PATCH is sparse: omitted values remain unchanged. Only `fullName` and `avatarUrl` are
editable. `cognitoSub`, `email`, `studentCode`, `applicationRole`, `accountStatus`,
`localProfileId`, Team role, and Course membership are read-only and must not be sent.
The backend never fetches avatar URLs, uploads files, calls Google/Cognito, or accepts
provider tokens. `studentCode` is returned for STUDENT and `null` for LECTURER/ADMIN.

Backend owns the status gate. `401 ACCOUNT_DISABLED` for an inactive/suspended session
also applies to `/api/auth/me` and PATCH; clear FE auth state and do not retry. PENDING
Student behavior remains blocked by the same active-status gate. OIDC initializes name
and valid picture only for a newly created local profile; later logins do not overwrite
locally edited profile fields.

This supersedes the earlier Avatar note that prohibited all browser avatar URLs; V1
allows the validated local `avatarUrl` PATCH above.

## Avatar / Student progress / Lecturer Course weights — 2026-08-15

Browser auth: `JSESSIONID` + `credentials: "include"`. GET không CSRF. POST/PUT/PATCH/DELETE cần CSRF. **Không Bearer.** OpenAPI **150**. Migration **V33**. Full suite **không** green (1019 / 23 fail / 8 error).

### Avatar

- Dùng `avatarUrl` từ `GET /api/auth/me` (nullable). Student Basic Info cũng đọc `Student.avatarUrl` (nullable).
- FE **không** gọi Google API để lấy ảnh, **không** gửi avatar URL hay provider token lên Backend.
- Picture sync xảy ra lúc login OIDC khi claim `picture` hợp lệ (http/https, host, ≤2048). Absent/invalid: login vẫn thành công, avatar đã lưu không bị xóa.
- Fallback UI khi `avatarUrl` null. Cognito Google mapping console đã confirmed; runtime login smoke **TBD**.

```ts
type AuthMeResponse = {
  cognitoSub: string;
  email: string;
  fullName: string;
  applicationRole: "ADMIN" | "LECTURER" | "STUDENT";
  localProfileId: string;
  accountStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING" | null;
  avatarUrl: string | null;
  studentCode: string | null; // canonical only for STUDENT
};
```

### Student progress

`GET /api/v1/courses/{courseId}/students/{studentId}/progress`

- MEMBER: chỉ chính mình → 200; teammate → 403
- LEADER: chính mình + Student cùng exact Team → 200; Team khác / Course khác → 403
- MENTOR / không membership → 403
- LECTURER: chỉ exact Course instructor
- ADMIN: retained
- Anonymous → 401
- Actor luôn từ session `SagaPrincipal.localProfileId`
- Target có thêm Team membership khác trong cùng Course **không** 409 nếu vẫn thuộc exact Team Leader (DEC-085). 409 chỉ khi MEMBER self ambiguous hoặc target thuộc nhiều Team actor cùng lead.
- **Không** suy rộng Leader sang toàn Course hoặc toàn Class. `classId` không phải progress param; scope = path `courseId` + exact Team + `roleInTeam`. LEADER Course A không áp Course B trừ khi actor independently LEADER exact Team Course B.
- **Không** suy rộng STUDENT sang activities, contribution-detail, early-warnings, Lecturer Dashboard. Graph routes overview/heatmap/interactions/burndown vẫn theo DEC-080 (LEADER/MEMBER exact Team).

### Course contribution slice weights (official new FE)

Scale **0..100**, example `30 / 20 / 10 / 40`. Sum = 100 ± 0.01. Không nhầm Team/Project group weights (`0..1`, sum `1.0`).

```http
GET /api/v1/courses/{courseId}/contribution-slice-weights
PUT /api/v1/courses/{courseId}/contribution-slice-weights
```

PUT body — **không** `lecturerId` / `adminId`:

```json
{ "codeWeight": 30, "testWeight": 20, "documentWeight": 10, "researchWeight": 40 }
```

- GET: ADMIN mọi Course; LECTURER chỉ Course mình dạy
- PUT: LECTURER exact instructor; other Course / STUDENT / ADMIN → 403
- PUT cần CSRF; GET không
- **Không còn "precedence" (Project+Team trước, Course sau).** Course dùng một trong hai mode loại trừ nhau (`Course.contributionConfigMode`): PUT này chỉ có hiệu lực khi Course đang ở `COURSE` mode. Khi Course ở `TEAM` mode, mọi Team phải có `ProjectGroupWeightConfig` riêng qua `PUT /api/projects/{projectId}/group-weights` — không Team nào fallback về giá trị PUT này. Xem section "Contribution weight: Course-default + optional exclusive Team override (DEC-088)" ở đầu file cho chi tiết mode switch.
- Legacy request → Admin decision vẫn tồn tại; **new FE không dùng** cho normal Course-weight editing.

## J1J Update Priority business contract — 2026-08-10

## Jira Web sync correctness — 2026-08-13

Không có frontend endpoint hay auth flow mới. Jira Web gửi provider webhook tới backend; FE không gọi/replay webhook và vẫn đọc Task local bằng browser session.

- Issue created/updated: authenticated webhook tạo durable deduplicated receipt rồi kích hoạt canonical Jira reconciliation. Scheduler/manual sync là fallback; UI có thể theo dõi safe state qua `/api/projects/{projectId}/sync-status`, `/sync-history` và Admin local integration health.
- Story Point: generic reconciliation tự discovery estimation field theo board và request exact field; FE không gửi/nhập `customfield_*`. Giá trị Jira whole non-negative như `5`, `5.0`, `"5"`, `"5.0"` map về integer. Canonical null clear local; field provider omit không làm mất giá trị cũ.
- Issue deleted trực tiếp trên Jira Web: authenticated delete receipt tombstone Task local; Task biến khỏi active list/detail. Duplicate/unknown delete là no-op có kiểm soát và không ảnh hưởng Project khác. Backend không hard-delete Contribution/Peer Review/history.
- Admin health local-only có thêm `latestWebhookReceipt` cho Jira/GitHub và `latestWebhookMaintenance` cho Jira; sync history có maintenance job `OTHER` với safe stage/category. Đây là persisted diagnostics, không phải provider-live ping. Runtime delivery trên Jira Cloud/Railway vẫn `TBD_DEPLOYMENT_SMOKE`; FE không gửi Bearer, provider token, secret hoặc raw Jira payload.

Normal FE cập nhật priority bằng business enum, không lấy hoặc hardcode Jira numeric/provider ID:

```json
{ "priority": "HIGH" }
```

`priorityId` vẫn được backend nhận để tương thích consumer nâng cao cũ nhưng normal FE không dùng. Không gửi đồng thời `priority` và `priorityId`; backend trả `400 JIRA_PRIORITY_INVALID`. `JIRA_PRIORITY_RESOLUTION_NOT_FOUND` hoặc `JIRA_PRIORITY_RESOLUTION_AMBIGUOUS` nghĩa metadata Jira không thể map duy nhất; FE giữ input và hiển thị lỗi, không yêu cầu người dùng nhập Jira ID. `JIRA_EDIT_FIELD_NOT_ALLOWED` nghĩa Jira không cho sửa field. Runtime production vẫn `TBD_DEPLOYMENT_SMOKE`.

`componentIds` vẫn là Jira component IDs; source hiện chưa chứng minh public options endpoint. FE không tự hardcode component ID; gap này chưa được giải quyết trong J1J. Transition là ngoại lệ provider-ID round-trip: FE lấy `transitionId` từ `GET /tasks/{taskId}/transitions` của đúng task rồi gửi lại POST, không suy từ status name.

## J1I Estimation response contract — 2026-08-10

FE vẫn gửi `{ "value": <integer không âm> }`; không đổi sang string dù Jira có thể biểu diễn estimation bằng decimal string. Backend xem canonical Task snapshot là nguồn truth và chỉ trả `200` khi `storyPoint` integer canonical đúng request.

Nếu canonical provider response invalid sau Jira đã xác nhận mutation, FE giữ cùng body và `Idempotency-Key` để retry recovery; không tạo key mới, không gửi PUT lần hai. Lỗi giữ taxonomy `JIRA_RESPONSE_INVALID`/502 hiện hữu.

## J1H Jira Task Estimation recovery — 2026-08-10

Với `PUT /api/v1/projects/{projectId}/tasks/{taskId}/estimation`, gửi `{ "value": <integer không âm> }` và giữ nguyên `Idempotency-Key` khi retry cùng intent. Sau remote success, backend chỉ fetch/upsert Jira canonical và trả `200` khi `storyPoint` canonical đúng value đã gửi; không gửi PUT estimation lần hai.

Nếu nhận lỗi recovery sau sự cố canonical, FE không tạo key mới và không đổi value để "sửa" trạng thái: retry cùng body/key. Background recovery không tự complete estimation vì backend không persist target intent ngoài fingerprint. `0` là giá trị hợp lệ. CSRF, session và quyền Project Manager không đổi.

## J1G Jira Task Update contract — 2026-08-10

Historical J1G baseline accepted `title`, `description`, `priority`, advanced `priorityId`, `dueDate` (`YYYY-MM-DD`), `labels`, and `componentIds`. J1K now also accepts business `type`; assignee, sprintId, estimation and status remain on their separate routes. Omit/null means unchanged; `labels: []`/`componentIds: []` replace-all empty.

| Ý định | Endpoint | Body tối thiểu |
| --- | --- | --- |
| Sửa title/description/priority/due date/labels/components | `PUT /tasks/{taskId}` | chỉ field thực sự muốn đổi |
| Assign/unassign | `PUT /tasks/{taskId}/assignee` | `assigneeId` hoặc `unassign: true` |
| Move Sprint/backlog | `PUT /tasks/{taskId}/sprint` | `sprintId` hoặc `backlog: true` |
| Estimation | `PUT /tasks/{taskId}/estimation` | `value` |
| Status | `POST /tasks/{taskId}/transitions` | `transitionId` |

FE gửi sparse body. `description` non-null luôn requested vì ADF canonicalization không giữ formatting. Normal priority dùng `priority` business enum; `priorityId` chỉ là override nâng cao tương thích ngược. `400 JIRA_EDIT_FIELD_NOT_ALLOWED` giữ input và hiển thị lỗi. CSRF + `Idempotency-Key` vẫn bắt buộc; thiếu header là `400 INVALID_REQUEST`.

# SAGA Frontend API Integration Guide

## A13 — Admin advanced capability boundary, 2026-08-10

Không có API Admin mới trong A13. FE reuse shared route khi ADMIN đã được source cho phép: `/api/v1/courses/**` (bao gồm roster), Team roster, Task/Sprint, analytics, Peer Review và Contribution theo exact route hiện hữu. Không gọi `/api/admin/courses/**` vì không tồn tại.

Các capability vẫn chưa có endpoint: per-user audit history, đổi role, reset password và generic evaluation settings. Course membership mutation và notification broadcast đã được bổ sung ở milestone sau và được mô tả trong quick start cuối tài liệu. FE không dựng request giả, không gửi `actorId`/`adminId`, không dùng Bearer hay Cognito Admin flow. Dashboard anomaly/graph-processing chart không thuộc A13 (lịch sử scope); Admin Dashboard V1 sau đó đã thêm `/api/admin/reports/anomalies` và `/api/admin/reports/graph-processing` — xem section Merged main FE contracts 2026-08-15.

## A12 — Bàn giao Admin cho FE, 2026-08-09

| Feature | Endpoint | Method | Role | CSRF | Request / response | Status | FE action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| User toàn cục | `/api/admin/users` | GET | ADMIN | Không | filter/page → safe `Page` | CONFIRMED | Render danh sách. |
| Account status | `/api/admin/users/{id}/status` | PATCH | ADMIN | Có | `{status}` → safe user | CONFIRMED | Chỉ Student/Lecturer; không gửi PENDING. |
| Global import | `/api/admin/users/import` | POST | ADMIN | Có | multipart role STUDENT/LECTURER + XLSX → summary | CONFIRMED | Không gọi cho Admin. |
| Audit/stats/health | `/api/admin/audit-logs`, `/system-stats`, `/integrations/health` | GET | ADMIN | Không | local sanitized snapshot | CONFIRMED | Không render actor/IP/payload; health không live provider. |
| Teams/projects | `/api/admin/teams`, `/api/admin/projects` | GET | ADMIN | Không | paged local summaries | CONFIRMED | Read-only. |
| Course progress | `/api/admin/course-progress-overview` | GET | ADMIN | Không | paged current counts | CONFIRMED | Không diễn giải final grade. |
| Course export | `/api/admin/reports/courses/{courseId}/export` | GET | ADMIN | Không | XLSX attachment | CONFIRMED | Tải file, không coi là bảng điểm. |
| Active Semester | `/api/admin/settings/active-semester` | GET/PUT | ADMIN | PUT có | typed Semester setting | CONFIRMED | Không dùng generic settings UI. |
| Subject/Class/Semester/Course | `/api/v1/subjects`, `/classes`, `/semesters`, `/courses` | POST/PUT/DELETE | ADMIN | Có | request domain → entity/204 | CONFIRMED | DELETE là soft-delete có dependency guard. |
| Course instructors | `/api/v1/courses/instructors` | GET | ADMIN | Không | paged lecturer options | CONFIRMED | Dùng khi gán Course. |

Không gọi endpoint không tồn tại cho per-user audit, impersonation, role mutation, password reset,
Project DELETE hoặc generic settings. Notification broadcast và manual Course membership dùng đúng các route
được mô tả ở phần quick start; không invent route khác. Mọi request
dùng `credentials: "include"`; không dùng Bearer.

## Account lifecycle M3B — 2026-08-09

`PATCH /api/admin/users/{id}/status` đã có. Chỉ ADMIN, `credentials: "include"` và `X-XSRF-TOKEN`; body là `{ "status": "ACTIVE" | "INACTIVE" | "SUSPENDED" }`. Response 200 là safe `AdminUserReadResponse`; Student/Lecturer hỗ trợ, Admin target 400 `ACCOUNT_STATUS_TARGET_UNSUPPORTED`, PENDING 400 `ACCOUNT_STATUS_PENDING_NOT_ALLOWED`, ID không có 404. Sau status change, business API của session đó bị check DB ngay request tiếp theo; `/api/auth/me` vẫn 200 và trả status hiện tại, logout vẫn dùng được.

## AccountStatus M3A audit — 2026-08-09

`PATCH /api/admin/users/{id}/status` **chưa tồn tại**. Không gọi route này hoặc suy diễn có thể suspend user qua UI. `GET /api/admin/users` trả localProfileId và status chỉ cho Student; Admin/Lecturer có `accountStatus: null`. Khi có policy được phê duyệt, endpoint sau này sẽ cần ADMIN session + CSRF; không dùng Bearer.

## Course Update và Soft Delete — 2026-08-09

`PUT /api/v1/courses/{id}` dùng nguyên `CourseRequest`; `DELETE /api/v1/courses/{id}` không body. Cả hai cần `ADMIN`, `credentials: "include"` và CSRF. PUT thành công 200; DELETE thành công 204. Subject/Class/Semester tombstone hoặc Course missing/tombstone trả 404; courseCode duplicate và Course còn Team/Project/invitation/weight config trả 409. Sau DELETE, GET Course detail/list/filter không trả tombstone; không gọi lại DELETE và không tái dùng code.

## Semester Update và Soft Delete — 2026-08-09

`PUT /api/v1/semesters/{id}` dùng nguyên `SemesterRequest`; `DELETE /api/v1/semesters/{id}` không body. Cả hai cần `ADMIN`, `credentials: "include"` và CSRF. PUT thành công 200; DELETE thành công 204. Missing/tombstoned Semester 404, code duplicate 409, endDate trước startDate 400, Semester đang được Course dùng 409. Sau DELETE, GET detail/list/search không trả tombstone; code không tái sử dụng.

## Admin Read Foundation — 2026-08-09

Các API sau yêu cầu session browser `ADMIN`, gọi `credentials: "include"`; là GET nên không gửi CSRF hoặc Bearer. Anonymous `401`, Lecturer/Student `403`.

| Path | Query | Response |
| --- | --- | --- |
| `GET /api/admin/users` | keyword, role, accountStatus, page=0, size=20 (1..100) | Page local profile an toàn |
| `GET /api/admin/audit-logs` | page=0, size=20 (1..100) | Page newest-first, không raw payload/IP/actor |
| `GET /api/admin/system-stats` | — | local counts, active integration count, generatedAt |
| `GET /api/admin/integrations/health` | — | Jira/GitHub local state/count; không provider-live health |
| `GET /api/admin/teams` | page=0, size=20 (1..100) | Team/Course/nullable Project summary |
| `GET /api/admin/projects` | page=0, size=20 (1..100) | Project/Course/Jira local/GitHub aggregate |

Không phụ thuộc hay render Cognito sub, token, raw provider response, raw audit JSON, IP, repository URL hoặc secret. accountStatus chỉ có semantic cho Student.

## M10 Integration health — 2026-08-09

FE ADMIN gọi `GET /api/admin/integrations/health` với `credentials: "include"`; GET không
cần CSRF hoặc Bearer. Anonymous nhận 401, Lecturer/Student 403. Chỉ render raw local state:
`enabled`, linked-project count, status count, latest persisted sync timestamp và receipt
count. Không coi response là ping/live availability Jira/GitHub và không hiển thị credential,
secret, webhook ID, payload, URL hay Cognito subject.

Không có route per-user audit log hoặc impersonation. FE không gửi Cognito subject/token để
tìm audit và không có token tạm thời/Bearer flow.

A11A chỉ bổ sung metadata durable nội bộ cho event audit mới; không đổi response
`GET /api/admin/audit-logs` và không expose `actorLocalProfileId`, `actorRole`, Cognito subject,
IP hay raw payload. Chưa có route per-user vì history trước đó không đủ coverage.

## Jira Task Create metadata — 2026-08-09

`POST /api/v1/projects/{projectId}/tasks` normal does not require Jira numeric IDs. FE sends `title`, business `type` (`BUG`, `FEATURE`, `REQUEST`, `STORY`, `TASK`, `EPIC`, `SUBTASK`) and optional business `priority` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). Backend resolves Jira issue type/priority from metadata for the exact current Project.

`issueTypeId` và `priorityId` vẫn được chấp nhận như advanced override optional cho client cũ/debug admin. Backend luôn validate: ID issue type phải thuộc metadata Project; ID priority phải thuộc `priority.allowedValues`. Không gửi `customfield_*`, token, Bearer hay Jira numeric ID hardcode. Nếu auto-resolution không có đúng một candidate, API fail closed; FE không fallback sang tự chọn numeric ID.

J1C ưu tiên exact canonical name sau khi backend dedup provider ID: ví dụ `Task` thắng
`Spike` cho `TASK`, `Critical` thắng `Highest` cho `CRITICAL`. Khi không có exact, backend chỉ
dùng semantic fallback nếu còn đúng một ID; nhiều ID thật sự vẫn là `409` resolution ambiguous.
FE không đổi request normal và khi chọn “Mặc định” vẫn omit cả `priority`/`priorityId`.

## Sprint list item state — 2026-08-08

`GET /api/v1/projects/{projectId}/sprints` và `GET /api/v1/teams/{teamId}/sprints` cùng trả thêm trường additive `state` trong từng phần tử `sprints`. Đây là Jira Sprint state canonical cục bộ, kiểu `string`, giữ nguyên giá trị Jira như `future`, `active` hoặc `closed`; không suy diễn từ ngày tháng và không gọi Jira khi đọc danh sách.

Ví dụ một item: `sprintId`, `sprintName`, `externalSprintId`, `state`, `startDate`, `endDate`, `goal`. `response.state` ở cấp danh sách vẫn giữ nghĩa cũ `PROJECT_NOT_CREATED` / `EMPTY` / `READY`; nó khác với `response.sprints[i].state`. Sau Start/Close đã canonical write-through, lần GET list tiếp theo phản ánh state local mới.

## Jira simple-board Sprint capability probe — 2026-08-07

`POST /api/projects/{projectId}/jira/link` vẫn dùng browser session + CSRF và không nhận Bearer/provider credential từ FE. Runtime SDP có board `35`, `type=simple`, association `10034/SDP`; Board/Project Features không đưa ra Sprint identifier hữu dụng nên FE không được suy diễn từ chúng.

Backend tự probe read-only endpoint Sprint của board với `maxResults=1`. Với simple board, 200 và page hợp lệ — kể cả không có Sprint item — cho phép link; board external `35` sẽ được dùng làm `originBoardId` cho Create Sprint flow hiện hữu. Scrum vẫn resolve trực tiếp. FE không gửi board ID tự chọn, Sprint list hay raw provider response để tác động lựa chọn.

Nếu Jira trả 400, API trả `409 JIRA_SPRINT_CAPABILITY_UNCONFIRMED` và FE hiển thị capability chưa được Jira xác nhận, không retry mù. 401/403/404/429/5xx-network/malformed-2xx giữ category an toàn `JIRA_ACCESS_REVOKED` / `JIRA_ACCESS_FORBIDDEN` / `JIRA_BOARD_NOT_FOUND` / `JIRA_RATE_LIMITED` / `JIRA_PROVIDER_UNAVAILABLE` / `JIRA_RESPONSE_INVALID`.

Link preflight hiện cần thêm `read:sprint:jira-software`; sau deploy scope mới, flow là disconnect Jira → connect/consent lại → callback mới → link. Không gửi token, cookie, CSRF hay raw Jira body cho support/log.

## Jira 3LO re-consent và lỗi scope — 2026-08-07

Jira 3LO site API do backend gọi qua `https://api.atlassian.com/ex/jira/{cloudId}/...`. FE chỉ nhận danh sách site an toàn từ callback result, gửi `cloudId` cùng Jira project đã chọn tới `POST /api/projects/{projectId}/jira/link`, và không gửi Bearer, access token, refresh token, provider response hoặc site URL để backend tin cậy.

Backend đối chiếu `cloudId` với `accessible-resources` của fresh grant. Nếu site không còn trong resource, backend trả `JIRA_SITE_NOT_AUTHORIZED`; FE yêu cầu người dùng kết nối lại/chọn site hợp lệ, không retry provider call với URL tự ghép.

Nếu resource thiếu scope cần cho chính link (project read, board discovery hoặc dynamic webhook), backend trả `409 JIRA_SCOPE_INSUFFICIENT`:

```json
{
  "code": "JIRA_SCOPE_INSUFFICIENT",
  "message": "Jira authorization does not include the permissions required by this integration"
}
```

FE hiển thị hướng dẫn reconnect/re-consent; không diễn giải lỗi này là session login failure hay Jira credential revoked. Scope Sprint/Task không dùng trong link được kiểm tra khi người dùng gọi operation đó. Sau khi deploy bộ scope mới trong Atlassian Developer Console, flow bắt buộc là disconnect Jira → `/jira/connect` mới → Atlassian consent mới → callback/result mới → `POST /jira/link`. Scope chính xác do backend dùng gồm Platform read/write, webhook, `offline_access`, và Jira Software board/sprint/issue scopes được ghi trong SAGA system context/decision log; `offline_access` không phải scope site-resource. Quyền Jira thực tế của user vẫn có thể gây `JIRA_ACCESS_FORBIDDEN` dù scope đã đủ.

## Jira sync/relink status contract — 2026-08-06

- Jira hydration chạy nền; FE không gửi provider credential và không nhận raw Jira response. Theo dõi qua sync status/history đã có, chỉ dùng safe `errorCategory`/failure stage.
- Category actionable: `JIRA_ACCESS_REVOKED` tương ứng Jira 401 (operator cần relink), `JIRA_ACCESS_FORBIDDEN` là 403 (cần cấp quyền), `JIRA_SPRINT_NOT_FOUND` là 404 Sprint (không yêu cầu tự relink), `JIRA_RATE_LIMITED` là 429 và `JIRA_PROVIDER_UNAVAILABLE` là 5xx/network. Không suy đoán OAuth scope hoặc tự gửi Bearer token.
- Một Sprint lỗi có thể khiến job `PARTIAL_FAILURE` và cursor không advance trong khi Sprint khác vẫn được hydrate. `JIRA_SPRINT_NOT_FOUND` không đồng nghĩa local data bị xóa; FE không tự ẩn/xóa Sprint history theo error này.
- Sau disconnect, retained Jira row/history vẫn có thể hiện trong dữ liệu local nhưng không phải integration active. Relink vẫn dùng session + CSRF hiện có và fresh OAuth grant; không giữ/reuse token ở frontend.
- Khi `POST /api/projects/{projectId}/jira/link` chọn Jira Project đã thuộc SAGA Project khác, backend trả `409 JIRA_PROJECT_ALREADY_LINKED` với message “This Jira project is already linked to another SAGA project”. FE hiển thị conflict và không retry/không chuyển ownership. Nếu retained Project cố đổi sang Jira Project khác, backend trả `409 JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED`; FE yêu cầu quyết định nghiệp vụ mới, không tự disconnect/delete history. `JIRA_BOARD_UPSERT_CONFLICT` là safe 409 retry/reload failure; không parse SQL/constraint hay provider payload.
- Runtime incident history (externalSprintId, upstream status) vẫn TBD; safe diagnostics là vận hành backend, không phải API payload cho FE.

## Swagger/OpenAPI tiếng Việt - 2026-08-06

- Swagger UI hiển thị nhóm, tiêu đề operation, mô tả, parameter và schema bằng tiếng Việt. Nội dung mô tả phản ánh contract hiện có, không thay API frontend đang gọi.
- Tiếp tục gọi API bằng browser session với `credentials: "include"`. Swagger UI tự đọc/bootstrap cookie CSRF và chỉ gắn header cho POST/PUT/PATCH/DELETE cùng origin; frontend không tự khai báo token trên từng endpoint.
- Không có Bearer/OAuth input cho API nghiệp vụ. Không gửi GitHub/Jira token, cookie thật, CSRF token thật, secret hoặc private key vào Swagger.
- Với Jira mutation, `Idempotency-Key` vẫn được mô tả vì Backend bắt buộc header này. Branch GitHub vẫn là query parameter có thể chứa `/` và phải URL-encode.
- Generated `/v3/api-docs` được test có 96 operation, summary/description/tag đầy đủ và không có CSRF header lặp. Production Swagger UI smoke test vẫn TBD.

## Project dashboard and GitHub contract - 2026-08-06

- All routes below use the existing session contract: `credentials: "include"`. State-changing reconnect also needs the existing CSRF header; do not send bearer/provider credentials.
- Project update accepts `{ "name": "...", "description": "..." }`; `description` may be null/blank. Project detail now returns `description`.
- Dashboard: `GET /api/projects/{projectId}/dashboard-stats`. Use local aggregates as a snapshot, not a claim that GitHub was just refreshed.
- Branches: `GET /api/projects/{projectId}/github/repositories/{repositoryId}/branches?page=0&size=20`. Response contains repository id/name and `{ content, page, size, hasNext }`; each item has `name`, `headSha`, `protectedBranch`.
- Commits: `GET /api/projects/{projectId}/github/repositories/{repositoryId}/commits?branch=feature/x&page=0&size=20`. Keep `branch` URL-encoded as a query parameter (never a path segment). Each item has SHA, message, author fields, authored/committed instants and URL. `size` is 1..100.
- Reconnect: `POST /api/projects/{projectId}/github/repositories/{repositoryId}/connect`, expected `202 Accepted` with no body. Show a pending/backfilling state and poll status/history; handle `GITHUB_RECONNECT_NOT_REQUIRED` and `GITHUB_RECONNECT_REQUIRES_INSTALLATION` as actionable 409 responses.
- History: `GET /api/projects/{projectId}/sync-history?page=0&size=20&targetSystem=GITHUB&status=IN_PROGRESS&jobType=RECONCILIATION`. Filters are optional. Response page contains the same sanitized job fields as status (`errorCategory`, not provider payload/error secrets). Use this endpoint for timeline/history; `/sync-status` is only the compact latest-20 status view.
- These project integration operations are manager-only. For 403/404, follow the existing project authorization UX; never infer access from a repository id alone.

## Jira Sprint time và Scrum board contract — 2026-08-06

- `startDate`/`endDate` Create và Update Sprint phải là ISO-8601 có offset, ví dụ `2026-08-06T06:03:50Z` hoặc `2026-08-06T13:03:50+07:00`; không gửi `LocalDateTime` không offset.
- Sprint response trả date là UTC Instant có `Z` hoặc `null`. FE hiển thị bằng `Intl.DateTimeFormat`, không cộng cứng `+7`/nối thêm `Z`.
- Jira link tự discover Agile board theo project canonical. `jiraBoardId` là external numeric Scrum board ID; UUID local không phải Jira board ID. Zero/multiple board trả `JIRA_SCRUM_BOARD_NOT_FOUND`/`JIRA_BOARD_SELECTION_REQUIRED`; FE không chọn board đầu tiên.
- Legacy missing/malformed ID được lazy-discover khi Create Sprint; numeric valid ID được dùng lại. Invalid URL/tên/project key không gọi Jira Create Sprint. Route, `credentials: "include"`, CSRF và `Idempotency-Key` không đổi.
- UTC chỉ chuyển sang `JIRA_TIME_ZONE` khi tạo JQL literal. Backend không trả/log raw provider data. Runtime production board access/smoke test TBD.

## Contribution và Jira task-data status (2026-08-05)

- **CONFIRMED:** Jira sync keeps internal Task snapshots for labels, components
  (`id`/`name`) and a canonical plain-text description. Missing/null collection
  fields become empty and each sync replaces the prior snapshot.
- **CONFIRMED (supersedes trạng thái cũ):** Backend có Task list/detail và Task/Sprint
  write-through API; `TaskReadResponse` expose description, components và labels.
  Backend vẫn có sáu Contribution HTTP API cho current aggregate, manual override
  và Course slice weights; contract nằm tại mục "Contribution API" cuối tài liệu.
- **CONFIRMED:** Contribution evaluation là aggregate theo dữ liệu hiện tại, không
  phải historical committed snapshot. FE không tự suy ra công thức từ assessment
  endpoint và không nhận provider payload, token hoặc credential.
- **PARTIAL:** Ownership của GET contribution-evaluation đã được khắc phục và test; các risk
  actor-binding của slice-weight/decision cùng production migration vẫn được ghi riêng trong contract.

Tài liệu này được đối chiếu với controller, DTO, Security, CORS, exception
handler và integration service hiện tại của backend. Không coi tài liệu này là
nơi lưu credentials: frontend **không** cần và không được nhận OAuth token,
client secret, private key, webhook secret, database credential hoặc token đã
mã hóa.

## Jira Task và Sprint Management — 2026-08-06

### Contract chung

- Mọi request nghiệp vụ dùng browser session: `credentials: "include"`; không gửi
  `Authorization: Bearer`.
- GET không cần CSRF. POST/PUT/DELETE cần token/header CSRF theo utility hiện có.
- Mọi Task/Sprint mutation dưới đây bắt buộc header `Idempotency-Key` (không rỗng,
  tối đa 128 ký tự). FE tạo một key cho một intent và giữ nguyên key khi retry cùng
  request; không tái sử dụng key cho payload/operation khác.
- Không gửi `actorId`, `adminId`, `lecturerId`, Jira token/account credential hay raw
  provider payload. Backend lấy actor từ session `SagaPrincipal`.
- Flow write-through: `FE -> SAGA -> Jira -> canonical fetch -> local upsert -> response`.
  Jira là source of truth; local DB là canonical snapshot/read model.
- Safe domain error có `code`/`message`; backend không trả raw Jira payload hoặc
  credential. Không blind retry mutation khi nhận lỗi outcome-unknown/in-progress;
  giữ key và để recovery hoàn tất.

Quyền mutation/detail Sprint và transition metadata: ADMIN, LECTURER phụ trách
Course, hoặc STUDENT có `LEADER` membership của owning Team. Task list/detail read
cho ADMIN, assigned LECTURER và mọi STUDENT thuộc owning Team; ngoài scope trả 403.

### Task routes

| Method | Exact path | Mục đích | CSRF / Idempotency |
| --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/tasks` | List Task local canonical | Không / không |
| GET | `/api/v1/projects/{projectId}/tasks/{taskId}` | Task detail | Không / không |
| POST | `/api/v1/projects/{projectId}/tasks` | Tạo Jira Task | Có / có |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}` | Cập nhật Task | Có / có |
| DELETE | `/api/v1/projects/{projectId}/tasks/{taskId}` | Xóa Jira rồi tombstone local | Có / có |
| GET | `/api/v1/projects/{projectId}/tasks/{taskId}/transitions` | Transition metadata | Không / không |
| POST | `/api/v1/projects/{projectId}/tasks/{taskId}/transitions` | Transition Task | Có / có |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/assignee` | Assign/unassign | Có / có |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/sprint` | Move Sprint/Backlog | Có / có |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/estimation` | Set estimation | Có / có |

Task list query: `keyword` tìm `externalKey`/`title`; filter `sprintId`, `assigneeId`,
`status`; `sortBy=externalKey|title|status|priority|storyPoint|dueDate|externalUpdatedAt`,
`sortDirection=asc|desc`, mặc định `externalKey/asc`; `page=0`, `size=20`, size 1..100.
Task tombstone không xuất hiện. Assignee request phải chọn đúng một trong
`assigneeId` hoặc `unassign: true`; Sprint request chọn đúng một trong `sprintId`
hoặc `backlog: true`; estimation `value` là integer không âm. Create/update dùng
Jira metadata thực tế; FE không tự dựng `customfield_*`.

### Sprint routes

| Method | Exact path | Mục đích | CSRF / Idempotency |
| --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/sprints` | List theo Project | Không / không |
| GET | `/api/v1/teams/{teamId}/sprints` | List theo Team | Không / không |
| GET | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Sprint detail | Không / không |
| POST | `/api/v1/projects/{projectId}/sprints` | Tạo Sprint | Có / có |
| PUT | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Cập nhật Sprint | Có / có |
| POST | `/api/v1/projects/{projectId}/sprints/{sprintId}/start` | Start future Sprint có dates | Có / có |
| POST | `/api/v1/projects/{projectId}/sprints/{sprintId}/close` | Close active Sprint | Có / có |
| DELETE | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Xóa Jira, detach Task, tombstone local | Có / có |

`JiraSprintResponse` gồm `id`, `externalSprintId`, `name`, `state`, `goal`,
`startDate`, `endDate`, `completeDate`. Ba date là ISO local datetime đã normalize
về UTC ở backend; FE đổi sang timezone hiển thị. Null là hợp lệ khi Jira không có
dữ liệu; FE không tự dựng ngày. Embedded Sprint trong Task không có quyền clear
canonical dates.

### Course Student Basic Info

`GET /api/v1/courses/{courseId}/students/{studentId}` dùng session, không cần CSRF:
ADMIN đọc mọi Course, LECTURER chỉ Course được phân công, STUDENT 403, anonymous 401.
Không có membership qua `TeamMember -> Team -> Course` trả 404; nhiều legacy
membership trả 409. Endpoint chưa hỗ trợ Student thuộc Course nhưng chưa có Team.

```ts
type AccountStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING";
type RoleInTeam = "LEADER" | "MEMBER" | "MENTOR";

type CourseStudentBasicInfoResponse = {
  courseId: string;
  studentId: string;
  studentCode: string;
  fullName: string;
  email: string;
  avatarUrl: string | null; // nullable; đọc Student.avatarUrl sau OIDC picture sync; fallback UI khi null
  accountStatus: AccountStatus; // không phải Course enrollment status
  team: {
    teamId: string;
    teamName: string;
    roleInTeam: RoleInTeam;
  };
};
```

`team` không nullable theo model hiện tại; không gửi/hiển thị giả định Course status.

## Base URL và tài liệu API

| Môi trường | URL |
| --- | --- |
| Local backend | `http://localhost:8080` |
| Production backend | `https://saga-backend-production-3951.up.railway.app` |
| Production Swagger UI | `https://saga-backend-production-3951.up.railway.app/swagger-ui/index.html` |
| Production health | `https://saga-backend-production-3951.up.railway.app/actuator/health` |

Swagger chỉ có mặt khi backend bật cả `SPRINGDOC_API_DOCS_ENABLED` và
`SPRINGDOC_SWAGGER_UI_ENABLED`. Frontend không gửi hai biến này và không cần
biết bất kỳ biến môi trường bí mật nào của backend.

`GET /` là landing page công khai. `GET /actuator/health` là health check công
khai. Mọi API nghiệp vụ dưới đây đều cần session, trừ các endpoint được ghi rõ
là public.

`GET {PUBLIC_BASE_URL}/privacy` is a public HTML UTF-8 Privacy Policy page. It
does not require a browser session, Bearer token, or CSRF token, and it must not
be constructed from the Railway example URL above. Frontend may link to it
directly; it is not an OAuth callback or integration flow. The SAGA operator
must configure `PRIVACY_CONTACT_URL` to a real public contact URL before deploy;
the backend validates that it is an absolute `http` or `https` URL.

---

## 1. Authentication: browser session với Cognito

Flow hiện tại là:

```text
Frontend
  -> Spring Boot: GET /api/auth/login
  -> Cognito Hosted UI
  -> Google hoặc Cognito native login
  -> Spring Boot callback /login/oauth2/code/cognito
  -> backend tạo Spring Security session (JSESSIONID)
  -> redirect về AUTH_SUCCESS_REDIRECT_URI
  -> Frontend gọi API kèm session cookie
```

Backend là confidential OAuth/OIDC client và quản lý OAuth/OIDC token nội bộ.
Sau login thành công, backend thay authentication ban đầu bằng `SagaPrincipal`
trong session, không trả access token, ID token hoặc refresh token cho FE.

**Không lưu token vào `localStorage` hoặc `sessionStorage`.** Mọi gọi API từ
trình duyệt phải gửi cookie:

```ts
fetch(`${API_BASE_URL}/api/auth/me`, { credentials: "include" });
// Axios: axios.create({ baseURL: API_BASE_URL, withCredentials: true })
```

### Login, current user và logout

| Method | Endpoint | Auth | Kết quả từ code |
| --- | --- | --- | --- |
| GET | `/api/auth/login` | Public | `302 Found` đến `/oauth2/authorization/cognito`. |
| GET | `/api/auth/me` | Session | `200` với `AuthMeResponse`; endpoint này vẫn chạm CSRF token để browser nhận cookie CSRF. |
| GET | `/api/auth/csrf` | Session | `200` với token CSRF JSON cho frontend khác domain; không trả session id hay OAuth token. |
| POST | `/api/auth/logout` | Framework-managed + CSRF | CSRF hợp lệ trả `302` đến Cognito `/logout`; session hiện có bị hủy. Thiếu/sai CSRF trả `403`. |

Khởi tạo login bằng **browser navigation**, không dùng `fetch`:

```ts
window.location.assign(`${API_BASE_URL}/api/auth/login`);
```

`AUTH_SUCCESS_REDIRECT_URI` quyết định URL FE sau khi login xong;
`AUTH_LOGOUT_REDIRECT_URI` quyết định URL sau khi Cognito logout. Đây là cấu
hình backend, không phải response FE được tự đặt. Cả hai phải là URL HTTP(S)
tuyệt đối.

### Logout: POST browser navigation qua Cognito

Không dùng `GET /api/auth/logout` và không dùng `fetch`/Axios để mong browser
tự theo redirect logout. Backend dùng Spring Security `LogoutFilter` cho
`POST /api/auth/logout`: request cần CSRF hợp lệ; nếu có session thì backend
invalidate session, clear authentication, xoá `JSESSIONID`/`XSRF-TOKEN`, rồi
redirect browser đến Cognito `/logout` với `client_id` và logout URI đã cấu hình.
Không có Cognito token, session id hay cookie nào được đưa vào redirect URL.
Swagger UI dùng fetch nên có thể báo `Failed to fetch` khi browser theo redirect
cross-origin đến Cognito; đó không phải bằng chứng logout thất bại. Dùng form
POST/top-level navigation như dưới đây cho browser client.

Từ FE, lấy CSRF token rồi submit form POST để đây là top-level navigation:

```ts
export async function logout(): Promise<void> {
  const csrfResponse = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" }
  });

  if (!csrfResponse.ok) {
    window.location.replace("/login");
    return;
  }

  const csrf = await csrfResponse.json() as {
    token: string;
    parameterName: string;
  };
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${API_BASE_URL}/api/auth/logout`;

  const input = document.createElement("input");
  input.type = "hidden";
  input.name = csrf.parameterName;
  input.value = csrf.token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}
```

Sau redirect `Backend → Cognito → Frontend`, route FE
`/logout/callback` phải xoá user và CSRF token trong AuthContext/Redux/Zustand
(memory), rồi điều hướng về `/login` hoặc trang chủ. Không đọc/xoá `JSESSIONID`
bằng JavaScript và không gọi Cognito logout bằng server-to-server request.

`GET /api/auth/me` trả:

```ts
type AuthMeResponse = {
  cognitoSub: string;
  email: string;
  fullName: string;
  applicationRole: "ADMIN" | "LECTURER" | "STUDENT";
  localProfileId: string; // UUID
  accountStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING" | null;
  avatarUrl: string | null; // OIDC picture đã sanitize; null khi chưa có
};
```

### 401 và 403

- Không có session hoặc session không hợp lệ trên protected route: `401`.
  Security trả `error: "Unauthorized"`, `message: "Authentication is required"`.
- Có session nhưng không có quyền: `403`. Security trả `error: "Forbidden"`,
  `message: "The authenticated user does not have permission for this operation"`.
- Service integration cũng có thể trả `403` với error code
  `INTEGRATION_FORBIDDEN` khi người dùng không là team manager/reviewer phù hợp.

---

## 2. CSRF

CSRF đang được bật toàn hệ thống bằng `CookieCsrfTokenRepository`:

| Thành phần | Giá trị |
| --- | --- |
| Cookie | `XSRF-TOKEN` |
| Header | `X-XSRF-TOKEN` |
| Cookie HttpOnly | `false`; browser có thể gửi cookie, nhưng JavaScript ở domain FE khác không thể đọc cookie domain backend |
| Cookie path | `/` |
| Endpoint lấy contract token cho FE | `GET /api/auth/csrf` |
| Endpoint được miễn CSRF | Chỉ `POST /api/webhooks/github` và `POST /api/webhooks/jira` |

Theo default CSRF matcher của Spring Security, các request unsafe cần header
CSRF: `POST`, `PUT`, `PATCH`, `DELETE`. Các webhook bị miễn là endpoint dành
cho provider (`POST /api/webhooks/github`, `POST /api/webhooks/jira`), không
phải API frontend gọi thay cho provider.

Flow FE chuẩn:

Khi frontend là `http://localhost:3000` và backend là Railway, JavaScript không
thể đọc `XSRF-TOKEN` thuộc domain backend bằng `document.cookie`. Sau callback,
frontend gọi `GET /api/auth/me`, sau đó gọi `GET /api/auth/csrf` với
`credentials: "include"` và giữ response trong memory/Auth store:

```ts
type CsrfTokenResponse = {
  token: string;
  headerName: "X-XSRF-TOKEN";
  parameterName: "_csrf";
};
```

Với `POST`, `PUT`, `PATCH`, `DELETE`, gửi `credentials: "include"` và header
động `[csrf.headerName]: csrf.token`. Không lưu CSRF token hay Cognito token vào
`localStorage` nếu không cần. Nếu response 403 do CSRF/session thay đổi, gọi lại
`GET /api/auth/csrf` đúng một lần rồi retry mutation tối đa một lần; không retry
vô hạn.

### Swagger UI CSRF

Swagger UI cùng origin backend được cấu hình `withCredentials` để giữ
`JSESSIONID`. Interceptor toàn cục đọc/bootstraps cookie `XSRF-TOKEN`, decode giá
trị và gắn `X-XSRF-TOKEN` chỉ cho `POST`, `PUT`, `PATCH`, `DELETE` cùng origin.
Nó không gắn header cho `GET`, `HEAD`, `OPTIONS`, không thay `Content-Type` nên
không làm hỏng multipart import, và không gửi token sang origin khác. Swagger
không dùng Bearer token và không cần khai báo CSRF header từng endpoint.

Nếu cookie chưa có, interceptor gọi cùng origin `GET /api/auth/csrf` trước
mutation. Frontend khác origin không đọc được cookie backend; tiếp tục dùng
response JSON từ endpoint CSRF theo flow ở trên.

### TypeScript fetch utility

```ts
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ??
  "http://localhost:8080";

export type CsrfTokenResponse = {
  token: string;
  headerName: "X-XSRF-TOKEN";
  parameterName: "_csrf";
};

let csrf: CsrfTokenResponse | null = null;

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) throw new Error(`Cannot obtain CSRF token: ${response.status}`);
  csrf = (await response.json()) as CsrfTokenResponse;
  return csrf;
}

type ApiRequestOptions = RequestInit & {
  requireCsrf?: boolean;
};

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const {
    requireCsrf = false,
    headers: customHeaders,
    ...requestOptions
  } = options;

  const headers = new Headers(customHeaders);

  if (
    requestOptions.body !== undefined &&
    !(requestOptions.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }

  headers.set("Accept", "application/json");

  if (requireCsrf) {
    const csrfToken = csrf ?? await getCsrfToken();
    if (!csrfToken.token) {
      throw new Error(
        "Missing CSRF token. Call /api/auth/csrf before mutation requests."
      );
    }
    headers.set(csrfToken.headerName, csrfToken.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    credentials: "include",
    headers
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      typeof body === "object" &&
      body !== null &&
      "message" in body
        ? String(body.message)
        : `HTTP ${response.status}`;

    throw new ApiError(response.status, message, body);
  }

  return body as T;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}
```

Ví dụ mutation:

```ts
await apiRequest("/api/teams/<team-uuid>/projects", {
  method: "POST",
  requireCsrf: true,
  body: JSON.stringify({ name: "SAGA capstone" })
});
```

---

## 3. CORS, cookie và frontend environment

Backend chỉ chấp nhận các origin được khai báo bởi `FRONTEND_ORIGINS` (danh
sách cách nhau bằng dấu phẩy). Mỗi origin phải là HTTP(S) origin rõ ràng, không
chứa wildcard, path, query, fragment hoặc user info. CORS cho phép:

- methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`;
- request headers: `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`;
- exposed header: `Location`;
- credentials: `true`.

Để FE khác origin hoạt động, origin thực tế của FE phải được thêm đúng vào
`FRONTEND_ORIGINS` ở backend. Không dùng `*` khi gọi bằng credentials.

Session cookie do backend thiết lập. Với profile production, các property lấy
từ `SESSION_COOKIE_SECURE` (default `true`) và `SESSION_COOKIE_SAME_SITE`
(default `none`); profile local dùng `secure=false`, `same-site=lax`.
Frontend chỉ cần `VITE_API_BASE_URL`, ví dụ:

```env
# local
VITE_API_BASE_URL=http://localhost:8080

# production
VITE_API_BASE_URL=https://saga-backend-production-3951.up.railway.app
```

Không đưa biến secret backend nào vào bundle FE.

Railway phải cấu hình đúng tên biến mà `application-prod.properties` đang map,
không dùng tên relaxed-binding khác:

```env
FRONTEND_ORIGINS=http://localhost:3000
AUTH_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback
AUTH_LOGOUT_REDIRECT_URI=http://localhost:3000/logout/callback
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
```

Trên Cognito Hosted UI, Allowed sign-out URL phải chứa chính xác
`http://localhost:3000/logout/callback`. `COGNITO_CLIENT_ID` là client id đã
có của backend; `COGNITO_DOMAIN` là tùy chọn nếu muốn chỉ định origin Cognito
HTTPS, nếu trống backend suy ra domain từ authorization URI. Không truyền
`logout_uri` từ query string người dùng: backend chỉ dùng URI cấu hình.

`Idempotency-Key` là bắt buộc cho mọi Jira Task/Sprint mutation. Khi frontend gọi
cross-origin với browser session và `credentials: "include"`, browser sẽ preflight
header này; backend cho phép nó cùng `Content-Type` và `X-XSRF-TOKEN` trước khi
request mutation thật đến controller. Đây không thay đổi authentication contract:
frontend tiếp tục dùng session + credentials, không dùng Bearer.

`FRONTEND_ORIGINS` được tách bằng dấu phẩy, trim từng phần, bỏ phần rỗng,
deduplicate và chỉ chấp nhận HTTP(S) origin không wildcard/path/query/fragment.
CORS cho phép `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; các request
header `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`; và
`allowCredentials=true`. Preflight `OPTIONS` vì vậy không cần session.

---

## 4. Error response và validation

Các lỗi authentication/authorization và exception handler ứng dụng dùng:

```ts
type ApiErrorResponse = {
  timestamp: string; // Instant
  status: number;
  error: string;
  message: string;
  path: string;
};
```

Mapping được code định nghĩa:

| Nguồn lỗi | HTTP status | `error` |
| --- | --- | --- |
| Không xác thực | 401 | `Unauthorized` |
| Không có quyền | 403 | `Forbidden` |
| `UnauthenticatedRequestException` | 401 | `Unauthorized` |
| `IdentityConflictException` | 409 | `Conflict` |
| `InvalidIdentityException` | 422 | `Unprocessable Entity` |
| `IdentityServiceException` | 502 | `Bad Gateway` |
| `IntegrationException` | Theo exception | Mã integration an toàn, ví dụ `INTEGRATION_NOT_CONFIGURED` |

`IntegrationException` có thể mang status `400`, `403`, `409`, `502` hoặc
`503` tùy service. FE phải dùng `status`, `error` và `message`, không parse
raw provider response.

Request DTO có Bean Validation. Các lỗi validation framework (`@NotBlank`,
`@NotNull`, `@Size`, `@Positive`, `@Min`, `@Max`) hiện **không có** custom
`@ExceptionHandler` trong source; do đó FE không nên phụ thuộc vào body chi
tiết của validation error. Hiển thị `ApiError.message` khi có, đồng thời validate
form phía client theo contract dưới đây.

---

## 5. Quyền và enums FE cần biết

`SagaPrincipal` cung cấp các field của `GET /api/auth/me`: `cognitoSub`,
`email`, `fullName`, `applicationRole`, `localProfileId`, `accountStatus`.

| Enum | Giá trị |
| --- | --- |
| `ApplicationRole` | `ADMIN`, `LECTURER`, `STUDENT` |
| `AccountStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED`, `PENDING` |
| `IntegrationProvider` | `JIRA`, `GITHUB` |
| `IntegrationStatus` | `CONNECTING`, `BACKFILLING`, `ACTIVE`, `DEGRADED`, `DISCONNECTED` |
| `IdentityMappingStatus` | `ACTIVE`, `DISCONNECTED`, `PENDING_REVIEW`, `REJECTED` |
| Review action | `APPROVE`, `REJECT`, `CORRECT` |
| `SyncJobType` | `JIRA_SYNC`, `GIT_SYNC`, `INITIAL_BACKFILL`, `RECONCILIATION`, `WEBHOOK_PROCESSING`, `OTHER` |
| `SyncJobStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `PARTIAL_FAILURE`, `FAILED` |

Quyền API:

- Tất cả API master data đọc yêu cầu session. Tạo Subject/Course/Class/Semester
  yêu cầu `ADMIN`.
- Team Project và project integration yêu cầu **team manager**: `ADMIN`; hoặc
  `LECTURER` là instructor của course của team; hoặc `STUDENT` là `LEADER` của
  team đó.
- Review identity mapping yêu cầu `ADMIN`, hoặc `LECTURER` dạy course của một
  team có student cần review.
- Personal integration yêu cầu người dùng đã đăng nhập; provider phải được
  backend bật. Project integration cũng cần team-manager check.

Frontend có thể dùng role để quyết định hiển thị UI, nhưng luôn phải xử lý 403:
backend mới là nguồn quyết định quyền.

---

## 6. Master-data API

Tất cả endpoint trong phần này cần session. `page` là zero-based; mặc định
`page=0`, `size=10`; `size` phải từ `1` đến `100`.

| Method | Path | Query | Quyền | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/subjects` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Subject>` |
| GET | `/api/v1/subjects/{id}` | — | Session | 200 `Subject` |
| POST | `/api/v1/subjects` | — | ADMIN + CSRF | 201 `Subject` |
| GET | `/api/v1/classes` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Class>` |
| GET | `/api/v1/classes/{id}` | — | Session | 200 `Class` |
| POST | `/api/v1/classes` | — | ADMIN + CSRF | 201 `Class` |
| GET | `/api/v1/semesters` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Semester>` |
| GET | `/api/v1/semesters/{id}` | — | Session | 200 `Semester` |
| POST | `/api/v1/semesters` | — | ADMIN + CSRF | 201 `Semester` |
| GET | `/api/v1/courses` | `subjectId?`, `semesterId?`, `instructorId?`, `page?`, `size?` | Session | 200 `Page<CourseResponse>` |
| GET | `/api/v1/courses/{id}` | — | Session | 200 `CourseResponse` |
| POST | `/api/v1/courses` | — | ADMIN + CSRF | 201 `CourseResponse` |
| PUT | `/api/v1/courses/{id}` | — | ADMIN + CSRF | 200 `CourseResponse` |

### Course read response

`GET /api/v1/courses` returns `Page<CourseResponse>`; detail plus ADMIN create/update
also return `CourseResponse`. The stable fields include `id`, `createdAt`, `updatedAt`,
`courseCode`, `name`, `subject`, `academicClass`, `semester`, `instructor`, and
computed `courseStatus`.

`academicClass` is the sole nested Class field. Do not send or read `clazz` or
`academicClazz`; both are absent. Use only `codeContributionWeight`,
`testContributionWeight`, `documentContributionWeight`, and `researchContributionWeight`.
`designContributionWeight` is absent.

`courseStatus` is `OPEN` or `CLOSED`, computed by Backend from the Course Semester in
`Asia/Ho_Chi_Minh`, inclusively between `startDate` and `endDate`. Frontend must not
calculate the status using browser timezone. If the Semester, `startDate`, or `endDate`
is missing, Backend returns `CLOSED`; the read request does not fail merely due to a
legacy null Semester date.

### Request body

```ts
type SubjectRequest = {
  subjectCode: string; // non-blank, max 255
  name: string;        // non-blank, max 255
};

type ClassRequest = {
  classCode: string; // non-blank, max 255
  name: string;      // non-blank, max 255
};

type SemesterRequest = {
  code: string;      // non-blank, max 255
  name: string;      // non-blank, max 255
  startDate: string; // LocalDateTime JSON string
  endDate: string;   // LocalDateTime JSON string; must not be before startDate
};

type CourseRequest = {
  courseCode: string; // non-blank, max 255
  name: string;       // non-blank, max 255
  subjectId: string;  // UUID
  classId: string;    // UUID
  semesterId: string; // UUID
  instructorId: string; // UUID
};
```

`Subject`, `Class` và `Semester` là JPA entity response trực tiếp, có các field
base `id`, `createdAt`, `updatedAt` và các property domain tương ứng. `Course`
không trả trực tiếp entity: mọi response Course dùng DTO `CourseResponse` ổn định;
không bind UI vào storage field `clazz` hoặc `designContributionWeight`.

Các service xác định rõ `404` khi không tìm thấy entity liên quan, `409` khi
mã Subject/Class/Course/Semester trùng, và `400` khi `endDate` trước
`startDate`. Đây là `ResponseStatusException` từ service; chỉ dựa vào status
và message hiển thị được, không giả định error-body validation chi tiết.

`Page<T>` là response `org.springframework.data.domain.Page` trả trực tiếp,
không phải DTO tự định nghĩa. FE cần dùng `content` và pagination metadata mà
Swagger của môi trường đang chạy hiển thị, thay vì tự tạo một envelope khác.

---

## 7. Team Project và identity mapping review

### Danh sách thành viên Team

| Method | Path | Auth/authorization | Response |
| --- | --- | --- | --- |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/members?page=0&size=20` | ADMIN mọi Team; LECTURER chỉ Course mình dạy; STUDENT chỉ đúng Team mình là member, cả LEADER và MEMBER | `200 Page<TeamMemberResponse>`; `401` anonymous; `403` không đủ scope; `404` Team không có hoặc không thuộc Course URL |

`TeamMemberResponse` chỉ có `studentId`, `fullName`, `studentCode`, `roleInTeam`.
Không hiển thị email, `cognitoSub` hay version. UI phải gửi `courseId` và `teamId`
đúng quan hệ; endpoint là read-only nên không cần CSRF.

### Tạo Project cho team

| Method | Path | Quyền | Success |
| --- | --- | --- | --- |
| POST | `/api/teams/{teamId}/projects` | Team manager + CSRF | 201 `ProjectResponse` |

```ts
type CreateTeamProjectRequest = { name: string }; // non-blank, max 255
type ProjectResponse = { id: string; teamId: string; name: string };
```

Nếu team đã có project, backend trả `409` với code
`TEAM_PROJECT_ALREADY_EXISTS`.

### Identity mapping review

| Method | Path | Quyền | Success |
| --- | --- | --- | --- |
| GET | `/api/integrations/identity-mappings?studentId={uuid}` | Authorized reviewer | 200 `IdentityConnectionResponse[]` |
| PATCH | `/api/integrations/identity-mappings/{mappingId}` | Authorized reviewer + CSRF | 200 `IdentityConnectionResponse` |

```ts
type IdentityMappingReviewRequest = {
  action: "APPROVE" | "REJECT" | "CORRECT";
  correctedStudentId?: string; // required only for CORRECT
};

type IdentityConnectionResponse = {
  provider: "JIRA" | "GITHUB";
  status: "ACTIVE" | "DISCONNECTED" | "PENDING_REVIEW" | "REJECTED";
  displayName: string;
  email: string;
  verifiedAt: string | null;     // LocalDateTime
  disconnectedAt: string | null; // LocalDateTime
};
```

`CORRECT` thiếu `correctedStudentId` trả `400` code
`CORRECTED_STUDENT_REQUIRED`. Mapping không tồn tại trả `400`
`IDENTITY_MAPPING_NOT_FOUND`; mapping trùng khi sửa trả `409`
`IDENTITY_MAPPING_CONFLICT`.

---

## 8. Personal Jira/GitHub integration

Các route phần này đều cần session. Chỉ route `GET .../connect` dùng browser
navigation vì trả `302` đến OAuth provider.

| Method | Path | Success | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/me/integrations` | 200 `PersonalIntegrationsResponse` | Danh sách kết nối của chính user. |
| GET | `/api/me/integrations/jira/connect` | 302 | Dùng `window.location.assign`; bắt đầu Jira OAuth. |
| GET | `/api/integrations/jira/callback` | 302 | Provider callback; redirects browser to the configured frontend callback with an opaque `resultId` only. Do not call it manually. |
| DELETE | `/api/me/integrations/jira` | 200 | Controller trả `void` và không gắn `@ResponseStatus`; CSRF required. |
| GET | `/api/me/integrations/github/connect` | 302 | Dùng browser navigation; bắt đầu GitHub OAuth. |
| GET | `/api/me/integrations/github/callback` | 302 | Provider callback redirect with an opaque `resultId`; no direct JSON response. |
| DELETE | `/api/me/integrations/github` | 200 | Controller trả `void` và không gắn `@ResponseStatus`; CSRF required. |

```ts
type PersonalIntegrationsResponse = {
  connections: IdentityConnectionResponse[];
};
```

Jira callback có response phụ thuộc OAuth flow: personal flow trả
`IdentityConnectionResponse`; project flow trả `JiraAuthorizationResponse`.
FE không tự truyền `state`, `code` hoặc `error`: chúng là query parameters do
provider redirect trả về và được backend kiểm tra bằng `HttpSession` state.

**Historical behavior:** Jira/GitHub completion callbacks previously returned direct JSON after consuming session state. Current implementation redirects to frontend as documented below.
**CONFIRMED:** completion callback redirects to frontend with only opaque `resultId`; the frontend consumes the safe, read-once session result via the dedicated POST API.

Nếu Jira hoặc GitHub bị tắt ở backend, endpoint connect trả `503` với code
`INTEGRATION_NOT_CONFIGURED`. Consent bị từ chối/cancel trả `400`
`OAUTH_CONSENT_DENIED`.

---

## 9. Project Jira/GitHub integration và sync status

Mọi route trong phần này cần team-manager authorization. Các `connect`,
`install`, `setup` trả redirect nên dùng browser navigation. Với mutation dùng
CSRF utility.

### Project integration routes

| Method | Path | Success | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/projects/{projectId}/integrations` | 200 `ProjectIntegrationsResponse` | Trạng thái Jira/repository hiện tại. |
| GET | `/api/projects/{projectId}/jira/connect` | 302 | Bắt đầu Jira OAuth. |
| POST | `/api/projects/{projectId}/jira/link` | 200 `ProjectIntegrationsResponse` | Chỉ gọi sau project Jira OAuth callback thành công trong cùng browser session. |
| DELETE | `/api/projects/{projectId}/jira` | 204 | CSRF required. |
| GET | `/api/projects/{projectId}/github/install` | 302 | Bắt đầu GitHub App install. |
| GET | `/api/projects/{projectId}/github/setup` | 302 | GitHub setup callback route; provider/browser flow. |
| GET | `/api/projects/{projectId}/github/callback` | 302 | GitHub OAuth callback redirect with opaque `resultId`. |
| POST | `/api/projects/{projectId}/github/repositories` | 200 `ProjectIntegrationsResponse` | Liên kết repository và yêu cầu initial backfill. |
| DELETE | `/api/projects/{projectId}/github/repositories/{repositoryId}` | 204 | CSRF required. |
| GET | `/api/projects/{projectId}/sync-status` | 200 `SyncStatusResponse` | Tối đa 20 job mới nhất của Jira/repository thuộc project. |

Provider callback aliases có cùng session flow:

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/integrations/github/setup` | 302 |
| GET | `/api/integrations/github/project/callback` | 302 | Redirect with opaque `resultId`. |

### Request/response bodies

```ts
type JiraProjectLinkRequest = {
  cloudId: string;       // non-blank, max 255
  jiraProjectId: string; // Jira numeric project id hoặc project key, non-blank, max 255
};

type GitHubRepositoriesLinkRequest = {
  installationId: number; // positive integer
  repositoryIds: number[]; // non-empty; every entry positive; no duplicates
};

type JiraSiteResponse = {
  cloudId: string;
  name: string;
  siteUrl: string;
};

type JiraAuthorizationResponse = {
  projectId: string;
  sites: JiraSiteResponse[];
};

type GitHubRepositoryResponse = {
  repositoryId: number;
  fullName: string;
  defaultBranch: string;
  status: "CONNECTING" | "BACKFILLING" | "ACTIVE" | "DEGRADED" | "DISCONNECTED";
  lastSyncedAt: string | null; // LocalDateTime
};

type GitHubInstallationResponse = {
  projectId: string;
  installationId: number;
  accountLogin: string;
  accountType: string;
  repositories: GitHubRepositoryResponse[];
};

type ProjectIntegrationsResponse = {
  projectId: string;
  jira: {
    siteUrl: string;
    projectKey: string;
    status: "CONNECTING" | "BACKFILLING" | "ACTIVE" | "DEGRADED" | "DISCONNECTED";
    webhookExpiresAt: string | null; // LocalDateTime
    lastSyncedAt: string | null;     // LocalDateTime
  } | null;
  githubRepositories: GitHubRepositoryResponse[];
};

type SyncStatusResponse = {
  projectId: string;
  recentJobs: Array<{
    id: string;
    targetSystem: string;
    type: "JIRA_SYNC" | "GIT_SYNC" | "INITIAL_BACKFILL" | "RECONCILIATION" | "WEBHOOK_PROCESSING" | "OTHER";
    status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "PARTIAL_FAILURE" | "FAILED";
    startedAt: string | null;   // ISO-8601 Instant UTC, có Z
    completedAt: string | null; // ISO-8601 Instant UTC, có Z
    itemsProcessed: number | null;
    itemsFailed: number | null;
    errorCategory: string | null;
    failureStage: string | null;
  }>;
};
```

`jiraProjectId` được trim; backend chấp nhận numeric id hoặc key không phân biệt
hoa thường, rồi persist id/key canonical từ `JiraProjectInfo` provider. Sai site
hoặc không match id/key trả `409 JIRA_PROJECT_NOT_ACCESSIBLE` theo contract hiện có.

Ví dụ raw sync-status:

```json
{
  "startedAt": "2026-08-04T05:13:49Z",
  "completedAt": null
}
```

FE parse trực tiếp Instant backend trả về; không nối thêm `Z` và không cộng cứng
UTC+7:

```ts
export function formatSyncTimestamp(
  value: string | null | undefined,
): string {
  if (!value) return "N/A";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "N/A";

  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "medium",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}
```

`Asia/Ho_Chi_Minh` ở đây chỉ là timezone hiển thị UI. Endpoint, browser session,
CSRF, authorization và kiểu TypeScript `string | null` không đổi; không cần Bearer
token.

### UI flow được backend hỗ trợ

1. Jira: navigate đến `.../jira/connect` -> callback redirect về FE với
   `resultId` -> FE consume result bằng POST có CSRF để nhận safe
   `JiraAuthorizationResponse` gồm `sites` -> user chọn site/project -> POST
   `.../jira/link` với `cloudId`, `jiraProjectId` và CSRF -> poll
   `.../sync-status` nếu cần tiến độ backfill.
2. GitHub: navigate đến `.../github/install` -> GitHub setup/callback ->
   nhận `GitHubInstallationResponse.repositories` -> POST
   `.../github/repositories` với installation/repository IDs và CSRF -> poll
   `.../sync-status`.

Không đặt OAuth `state`, `code`, installation verification data hoặc webhook
data trong client state như một credential. Backend giữ OAuth state và grant
ngắn hạn trong `HttpSession`.

Một số status/code integration FE nên xử lý trực tiếp:

- `400`: lựa chọn/request OAuth không hợp lệ (ví dụ
  `GITHUB_INSTALLATION_INCOMPLETE`, `OAUTH_CONSENT_DENIED`).
- `403`: không phải team manager hoặc user GitHub không có quyền installation.
- `409`: project/site/repository/installation không ở trạng thái liên kết hợp
  lệ (ví dụ `JIRA_PROJECT_ALREADY_LINKED`, `GITHUB_REPOSITORY_ALREADY_LINKED`).
- `502`: provider tạm thời không khả dụng hoặc response provider không hợp lệ.
- `503 INTEGRATION_NOT_CONFIGURED`: integration đã bị tắt trên môi trường đó.

`sync-status` chỉ báo trạng thái sync của backend; `COMPLETED` với `0/0` nghĩa
job hoàn tất nhưng không xử lý item nào. Dùng `errorCategory` và `failureStage`
để hiển thị diagnostic an toàn, không hiển thị như provider payload.

---

## 10. Webhook API: provider-only, không gọi từ frontend

| Method | Path | CSRF | Response |
| --- | --- | --- | --- |
| POST | `/api/webhooks/github` | Exempt | `200 {"status":"PING"}` cho ping, ngược lại `202 WebhookAcceptedResponse` |
| POST | `/api/webhooks/jira` | Exempt | `202 WebhookAcceptedResponse` |

GitHub gửi payload raw cùng `X-Hub-Signature-256`, `X-GitHub-Delivery`,
`X-GitHub-Event`. Jira gửi payload raw với query `token` hoặc header
`Authorization`, và có thể `X-Atlassian-Webhook-Identifier`. Các endpoint này
được Spring Security public để provider giao hàng; signature/secret validation
do backend thực hiện. FE không gọi, không replay payload và không cần biết các
header/secret đó.

---

## 11. Checklist triển khai FE

1. Đặt `VITE_API_BASE_URL` theo local/production.
2. Bảo đảm origin FE có trong `FRONTEND_ORIGINS` backend.
3. Login bằng `window.location.assign`, không bằng `fetch`.
4. Sau callback/login, gọi `/api/auth/me` để lấy profile, rồi gọi
   `/api/auth/csrf` với cookies để lấy CSRF token JSON vào memory.
5. Dùng `apiRequest` cho mọi API; đặt `requireCsrf: true` với mutation.
6. Khi nhận 401, đưa user về flow login; khi 403, hiển thị no-permission UI
   hoặc refresh CSRF đúng một lần trước khi retry mutation.
7. Với OAuth provider connect/install, luôn browser-navigate và để callback
   quay lại cùng browser session.
8. Lấy contract response thực tế từ Swagger khi Springdoc bật, đặc biệt với
   các master-data endpoint trả JPA entity trực tiếp và `Page<T>`.

## 12. Course roster và lecturer options

| Method | Path | Quyền | Query/response |
| --- | --- | --- | --- |
| GET | `/api/v1/courses/{courseId}/students` | ADMIN mọi Course; LECTURER là instructor; anonymous 401; STUDENT/lecturer ngoài scope 403; Course thiếu 404 | `keyword`, `hasTeam=all|with|without`, `sortBy=studentCode|fullName|email|teamName|projectName`, `sortDirection=asc|desc`, `page`, `size` → `studentsWithTeam`/`studentsWithoutTeam` pages |
| GET | `/api/v1/courses/instructors` | ADMIN; anonymous 401; LECTURER/STUDENT 403 | `keyword` chỉ trên fullName/email, `sortBy=fullName|email`, `sortDirection=asc|desc`, `page`, `size` → `Page<LecturerOptionResponse>` |
| GET | `/api/me/courses/{courseId}/team/members` | STUDENT-only; anonymous 401; ADMIN/LECTURER 403 | backend tự resolve team; `page`, `size` → `MyCourseTeamMembersResponse`; 404 Course/no Team, 409 legacy nhiều Team |

Cả hai là GET, cần browser session nhưng không cần CSRF. Giá trị filter/sort không
hợp lệ trả 400. Roster filter/sort trước pagination; metadata được tính trên toàn bộ
tập sau filter và tie-break ổn định theo id. Roster chỉ dùng `TeamMember -> Team ->
Course` làm bằng chứng Student thuộc Course; invitation outbox không phải enrollment
source. `studentsWithoutTeam` và `hasTeam=without` theo contract phải rỗng vì chưa có
quan hệ enrollment Student–Course độc lập. Current baseline còn source drift đọc outbox
và fail contract test DEC-023; FE không được dựa vào behavior này hay quảng bá nhánh
`without` như feature đầy đủ.

Business rule đã được Product Owner chốt: Student có thể thuộc nhiều Course nhưng
tối đa một Team trong mỗi Course; role và Project độc lập theo Team/Course. Legacy
invalid data nhiều Team cùng Course có thể được đọc mà roster không crash, nhưng đó
không phải behavior hợp lệ. Roster trả email Student cho ADMIN/Lecturer owner;
lecturer options trả email Lecturer cho ADMIN. Actor ngoài scope bị authorization
chặn. Business/UI justification cho hai email field vẫn TBD; không response nào trả
`cognitoSub`, version, session, token hoặc credential.

### Student self-scoped team trong Course

FE dùng endpoint này khi Student cần xem Team/Course hiện tại mà chưa biết `teamId`:

```ts
type MyCourseTeamMembersResponse = {
  courseId: string;
  teamId: string;       // backend tự resolve; FE không gửi teamId
  teamName: string;
  roleInTeam: "LEADER" | "MEMBER" | "MENTOR";
  project: { id: string; name: string } | null;
  members: Page<TeamMemberResponse>;
};
```

`GET /api/me/courses/{courseId}/team/members?page=0&size=20` dùng browser
session, không cần CSRF và không nhận `studentId` hay `teamId`. Backend lấy Student
từ `SagaPrincipal.localProfileId`, query membership theo Student+Course rồi trả
resolved `teamId`; FE có thể dùng id đó cho flow Project/integration hiện có. MEMBER
và LEADER đều xem được team của mình; quyền tạo Project vẫn là rule riêng.

`404` nghĩa Course không tồn tại hoặc Student chưa có Team trong Course. `409` nghĩa
dữ liệu legacy không hợp lệ có nhiều Team cho cùng Student/Course; FE không tự chọn
một Team để retry. Response và từng member không có email, `cognitoSub`, version,
session, CSRF, token hay credential.

## Độ tin cậy GitHub sync (2026-08-04)

Không có endpoint, `SyncJobStatus` hay request/response mới cho FE. Initial
backfill, scheduler reconciliation và webhook-triggered reconciliation được
backend coalesce theo repository; repository khác vẫn sync song song. Backend tự
finalize/recover job stale, nên FE chỉ poll `/sync-status` và hiển thị status trả
về; FE không tự đổi `IN_PROGRESS` thành `FAILED`. Runtime recovery production vẫn
**TBD** tới khi quan sát sau deploy. OAuth callback/resultId, browser session,
CSRF và webhook contract không đổi.

## OAuth callback result handoff (2026-08-04)

After the provider returns to SAGA, each completion callback returns `302` to the configured frontend integration callback route. The URL contains exactly one query field, `resultId`; it never includes OAuth `code`, `state`, token, provider payload, secret or session id.

Frontend keeps the browser `JSESSIONID`, reads `resultId`, obtains CSRF using the existing `/api/auth/csrf` flow, then calls `POST /api/integrations/callback-results/{resultId}/consume` with `X-XSRF-TOKEN`. The response is safe `IntegrationCallbackResultResponse`: provider, `PERSONAL`/`PROJECT`, nullable project id, success plus exactly one relevant safe response (`IdentityConnectionResponse`, `JiraAuthorizationResponse`, or `GitHubInstallationResponse`) or safe `errorCode`/`message`. It is read-once; missing, expired, consumed or wrong-session values return a controlled unavailable error.

Deployment: `INTEGRATION_CALLBACK_REDIRECT_URI` is a required absolute HTTP(S) frontend URI with no userinfo/query/fragment. `INTEGRATION_CALLBACK_RESULT_TTL` defaults to `PT5M`; neither replaces `AUTH_SUCCESS_REDIRECT_URI`.

### Jira labels status

Jira labels are fetched and stored only as an internal Task snapshot. There is
now a Task list/detail contract exposing `labels`, plus SAGA write-through APIs
for creating and updating Jira tasks. This paragraph supersedes the pre-2026-08-06
PARTIAL status. Labels remain classification data, not the Jira issue id/key used
to identify a Task.

## Project Sprint list API

Hai endpoint list dưới đây dùng `JSESSIONID`, `credentials: "include"`; đều là GET
nên không cần CSRF và không có pagination. Sprint detail/mutation được mô tả trong
mục “Jira Task và Sprint Management — 2026-08-06” phía trên:

| Method | Exact path | Role annotation | Effective service access | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/sprints` | ADMIN, LECTURER, STUDENT | ADMIN: mọi Project; LECTURER: instructor của Course; STUDENT: có Team membership trong cùng Course với Project | `200 SprintListResponse`; Project không tồn tại `404` |
| GET | `/api/v1/teams/{teamId}/sprints` | ADMIN, LECTURER, STUDENT | ADMIN: mọi Team; LECTURER: instructor của Course; STUDENT: thành viên đúng Team | `200 SprintListResponse`; Team/Team Course không tồn tại hoặc Team chưa có Project `404` |

Danh sách luôn sort theo `startDate` tăng dần. Không có Sprint trả `sprints: []`
với `200`. Ở route theo Project, `teamId` nullable nếu không resolve được Team.

```ts
type SprintSummaryResponse = {
  sprintId: string;
  sprintName: string;
  externalSprintId: string | null;
  startDate: string | null;
  endDate: string | null;
  goal: string | null;
};

type SprintListResponse = {
  projectId: string;
  teamId: string | null;
  sprints: SprintSummaryResponse[];
};
```

## Peer Review và default rubric API

| Method | Exact path | Role annotation | Effective service access | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/peer-review-rubrics/default` | Không có annotation riêng | Mọi authenticated session; global rubric được ưu tiên, Subject rubric chỉ dùng khi không có global | `200 PeerReviewDefaultRubricResponse` |
| GET | `/api/v1/teams/{teamId}/peer-review-rubric` | Không có annotation riêng | ADMIN; LECTURER là Course instructor; STUDENT thuộc Team | `200 PeerReviewRubricResponse` |


## Admin Course progress overview M5

| Method | Route | Quyền | Query | Kết quả |
|---|---|---|---|---|
| GET | `/api/admin/course-progress-overview` | ADMIN session, không CSRF | `keyword`, `semesterId`, `lecturerId`, `page`, `size` | `200 Page<AdminCourseProgressOverviewResponse>` |

Mỗi row gồm Course, lecturer summary và các count local: `teamCount`, `studentCount`,
`projectCount`, `sprintCount`, `activeSprintCount`, `closedSprintCount`, `peerReviewCount`.
Đây không phải grade, completion percentage, assessment finalized hay contribution snapshot.

## Admin Course report export M6

| Method | Route | Quyền | Response |
|---|---|---|---|
| GET | `/api/admin/reports/courses/{courseId}/export` | ADMIN session, không CSRF | attachment XLSX `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |

FE tải body như file; filename do server tạo `course-report-{safe-course-code}.xlsx`.
Workbook chỉ là local current snapshot, không phải bảng điểm/finalization. Không có email,
Cognito subject, comment Peer Review hoặc dữ liệu provider/credential.
| GET | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates` | ADMIN, STUDENT | Service thực tế chỉ chấp nhận STUDENT thuộc Team; ADMIN bị `403` | `200 PeerReviewCandidatesResponse` |
| POST | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews` | ADMIN, STUDENT | Service thực tế chỉ chấp nhận STUDENT thuộc Team; ADMIN bị `403` | `200 PeerReviewResponse` |
| GET | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews` | ADMIN, LECTURER, STUDENT | ADMIN; LECTURER là Course instructor; STUDENT thuộc Team | `200 SprintPeerReviewResponse` |

Candidates dùng `alreadyReviewed`, `existingReviewId` và
`existingTotalStarRating`. Submit là upsert theo `(sprint, reviewer, reviewee)`;
self-review và cross-team bị từ chối `400`. Student hiện đọc được toàn bộ reviews
của Team/Sprint, không chỉ review của chính mình. Đây là behavior/known risk hiện
tại, không phải bảo đảm về anonymity.

POST submit cần CSRF. Ví dụ tối thiểu:

```ts
const csrf = await fetch("/api/auth/csrf", {
  credentials: "include",
}).then((response) => response.json());

const response = await fetch(
  `/api/v1/teams/${teamId}/sprints/${sprintId}/peer-reviews`,
  {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(request),
  },
);
```

Không gửi `Authorization` header cho application API. Chi tiết request/response
Peer Review nằm trong `PEER_REVIEW_API_EXAMPLE.md`.

## Contribution API

Contribution API dùng session. Tất cả mutation phải gửi CSRF; GET không cần CSRF.

| Method | Exact path | Role annotation | Effective behavior | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/teams/{teamId}/contribution-evaluation` | LECTURER, STUDENT | LECTURER chỉ Course mình phụ trách; STUDENT chỉ exact `RoleInTeam.LEADER` của chính Team. ADMIN/MEMBER/MENTOR/cross-Team Leader 403 | `200 TeamContributionEvaluationResponse` |
| GET | `/api/v1/teams/{teamId}/contribution-graph` | LECTURER, STUDENT | Cùng quyền evaluation. Query `sprintId` tùy chọn: có thì flowchart đúng Sprint (404 nếu không thuộc Project); không có thì cả Project. ADMIN/MEMBER/MENTOR 403 | `200 TeamContributionGraphResponse` |
| POST | `/api/v1/teams/{teamId}/contribution-override` | ADMIN, LECTURER | ADMIN mọi Team; LECTURER phải là Course instructor | `200 ContributionOverrideResponse` |
| GET | `/api/v1/courses/{courseId}/contribution-slice-weights` | ADMIN, LECTURER | ADMIN mọi Course; LECTURER chỉ exact Course instructor. Actor từ `SagaPrincipal` | `200 CourseContributionSliceWeightResponse` |
| PUT | `/api/v1/courses/{courseId}/contribution-slice-weights` | LECTURER | Official new FE mutation. Exact Course instructor; no `lecturerId`. Other Course / STUDENT / ADMIN 403. CSRF required. Scale 0–100, sum 100 ± 0.01. `{codeWeight,testWeight,documentWeight,researchWeight}`. Only authoritative while Course is in `COURSE` mode | `200 CourseContributionSliceWeightResponse` |
| PUT | `/api/v1/courses/{courseId}/contribution-config-mode` | LECTURER | Exact Course instructor, CSRF required. `{"mode":"COURSE"\|"TEAM"}`. Activating `TEAM` requires every current Team to already have a valid `group-weights` override (atomic; 409 `TEAM_MODE_CONFIGURATION_INCOMPLETE` if any is missing) | `200 ContributionConfigModeResponse` |
| GET | `/api/v1/courses/{courseId}/contribution-team-weights` | ADMIN, LECTURER | Team-menu read: current mode + effective weights + source per Team | `200 CourseTeamContributionWeightsResponse` |

Luồng gửi đơn / Admin duyệt trọng số đã gỡ: không còn
`POST .../contribution-slice-weight-requests`, `GET .../contribution-slice-weight-requests`,
hay `PUT .../contribution-slice-weight-requests/{requestId}/decision`. Lecturer sửa trọng số trực tiếp.

Contribution evaluation và Lecturer direct Course-weight PUT đã bind actor từ `SagaPrincipal.localProfileId`; FE không gửi actor ID
và backend 403 là authority. Evaluation chỉ là current aggregate; không hiển thị nó như
historical committed snapshot.

Leader gọi:

```ts
fetch(`/api/v1/teams/${teamId}/contribution-evaluation`, {
  credentials: "include",
});
```

GET không cần CSRF và không dùng Bearer. FE có thể render `member.fullName`,
`member.studentCode`, `member.finalContributionPercentage` cùng các metric Contribution hiện hữu.
Response không có email, Cognito subject, reviewer/comment, token, credential hoặc raw provider payload.

## Lecturer Analytics read APIs — 2026-08-05

Giới hạn semantic bắt buộc khi FE hiển thị:

- Activities chỉ tổng hợp Commit và Document.
- Contribution Detail là current aggregate, không phải lịch sử theo Sprint.
- Early Warning hiện chỉ có signal deterministic `OVERDUE_TASK`, không phải AI.
- Interaction chỉ dựa trên Peer Review, không gồm pull request hay Jira comment.
- Heatmap chỉ dựa trên Commit và không trả level 1–4.
- Velocity dùng `currentPlannedPoints`, không phải committed snapshot đầu Sprint.

Tất cả endpoint dưới đây dùng browser session `JSESSIONID`: frontend gọi với
`credentials: "include"`. Đây đều là GET nên không gửi CSRF header và không dùng
Bearer token. ADMIN xem mọi Course; LECTURER chỉ xem Course mà mình là instructor;
anonymous nhận 401.

**STUDENT:** default 403 trên Lecturer Analytics. Ngoại lệ hiện hành:

- Graph routes (DEC-080): overview / heatmap / interactions / burndown — LEADER hoặc MEMBER của exact Team.
- Progress (DEC-083): `GET .../students/{studentId}/progress` — MEMBER self; LEADER exact same Team; MENTOR 403.

Không mở STUDENT cho activities, contribution-detail, early-warnings hay Lecturer Dashboard.

| Path | Query | Response chính |
|---|---|---|
| `GET /api/v1/courses/{courseId}/teams/{teamId}/detail` | `page=0&size=20`, size 1..100 | `TeamDetail`; `project` nullable, `project.repositories` là danh sách GitHub repository local, `members` là Spring `Page<TeamMemberResponse>` |
| `GET /api/v1/courses/{courseId}/students/{studentId}/progress` | — | task assigned theo Project, DONE/total completion, Commit count, `TaskType` distribution và unclassified count |
| `GET /api/v1/courses/{courseId}/students/{studentId}/activities` | `page=0&size=10` | `StudentActivities`; Commit/Document mới nhất, sort timestamp giảm dần rồi type/sourceId |
| `GET /api/v1/courses/{courseId}/students/{studentId}/contribution-detail` | — | aggregate Contribution hiện tại của Student; không phải lịch sử Sprint |
| `GET /api/v1/courses/{courseId}/early-warnings` | — | `OVERDUE_TASK` deterministic; `severity` null vì chưa có rule |
| `GET /api/v1/courses/{courseId}/teams/{teamId}/interactions` | — | nodes roster và directed Peer Review edges thật |
| `GET /api/v1/courses/{courseId}/teams/{teamId}/heatmap` | `startDate`, `endDate` bắt buộc; `studentId` tùy chọn | inclusive UTC days, Commit counts, có row zero, không có level |
| `GET /api/v1/courses/{courseId}/teams/{teamId}/sprints/velocity` | — | current task counts/points, DONE points, null-point count, official `BUG` count |

Mọi URL có Team/Student đều kiểm quan hệ lồng nhau với `courseId`; ID tồn tại ở
Course khác không làm lộ dữ liệu. Recent Activities không dựng Jira transition event
vì database chỉ giữ trạng thái hiện tại.

`TeamDetail.project` giữ nguyên `null` khi Team chưa có Project. Khi có Project, shape additive là
`{id,name,repositories:[{repositoryId,repositoryName}]}`; chưa link repository thì `repositories: []`.
Project hỗ trợ nhiều GitHub repository nên FE phải render/chọn từ toàn bộ danh sách, không lấy phần tử đầu
làm mặc định canonical. `repositoryId` là GitHub provider ID kiểu số (`Long`/OpenAPI `int64`), dùng trực
tiếp cho path `/api/projects/{projectId}/github/repositories/{repositoryId}/branches|commits` và query
`repositoryId` của Issue list. `repositoryName` là tên an toàn dạng `owner/name` đã persist. Team detail
chỉ đọc local DB theo `fullName`, rồi `repositoryId` để giữ thứ tự deterministic; không gọi GitHub provider
và không trả URL, installation, token hay credential. Authorization session hiện hữu không đổi: ADMIN theo
scope hiện tại, LECTURER đúng Course, STUDENT 403.
# P1 — Contract response/error (2026-08-07)

`GET /api/v1/teams/{teamId}/sprints` trả thêm `state` (additive):

- `PROJECT_NOT_CREATED`: Team đã truy cập được nhưng chưa có Project; `projectId: null`, `sprints: []` và HTTP 200.
- `EMPTY`: Project có nhưng chưa có Sprint; `sprints: []` và HTTP 200.
- `READY`: có Sprint; HTTP 200.

Team không tồn tại trả HTTP 404 với `error: "TEAM_NOT_FOUND"`. Generic error JSON dùng `timestamp`, `status`, `error`, `message`, `path`; FE phân nhánh bằng `status` và `error`, không parse `message`. Session/CSRF không đổi.

## Admin global user import M7

`POST /api/admin/users/import` chỉ ADMIN browser session. FE lấy CSRF qua
`GET /api/auth/csrf`, gửi cookie session và header CSRF; không bearer. Request là
`multipart/form-data` với `role` enum `STUDENT` hoặc `LECTURER` và `file` `.xlsx`.
Workbook không có cột role.

| role | Header exact theo thứ tự | New profile | Reuse |
|---|---|---|---|
| `STUDENT` | `studentCode,email,fullName` | PENDING, không Cognito subject | chỉ khi email + code cùng Student |
| `LECTURER` | `email,fullName` | ACTIVE, không Cognito subject | chỉ khi email là Lecturer hiện hữu |

Success 200: `{ "role": "STUDENT", "createdCount": 1, "reusedCount": 0 }`. Không trả
email, studentCode, profile id hay row error. Invalid file/schema/role 400; partial hoặc
cross-profile identity 409; anonymous 401; sai role/CSRF 403. Không tạo Course, Team,
membership, invitation hay Cognito account.

## Admin active Semester setting M8A

FE ADMIN lấy setting bằng `GET /api/admin/settings/active-semester` và đặt bằng `PUT` cùng
route. Cả hai dùng `credentials: "include"`; PUT lấy CSRF từ `/api/auth/csrf` và gửi
`X-XSRF-TOKEN`. Không dùng bearer.

```json
{ "semesterId": "uuid" }
```

`200` trả `{ semesterId, semesterCode, semesterName, startDate, endDate }`; tất cả field
Semester có thể null khi chưa cấu hình. `404` cho Semester missing/tombstone; anonymous 401;
non-ADMIN hoặc CSRF sai 403; không có date override. Giá trị này chỉ là hint để FE điền/filter:
backend không tự đổi Course hoặc áp global filter.

## Course student import I1 — 2026-08-09

FE gửi `POST /api/v1/courses/{courseId}/import-students` với `multipart/form-data`, field duy nhất `file`. Dùng `credentials: "include"`; lấy CSRF qua `/api/auth/csrf` và gửi `X-XSRF-TOKEN`. Không dùng `Authorization: Bearer`. ADMIN import mọi Course; LECTURER chỉ Course mình dạy; STUDENT 403, anonymous 401.

FE phải tạo XLSX (không CSV) tối đa 1 MiB, tối đa 1.000 row data, sheet đầu tiên có chính xác các cột theo thứ tự `Class,RollNumber,Email,MemberCode,FullName,Group,Leader`. Không dùng formula ở bất kỳ cột nào; `RollNumber`, `Email`, `FullName` không được blank. Không có preview/validate/template endpoint, vì vậy FE validate UX cục bộ chỉ là hỗ trợ và không thay backend validation.

Success vẫn là plain text `Import danh sách sinh viên thành công!` (200), không đổi sang DTO. FE map lỗi 400 theo error code `MALFORMED_WORKBOOK`, `FILE_TOO_LARGE`, `INVALID_HEADER`, `FORMULA_NOT_ALLOWED`, `INVALID_ROW`, `DUPLICATE_IN_FILE`, `ROW_LIMIT`; 409 `IDENTITY_CONFLICT` hoặc `COURSE_TEAM_MEMBERSHIP_CONFLICT`. Không render raw provider/workbook/cell content; import không partial success.

## D1 Browser session và CSRF — 2026-08-09

FE giữ `credentials: "include"`, không Bearer. Sau khi Cognito redirect hoàn tất, dùng `GET /api/auth/me` để đọc safe session identity; lấy token hiện hành qua `GET /api/auth/csrf`, rồi gửi `X-XSRF-TOKEN` cho POST/PUT/PATCH/DELETE cùng cookies. Không dùng `/auth/callback` để bắt đầu login và không giả định FE phải đọc cookie cross-origin bằng `document.cookie`.

Backend chỉ cho origin explicit từ `FRONTEND_ORIGINS`; credentials=true, CORS cho `Content-Type`, `X-XSRF-TOKEN` và `Idempotency-Key`, không wildcard. `POST /api/auth/logout` cần CSRF, xóa local session/cookies rồi redirect Cognito; không có GET logout. Browser/Cognito/Railway E2E vẫn TBD: FE cần smoke test thật sau deploy cho login, `/me`, `/csrf`, mutation và logout.

## Admin managed users và audit time — 2026-08-09

`GET /api/admin/users` chỉ trả `STUDENT` và `LECTURER`; FE không tự lọc Admin. `role=ADMIN` hiện trả page rỗng. System stats là metric độc lập nên total profile vẫn có Admin.

`GET /api/admin/audit-logs` trả `timestamp` theo ISO-8601 UTC, ví dụ `2026-08-09T16:30:00Z`. FE phải parse bằng `new Date(timestamp)` rồi format bằng `Intl.DateTimeFormat`; khuyến nghị `timeZone: "Asia/Ho_Chi_Minh"`. Không cắt chuỗi timestamp hoặc cộng `+7` thủ công.

## Agent TASK_CREATE into Sprint — Confirm recovery (DEC-100) — 2026-08-17

Public operation and schema are unchanged: `POST /api/v1/ai/pending-actions/{actionId}/confirm` still has no body. `PUBLIC_CONFIRM_BEHAVIOR_CHANGED=YES` only for this recovery retry.

Normal Confirm: one click, atomic claim. FE still disables a second Confirm for `TASK_UPDATE` and for Task create without Sprint.

Composite create-into-Sprint: one Confirm. Backend may create the Task then move it with the existing sprint write. If Confirm returns `409 JIRA_WRITE_RECOVERY_REQUIRED` or `409 JIRA_WRITE_OPERATION_IN_PROGRESS`, Jira may already have moved the issue; local list filters must not be treated as “still Backlog”. Retry the same Confirm so Backend can canonical-recover with the same sprint idempotency key. Do not invent a 409+Task shape. Do not send Bearer or identity fields.

## J1F Task Sprint recovery — 2026-08-10

Với `PUT /api/v1/projects/{projectId}/tasks/{taskId}/sprint`, FE gửi đúng một trong `sprintId` hoặc `backlog=true` và giữ nguyên `Idempotency-Key` khi retry cùng intent. Sau remote success, backend chỉ canonical recover và xác nhận Task local phản ánh Sprint/backlog trước khi trả success; FE không tạo key mới hay gửi mutation khác để “sửa” trạng thái.

Runtime DEMO-24 xác nhận `TASK_SPRINT=REMOTE_SUCCEEDED` với remote `10026`/`DEMO-24` nghĩa là Jira move đã xảy ra. FE không suy diễn cần gửi move lần hai từ 409 cũ; sau deploy J1F, retry cùng request/key là target-aware recovery an toàn.

## J1D Task Create recovery — 2026-08-10

Khi `POST` Task trả `409 JIRA_WRITE_RECOVERY_REQUIRED`, FE không tự động retry, polling hay tạo `Idempotency-Key` mới. Nếu có luồng gửi lại đã được product chấp thuận, phải giữ nguyên key và request để backend chỉ canonical recovery, không Jira POST mới. Backend chỉ trả success sau fresh local canonical confirmation; failure sau remote success vẫn là recovery state, không phải tín hiệu tạo issue mới.
## Student Course Invitation email — 2026-08-11

Không có endpoint/request/response/session/CSRF mới cho FE. Backend gửi email sau Course import/manual membership flow qua outbox hiện hữu. CTA của linked Student là `Đăng nhập SAGA`; unlinked Student là `Đăng ký / Kích hoạt tài khoản SAGA`; cả hai trỏ tới deployment URL cấu hình bởi `STUDENT_INVITATION_LOGIN_URL`. FE không dựng OAuth callback từ email, không nhận invite token/password/provider credential và không suy đoán Google là phương thức đăng nhập bắt buộc.

Source/test đã xác nhận template và outbox state machine; delivery Gmail production vẫn **TBD_DEPLOYMENT_SMOKE**. Khi smoke, FE chỉ cần xác nhận CTA mở đúng entry URL và browser session/login flow hiện hữu tiếp tục hoạt động.
## Notification Bell and Firebase Installation API — 2026-08-11

All calls use the existing browser OIDC session. Do not send Bearer tokens or owner/profile/role fields. Send the existing CSRF header/cookie on POST, PATCH, and DELETE.

- `GET /api/me/notifications?page=0&size=20` — newest-first owned page; `size` is 1..100. Each item has `id`, `type`, `title`, `message`, nullable `actionUrl`, `read`, nullable `readAt`, and `createdAt`.
- `GET /api/me/notifications/unread-count` — returns `{ "unreadCount": 0 }`.
- `PATCH /api/me/notifications/{notificationId}/read` — idempotently marks an owned notification read; a foreign/missing ID returns 404.
- `POST /api/me/firebase-installations` with `{ "firebaseInstallationId": "<browser-fid>" }` — registers or reactivates the current browser FID. Same-owner replay is idempotent; a FID owned by another principal returns 409.
- `DELETE /api/me/firebase-installations/{installationId}` — idempotently revokes an owned installation; foreign/missing IDs return 404.

The client must obtain a Firebase Installation ID, not an FCM registration token. Register after authenticated session establishment and revoke the returned installation UUID on logout/device opt-out when practical. Notification list/read state always comes from SAGA APIs; FCM only prompts immediate refresh/display. Manual broadcast is available through the endpoints documented below.
## Manual notification broadcast — 2026-08-11

All requests use `credentials: "include"`, existing `X-XSRF-TOKEN`, and an `Idempotency-Key` header. Do not use Bearer. Do not send actor/sender/recipient IDs, FIDs, provider data, or Cognito IDs. Admin must not send `actionUrl`/`url`/`link`. Lecturer may send optional HTTPS `actionUrl` only (DEC-098).

| Endpoint | Role | Body | Result |
| --- | --- | --- | --- |
| `POST /api/admin/notifications/broadcast` | ADMIN | `{ "audience": "STUDENTS" | "LECTURERS" | "ALL_USERS", "title": "...", "message": "..." }` | 200 broadcast id, scope, safe counts, status |
| `POST /api/v1/courses/notifications/broadcast` | LECTURER | `{ "courseIds": ["<course-uuid>"], "title": "...", "message": "...", "actionUrl"?: "https://..." }` | 200 COURSE_STUDENTS broadcast counters/status |

Title is max 160 and message max 1000; both must be nonblank plain text. Same `Idempotency-Key` plus same intent replays; reuse for different content/scope/link returns 409. Lecturer duplicate Course IDs are normalized. A non-owned/missing Course fails the entire request before any notification is created. ALL_USERS currently means Student + Lecturer only; Admin inclusion and AccountStatus filtering are not available UI controls. UI label “ALL” maps to `ALL_USERS`.

Responses contain `broadcastId`, `audience`, `status`, `recipientCount`, `notificationCount`, `deliveryQueuedCount`, and `completedAt`; they never contain FID, email, recipient IDs, credentials, provider output or Cognito subject. Bell list/unread/read and Firebase FID registration remain as documented below.

## Automatic Jira Task and Sprint notifications — 2026-08-11

No new FE endpoint exists. Existing Task/Sprint mutation requests retain browser session, CSRF, and Idempotency-Key rules; Bell items appear only after backend canonical completion. Due-date reminders are calendar Tomorrow/Today/Overdue only, never 3-hour/24-hour alarms.

Task Bell types are `TASK_CREATED`, `TASK_UPDATED`, `TASK_ASSIGNEE_CHANGED`, `TASK_SPRINT_CHANGED`, `TASK_ESTIMATION_CHANGED`, `TASK_STATUS_CHANGED`, and `TASK_DELETED`. Sprint types are `SPRINT_CREATED`, `SPRINT_UPDATED`, `SPRINT_STARTED`, `SPRINT_CLOSED`, and `SPRINT_DELETED`; deadline types are `TASK_DUE_TOMORROW`, `TASK_DUE_TODAY`, and `TASK_OVERDUE`.

Task recipients are the canonical assignee, or owning Team Students only when unassigned. Sprint recipients are owning Team Students. A Student who performs the mutation does not notify themself. The backend does not add an AccountStatus filter. `actionUrl` is currently null because no canonical internal FE Task/Sprint route is confirmed; FE must not substitute localhost, Railway, or Jira provider URLs.

FCM is only a prompt to refresh/display. A Bell item exists even with no active browser FID, and multiple FIDs do not create duplicate Bell items. Mutation HTTP responses/errors are unchanged; a notification persistence or Firebase failure does not turn a completed Jira mutation into an HTTP/provider rollback.

# Frontend integration quick start — 2026-08-11

Phần này là luồng tích hợp ngắn nhất cho FE/QA. Contract chi tiết của các domain cũ vẫn nằm ở các mục phía trên; không dùng nội dung này để suy ra endpoint mới.

## 1. Authentication model

- Đăng nhập bằng top-level browser navigation tới `GET /api/auth/login`; backend trả `302` vào Cognito.
- API nghiệp vụ dùng cookie `JSESSIONID`. Mọi `fetch`/Axios request phải dùng `credentials: "include"`.
- Không gửi `Authorization: Bearer` và không lưu Cognito/provider token ở FE.
- Sau login, gọi `GET /api/auth/me` để lấy profile, role và account status của session hiện tại.
- Gọi `GET /api/auth/csrf`, giữ token trong memory và gửi qua `X-XSRF-TOKEN` cho `POST`, `PUT`, `PATCH`, `DELETE`.
- `POST /api/auth/logout` là Spring Security route, cần CSRF và dùng browser form/navigation để theo redirect Cognito. Đây không phải controller JSON API.

## 2. Current user

| Khi nào FE gọi | API | Kết quả |
|---|---|---|
| Sau khi browser quay về từ login; khi hydrate auth state | `GET /api/auth/me` | Profile local, application role và account status của user hiện tại |
| Trước mutation hoặc khi token cần làm mới | `GET /api/auth/csrf` | Tên header, parameter và token CSRF của session |

Không truyền `userId`. Anonymous trả `401`; user đã đăng nhập nhưng không đủ quyền ở business API trả `403`.

## 3. Student Import và Invitation Email

| API | Role | Input | Response thành công |
|---|---|---|---|
| `POST /api/v1/courses/{courseId}/import-students` | ADMIN hoặc LECTURER phụ trách Course | `multipart/form-data`, field `file`, XLSX grouping template | `200 CourseStudentImportResponse` |
| `POST /api/v1/courses/{courseId}/admin-import-students-template` | ADMIN | `multipart/form-data`, field `file`, XLSX 5 cột | `200 CourseStudentImportResponse` |

Sau khi import/membership được lưu theo contract hiện tại, backend có thể enqueue invitation tương ứng. Email được xử lý bất đồng bộ qua outbox; lỗi cấu hình/provider hoặc gửi email không rollback import/TeamMember. `invitationsQueued` trong response là số event được enqueue, không phải xác nhận email đã tới inbox.

- Student đã có liên kết Cognito nhận mẫu đăng nhập.
- Student local chưa liên kết nhận hướng dẫn đăng ký/kích hoạt bằng đúng email nhận thư.
- FE không gọi Gmail API trực tiếp và không có endpoint `/send-mail` hay invitation-status API công khai. Việc backend chuyển provider từ SMTP sang Gmail REST API không đổi API/role/session/CSRF contract của FE.
- Trạng thái Gmail API/Railway gửi thật vẫn cần deployment smoke; không hiển thị “email đã gửi thành công” chỉ từ HTTP 200 của import.

## 4. Notification Bell

| Chức năng | API | Contract |
|---|---|---|
| Danh sách của tôi | `GET /api/me/notifications?page=0&size=20` | Newest-first; `page >= 0`, `1 <= size <= 100` |
| Số chưa đọc | `GET /api/me/notifications/unread-count` | `{ "unreadCount": number }` |
| Đánh dấu đã đọc | `PATCH /api/me/notifications/{notificationId}/read` | Cần CSRF; idempotent; foreign/missing ID trả `404` |

`NotificationResponse` gồm `id`, `type`, `title`, `message`, nullable `actionUrl`, `read`, nullable `readAt`, `createdAt`. SAGA DB là nguồn lịch sử và read-state chuẩn. FCM không thay thế các API này.

## 5. Firebase Web Push

1. FE cấu hình Firebase Web SDK bằng `firebaseConfig` public của đúng environment.
2. Lấy Firebase Installation ID theo client contract đang hỗ trợ; example tài liệu là `example-browser-fid`, không dùng FID thật.
3. Gọi `POST /api/me/firebase-installations` với:

```json
{
  "firebaseInstallationId": "example-browser-fid"
}
```

4. Giữ `id` UUID trong response để có thể gọi `DELETE /api/me/firebase-installations/{installationId}` khi user tắt push/xóa device registration.
5. Khi nhận foreground/background push, invalidate hoặc refetch Bell API; không coi payload FCM là canonical history.

Firebase Admin service account/private key tuyệt đối không đưa xuống FE. VAPID public key và service-worker deployment là cấu hình public theo environment; việc FCM Web nhận push thật vẫn là `TBD_DEPLOYMENT_SMOKE`. Backend hiện nhận trường tên `firebaseInstallationId`; không tự đổi sang registration-token contract ở FE.

## 6. Admin notification broadcast

`POST /api/admin/notifications/broadcast` — chỉ ADMIN, cần session, CSRF và `Idempotency-Key`.

```json
{
  "audience": "STUDENTS",
  "title": "Thông báo lịch bảo trì",
  "message": "Hệ thống tạm ngừng lúc 22:00."
}
```

`audience` chỉ nhận `STUDENTS`, `LECTURERS`, `ALL_USERS`. UI “Tất cả” / “ALL” phải gửi `ALL_USERS`, không gửi `ALL`. `ALL_USERS` hiện là toàn bộ Student + Lecturer local; không gồm Admin. Title tối đa 160, message tối đa 1000, đều là plain text không chứa `<` hoặc `>`. Admin request không được gửi `actionUrl`, `url`, `link` hay field lạ — Backend fail-closed unknown properties và trả `400 INVALID_REQUEST`. Ẩn/disable link field trên màn Admin.

Response 200 gồm `broadcastId`, `audience`, `status`, `recipientCount`, `notificationCount`, `deliveryQueuedCount`, `completedAt`. Cùng key + cùng intent trả lại kết quả; tái sử dụng key cho nội dung/scope khác trả `409`.

## 7. Lecturer Course notification

`POST /api/v1/courses/notifications/broadcast` — chỉ LECTURER, cần session, CSRF và `Idempotency-Key`.

```json
{
  "courseIds": ["11111111-1111-1111-1111-111111111111"],
  "title": "Nhắc lịch demo",
  "message": "Các nhóm chuẩn bị demo vào thứ Sáu.",
  "actionUrl": "https://example.com/resource"
}
```

`actionUrl` là optional. Bỏ field hoặc gửi blank/null khi không có link. Khi có giá trị phải là HTTPS tuyệt đối, tối đa 500 ký tự. FE không gửi `http`, `javascript:`, `data:`, `file:` hay URL malformed. Cùng `Idempotency-Key` với URL khác là intent khác (`409`).

- `courseIds` có 1–100 phần tử; duplicate Course ID được normalize.
- Mọi Course phải active và do Lecturer hiện tại phụ trách. Missing Course trả `404`; Course ngoài scope trả `403`; toàn request dừng trước fanout.
- Người nhận chỉ lấy từ distinct `TeamMember` của các Course được duyệt; Student trùng nhiều Course chỉ nhận một Bell item cho broadcast.
- Response dùng audience `COURSE_STUDENTS` và cùng bộ counter/status như Admin broadcast.

## 8. Automatic notifications

FE không gọi send API cho các event dưới đây. FE chỉ gọi business API Task/Sprint/Integration/Import bình thường; backend tạo Bell sau confirmed success.

| Event | FE gọi send API? | Người nhận | Trigger | Notification type | Action URL |
|---|---|---|---|---|---|
| Thêm membership Course | Không | Student vừa có TeamMember | Membership mới được persist | `COURSE_MEMBERSHIP_ADDED` | `null` |
| Liên kết Jira cá nhân thành công | Không | User khởi tạo | Callback xác nhận liên kết đã lưu | `JIRA_LINK_SUCCEEDED` | `null` |
| Liên kết GitHub cá nhân thành công | Không | User khởi tạo | Callback xác nhận liên kết đã lưu | `GITHUB_LINK_SUCCEEDED` | `null` |
| Liên kết Jira Project thành công | Không | User khởi tạo | Jira board link được persist | `JIRA_PROJECT_LINK_SUCCEEDED` | `null` |
| Liên kết GitHub Project thành công | Không | User khởi tạo | GitHub installation/repository link được persist | `GITHUB_PROJECT_LINK_SUCCEEDED` | `null` |
| Task create | Không | Assignee; nếu chưa assign thì Team sở hữu Project | Jira write `COMPLETED` | `TASK_CREATED` | `null` |
| Task update | Không | Như trên | Jira write `COMPLETED` | `TASK_UPDATED` | `null` |
| Task assignee | Không | Assignee mới; nếu chưa assign thì Team | Jira write `COMPLETED` | `TASK_ASSIGNEE_CHANGED` | `null` |
| Task sprint | Không | Assignee hoặc Team | Target Sprint/backlog được canonical-confirm | `TASK_SPRINT_CHANGED` | `null` |
| Task estimation | Không | Assignee hoặc Team | Story Point được canonical-confirm | `TASK_ESTIMATION_CHANGED` | `null` |
| Task status | Không | Assignee hoặc Team | Transition được canonical-confirm | `TASK_STATUS_CHANGED` | `null` |
| Task delete | Không | Assignee hoặc Team | Jira delete và local tombstone hoàn tất | `TASK_DELETED` | `null` |
| Sprint create/update/start/close/delete | Không | Student Team sở hữu Project | Jira write `COMPLETED`; start/close đã canonical-confirm state | `SPRINT_CREATED`, `SPRINT_UPDATED`, `SPRINT_STARTED`, `SPRINT_CLOSED`, `SPRINT_DELETED` | `null` |
| Task đến hạn ngày mai | Không | Assignee hoặc Team | Date-only deadline scan | `TASK_DUE_TOMORROW` | `null` |
| Task đến hạn hôm nay | Không | Assignee hoặc Team | Date-only deadline scan | `TASK_DUE_TODAY` | `null` |
| Task quá hạn | Không | Assignee hoặc Team | Date-only deadline scan | `TASK_OVERDUE` | `null` |

Student actor của Task/Sprint mutation được loại khỏi tập recipient khi phù hợp. Event dedup ở backend ngăn replay/restart tạo trùng Bell item.

## Student account lifecycle V2 — 2026-08-14

No frontend endpoint or payload changed. Both normal ordering variants now converge automatically:

- Course import first, Student login later: exact identity binding returns the existing Student as ACTIVE.
- Student login/register first: successful accepted authentication immediately creates the local Student as ACTIVE, without TeamMember. Later exact Course provisioning reuses that Student, preserves ACTIVE and creates/reuses TeamMember.

FE must not require or automate `PATCH ACTIVE` for this normal flow and must not treat invitation delivery/click as activation or enrollment. Invitation remains an informational CTA. After successful login, `GET /api/auth/me` reports ACTIVE even before Course membership; after import, the existing `/api/me/courses/{courseId}/team/members` path resolves the Course/team immediately through the same local Student ID and TeamMember.

An unlinked imported placeholder remains PENDING and blocked with `ACCOUNT_STATUS_ACCESS_DENIED` until exact first login binds it. Historical `PENDING + cognitoSub` recovers on successful same-identity login without TeamMember. INACTIVE/SUSPENDED are not automatically reactivated. AccountStatus must not be rendered as Course enrollment status. Browser navigation login, `credentials: "include"`, JSESSIONID, CSRF rules and Cognito behavior are unchanged.

## 9. Notification frontend behavior

```text
App boot / sau login
  -> GET /api/auth/me
  -> GET /api/auth/csrf
  -> POST /api/me/firebase-installations (khi push được bật và có FID)
  -> GET /api/me/notifications?page=0&size=20
  -> GET /api/me/notifications/unread-count

Foreground FCM
  -> invalidate/refetch Bell list + unread count

Background FCM
  -> service worker/browser notification
  -> khi app mở lại, refetch Bell list + unread count

Mark read
  -> PATCH /api/me/notifications/{id}/read
  -> cập nhật item và unread count từ response/refetch
```

Không dựng Bell history chỉ từ push payload và không giả định `actionUrl` luôn có giá trị.

## 10. Logout và device lifecycle

Backend logout hiện không tự revoke Firebase installation. Product policy “một device có tiếp tục nhận push sau logout hay không” vẫn `TBD`; FE không được tự suy diễn contract mới.

- Khi user chủ động tắt push hoặc xóa đăng ký device: gọi revoke bằng installation UUID đã lưu.
- Khi logout: thực hiện `POST /api/auth/logout` theo browser navigation. Chỉ revoke trước logout nếu UI/product đã chọn rõ hành vi đó và FE vẫn còn session + CSRF hợp lệ.
- Sau logout xóa auth/CSRF/Bell state khỏi memory; không lưu FID, cookie hoặc token thật vào log.
## GitHub Issues và traceability — 2026-08-11

Backend đã expose local read model cho màn `Project > GitHub > Issues`. Các GET dưới đây không gọi
GitHub/Jira realtime; dữ liệu mới nhất phụ thuộc backfill/reconciliation/webhook sync hiện hữu.

| Method | Route | Dùng cho FE |
|---|---|---|
| GET | `/api/projects/{projectId}/github/issues` | List Issue, filter, pagination, counters |
| GET | `/api/projects/{projectId}/github/issues/{issueId}` | Issue detail + linked Task/PR/Commit + timeline |
| POST | `/api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}` | Manager explicit Issue–Commit link (`MANUAL`) |
| DELETE | `/api/projects/{projectId}/github/issues/{issueId}/commits/{commitId}` | Manager unlink; repeated unlink 204 |
| POST | `/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}` | Manager link local Task–Issue |
| DELETE | `/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{issueId}` | Manager unlink local Task–Issue |
| GET | `/api/v1/projects/{projectId}/tasks/{taskId}/traceability` | Task → Issues → PR/Commit |
| GET | `/api/projects/{projectId}/traceability?limit=50` | Bounded project timeline |

Issue list query:

- `state=OPEN|CLOSED` optional;
- `repositoryId` optional, dùng repository ID đã trả từ Project integrations;
- `keyword` search title hoặc exact issue number, hỗ trợ `42`/`#42`;
- `assignedToMe=true` chỉ hợp lệ cho current Student có local profile;
- `page` từ 0, `size` 1..100.

`summary.open`, `summary.closed`, `summary.assignedToMe`, `summary.unassigned` được tính trong toàn
Project và repository filter hiện tại, độc lập với state/keyword/assigned-to-me filter. Sort list cố
định mới nhất trước theo external update, rồi issue number/id để pagination deterministic.

Safe Issue shape gồm local `id`, `issueNumber`, `title`, `state`, repository `{repositoryId,fullName}`,
resolved local `author`/`assignee` `{id,fullName,studentCode}` nullable, `externalUpdatedAt`, `closedAt`.
Không có `githubIssueId`, `nodeId`, external identity ID, installation hoặc credential. Unresolved
author/assignee được trả `null`, FE không tự map bằng login/name.

Issue detail trả `linkedTasks`, `linkedPullRequests`, `linkedCommits` dưới dạng
`{items, truncated}` và timeline có `sourceType`:
`JIRA_TASK|GITHUB_ISSUE|GITHUB_PULL_REQUEST|GITHUB_COMMIT`. Collection/timeline tối đa 100; project
timeline nhận `limit` 1..100. Timestamp null không xuất hiện trong timeline; không coi local
`createdAt` là GitHub created time.

Task–Issue link là **SAGA local relation**. POST không tạo/sửa GitHub Issue và không sửa Jira Task;
duplicate POST trả `200 linked=true`. DELETE trả `204` cả khi pair đã được gỡ. Mutation dùng browser
session + CSRF, không có actor ID/request body và không yêu cầu `Idempotency-Key`. Quyền mutation là
exact Project Integration Manager hiện hữu: ADMIN override, Lecturer đúng Course, Student Team
LEADER; MEMBER không được link/unlink. Read dùng Project read scope hiện hữu, gồm Student MEMBER đúng
owning Team. Không dùng Bearer.

PR/Commit relation type có `REFERENCE|CLOSING_REFERENCE|MANUAL`, nhưng current provider chưa tự
populate authoritative relation; FE phải chấp nhận danh sách rỗng. Không suy `REFERENCE` là
`CLOSING_REFERENCE`. GitHub Issue remote CRUD và lifecycle notification chưa có trong milestone này.
## J1K Jira Task Issue Type Update contract — 2026-08-13

Normal FE changes Jira Issue Type through the existing sparse endpoint:

```http
PUT /api/v1/projects/{projectId}/tasks/{taskId}
Idempotency-Key: <stable key for this intent>
Content-Type: application/json
```

```json
{ "type": "FEATURE" }
```

The normal UI values are `BUG`, `FEATURE`, `REQUEST`, `STORY`, and `TASK`, all represented by SAGA `TaskType`. Do not send `issueTypeId`, numeric Jira IDs, `customfield_*`, Bearer tokens, or provider metadata. Mixed sparse updates such as `{ "title": "...", "type": "FEATURE", "priority": "HIGH" }` are supported in one mutation.

Backend resolves the provider ID from `editmeta.fields.issuetype.allowedValues` of the exact issue. `JIRA_EDIT_FIELD_NOT_ALLOWED`, `JIRA_ISSUE_TYPE_RESOLUTION_NOT_FOUND`, or `JIRA_ISSUE_TYPE_RESOLUTION_AMBIGUOUS` are controlled failures: retain the user's input and display the backend error; never ask the user for a Jira ID. EPIC/SUBTASK hierarchy conversion is not supported by this normal edit flow.

If canonical confirmation fails after Jira accepted the PUT, retry the identical body with the identical `Idempotency-Key`; do not create a new key or send a different type. Backend will reconcile canonical state without replaying the provider mutation. Browser auth remains `JSESSIONID` with `credentials: "include"`, CSRF for PUT, and required `Idempotency-Key`; no Bearer flow was added. Runtime deploy smoke remains **TBD_DEPLOYMENT_SMOKE**.
## J1K.1 TaskType.REQUEST deployment note — 2026-08-13

Backend source already accepts business `REQUEST`, but the deployed physical MySQL `task.type` enum must receive Flyway V29 before Jira Request can persist. This does not change the FE payload or error-handling contract. Until deployment smoke completes, sync-history `ITEM_UPSERT_FAILED` at `UPSERT_ISSUES` with the confirmed enum mismatch is a deployment/schema incident; FE must not substitute another type or send a Jira provider ID.
# Merged main FE contracts — Project / Lecturer / Admin / AI — 2026-08-15

**Current baseline superseded 2026-08-15 later same day:** OpenAPI **150**, migration **V33**. Avatar / progress / Course-weight contracts are in the top section of this file. Numbers **149 / V32 / 994** below are the DEC-082 historical snapshot.

Authority: current merged `main` source + OpenAPI **149** + migration head **V32**. Browser auth always `JSESSIONID` + `credentials: "include"`. CSRF required for unsafe methods. **Do not send Bearer.** Deployed Swagger currency and HF/AI product smoke remain **TBD**.

## Project V1

| Method | Path | Roles | CSRF | Notes |
| --- | --- | --- | --- | --- |
| GET | `/api/project-types` | ADMIN, LECTURER, STUDENT | No | Dynamic catalog; fresh DB may return `[]` |
| POST | `/api/project-types` | ADMIN | Yes | No canonical production seed |
| POST | `/api/teams/{teamId}/projects` | existing TeamProject auth | Yes | **`projectTypeId` mandatory** → `PROJECT_TYPE_REQUIRED` if missing |
| PUT | `/api/projects/{projectId}/group-weights` | ADMIN or Course instructor LECTURER | Yes | Exact Project+Team Code/Document/Design; sum must be 1.0 |

Contribution weight **source** (not formula): exact Project+Team group config first; Course-level slice weights when absent. Peer Review / Rubric / Contribution formula unchanged. Do not treat ProjectType `criteriaConfig` as scoring authority.

## Lecturer Dashboard (implemented routes)

Dashboard:

- `GET /api/v1/courses/{courseId}/dashboard/teams-progress`
- `GET /api/v1/courses/{courseId}/dashboard/contribution-summary`
- `GET /api/v1/courses/{courseId}/dashboard/trends`
- `GET /api/v1/courses/{courseId}/dashboard/at-risk-summary`

`teams-progress` `TeamProgress.activeSprints[]` is the authority for active Sprint(s). Session GET, no CSRF, no Bearer. ADMIN / Course instructor LECTURER only.

- `activeSprints.length === 0`: no active sprint; `currentSprint` is null; legacy `currentSprint*` counters are 0
- `activeSprints.length === 1`: keep the current single-sprint UI; `currentSprint` matches that item
- `activeSprints.length > 1`: show a list/picker; `currentSprint` is null; do **not** treat legacy `currentSprint*` as an aggregate
- Burndown: `GET .../teams/{teamId}/sprints/{sprintId}/burndown` with `activeSprints[i].sprintId`

Also present on `LecturerAnalyticsController` (existing): detail, overview, progress, activities, contribution-detail, early-warnings, student interactions, burndown, heatmap, velocity. Auth ADMIN/LECTURER (plus Student Team graph reads already documented separately). Early warning = deterministic `OVERDUE_TASK` only. Do **not** implement FE for GHOSTING / TOXIC_COMMUNICATION / TECHNICAL_DEBT unless Backend later adds them.

## Admin Dashboard V1

| Method | Path | Auth | CSRF |
| --- | --- | --- | --- |
| GET | `/api/admin/reports/anomalies` | ADMIN session | No |
| GET | `/api/admin/reports/graph-processing` | ADMIN session | No |

Anomalies: `OVERDUE_TASK` supported with real `count`; `MSR` / `DEADLINE_PROCESS` / `SNA_ISOLATION` = `supportStatus: "TBD"` and **`count: null`** (never treat null as zero). Graph-processing: `periodDays: 7`, `historySupported: false`, `points: []` — do not invent charts. LECTURER/STUDENT forbidden; anonymous unauthorized.

## AI Agent V1 (public only)

Safe GETs: conversations list/detail, artifact download. Unsafe POSTs: create conversation, send message, confirm/reject pending action. Internal `/internal/ai/**` and Backend→AI `/internal/backend/v1/commit-reviews**` are **not** a frontend contract. AI does not accept browser `JSESSIONID`, does not read SAGA business DB, and does not call Jira/GitHub. Confirm/reject runs through Backend mutation path; no automatic Task mutation. Current actor identity is session/delegation only — FE must not send identity fields and must not ask name/MSSV to identify the logged-in user (DEC-095). When chat is opened inside a Course, FE sends additive `courseId` as resource scope (DEC-099); Backend validates it and binds the conversation.

## Verification note for FE

Local OpenAPI includes Project / Admin / Lecturer dashboard / AI public routes. Full clean suite at DEC-082 snapshot was **not** green (**994 / 23 failures**): 22 CSRF isolation flakes + DEC-023 roster baseline. **Current full suite (DEC-083):** 1019 tests / 23 failures / 8 errors; still not green. Feature contracts above are source/test confirmed; do not wait for full-suite green to integrate these routes.

# Internal AI context is not a frontend contract (2026-08-14)

`/internal/ai/**` is reserved for authenticated `saga-ai-service` reads and uses `X-SAGA-AI-Service-Token`. Frontend code must not send, store, or request this credential and must not call the internal commit-review context endpoint. Existing browser APIs continue to use `JSESSIONID`, `credentials: include`, and CSRF for unsafe methods; M5 adds no public/browser AI review API.

## Student Team graph read contract — 2026-08-14

FE may render these exact four widgets for a STUDENT whose current Team membership has either `roleInTeam=LEADER` or `roleInTeam=MEMBER`:

- `GET /api/v1/courses/{courseId}/teams/{teamId}/overview?startDate=...&endDate=...`
- `GET /api/v1/courses/{courseId}/teams/{teamId}/heatmap?startDate=...&endDate=...&studentId=...` (`studentId` optional)
- `GET /api/v1/courses/{courseId}/teams/{teamId}/students/{studentId}/interactions`
- `GET /api/v1/courses/{courseId}/teams/{teamId}/sprints/{sprintId}/burndown`

Use `credentials: "include"` with the browser `JSESSIONID`; do not send an Authorization Bearer token or CSRF header for these GETs. Do not hide these widgets merely because `roleInTeam != LEADER`: MEMBER has the same read permission. The caller must belong to the exact Team; interaction/heatmap targets must be members of that Team and Sprint must belong to its Project. Other Lecturer Analytics endpoints, including historical `/api/analytics/...` designs, are not opened by this contract.

## SAGA AI Agent V1 frontend contract — 2026-08-14

No frontend repository was discovered during this milestone, so this is an integration contract, not a UI implementation claim. Browser code calls only Backend and always uses `credentials: "include"`. It must never contain or send `SAGA_BACKEND_TO_AI_SERVICE_TOKEN`, `SAGA_AI_SERVICE_TOKEN`, provider keys, AI DB URL, or a Hugging Face URL.

Safe GETs (session, no CSRF header):

- `GET /api/v1/ai/conversations`
- `GET /api/v1/ai/conversations/{conversationId}`
- `GET /api/v1/ai/artifacts/{artifactId}/download`

Unsafe calls (session plus the existing CSRF header/cookie contract):

- `POST /api/v1/ai/conversations` with `{ "title": "optional, max 160", "courseId": "optional UUID resource scope when chat is opened inside a Course" }`
- `POST /api/v1/ai/conversations/{conversationId}/messages` with `{ "content": "required, max 8000", "courseId": "optional UUID; must match the conversation's bound Course" }`
- `POST /api/v1/ai/pending-actions/{actionId}/confirm` with no mutable action body (DEC-100: same path/body; behavior change is recovery-only, below)
- `POST /api/v1/ai/pending-actions/{actionId}/reject` with no mutable action body

Do not send `actorId`, `ownerId`, `applicationRole`, `studentId`, `lecturerId`, provider, model, Backend path, or tool name. `courseId` is a Course resource-scope hint, not actor identity. When the user is chatting inside an open Course, send that Course's `courseId` on create and every message. Backend validates current access and binds the conversation to that Course. Reusing a Course A conversation while the UI is on Course B returns `409 AI_AGENT_COURSE_SCOPE_MISMATCH` — create a new conversation for Course B instead of keeping history A. Conversation list/detail may include `courseId`. Do not render TOOL-role rows such as `discover_resource_context:COMPLETED`; Backend/AI filter those from the public conversation payload. Backend derives the current local owner and role from the session. Do not ask the user for name/MSSV to identify who is chatting; resource-selection questions (which Team/Project inside the open Course) are allowed when more than one valid resource remains.

A chat response includes `conversationId`, `messageId`, `text`, `status`, citations, optional `pendingAction`, optional `generatedArtifact`, optional `jobReference`, suggested follow-ups, and safe provider/model metadata. Render factual errors as unavailable/forbidden/not found; do not convert a failed tool or mutation into success text.

For `pendingAction`, show the immutable summary and expiry with explicit **Confirm** and **Cancel** controls. No Task exists before Confirm. If the proposal includes a Sprint, the summary must show the resolved Sprint **name**, not only a UUID. Confirm/Cancel still send no actor fields and no Sprint mutation body.

Disable repeated confirmation after the first request. Backend/AI still enforce atomic `PENDING` claim once and stable idempotency. Expired/rejected/completed/failed actions require a new proposal. **DEC-100 exception (narrow):** if this `TASK_CREATE` proposal had a Sprint and Confirm returns `409 JIRA_WRITE_RECOVERY_REQUIRED` or `409 JIRA_WRITE_OPERATION_IN_PROGRESS`, retry the **same** Confirm (`same actionId`, empty body, session + CSRF). Do not send a new proposal, do not derive keys, and do not call `PUT /api/v1/projects/{projectId}/tasks/{taskId}/sprint` for the happy path or this recovery. Do not retry Confirm for `TASK_UPDATE`, for create-only proposals, or for other 4xx/5xx. A concurrent double-submit may return `409`; that is fail-safe, not a second Confirm. Success remains `200` `{ actionId, status: "COMPLETED", task }`. Error bodies stay `ApiErrorResponse` without a Task.

For `generatedArtifact`, use only the Backend download endpoint. Do not construct AI URLs. For `jobReference`, render `PENDING`, `RUNNING`, `WAITING_RETRY`, `COMPLETED`, or `FAILED`; the chat can ask for the latest conversation-scoped result without requiring the user to know a job ID.

Logout destroys the Backend session and therefore Agent access. Chat delete/retention UI is deferred until product data policy is defined.

## Disabled OIDC callback handling (DEC-104) — 2026-08-17

After Cognito completes a normal ACTIVE login, Backend creates the token-free browser
session and redirects to `AUTH_SUCCESS_REDIRECT_URI` as before. For a confirmed
INACTIVE/SUSPENDED Student or Lecturer, Backend creates no usable SAGA session and
redirects the top-level browser callback to:

```text
AUTH_FAILURE_REDIRECT_URI?error=ACCOUNT_DISABLED
```

`AUTH_FAILURE_REDIRECT_URI` is Backend configuration (absolute HTTP(S), non-secret) and
falls back to `AUTH_SUCCESS_REDIRECT_URI` when not set. Frontend reads only the
allowlisted `error` code on its auth callback page, clears local auth UI state, and
displays the disabled-account message. No FE API call, Bearer token, identity value, or
provider token is supplied in this redirect. Do not treat other OIDC failures as
`ACCOUNT_DISABLED`.

## Account disabled session handling (DEC-101) — 2026-08-17

All browser API calls continue to use `credentials: "include"`; do not use Bearer tokens. When a currently authenticated Student or Lecturer is changed to `INACTIVE` or `SUSPENDED`, the next request using that session can return the existing error shape:

```json
{
  "status": 401,
  "error": "ACCOUNT_DISABLED",
  "message": "Tài khoản của bạn đã bị vô hiệu hóa."
}
```

Treat `401 ACCOUNT_DISABLED` as terminal for the local auth UI: clear authenticated state/CSRF state and show the disabled-account message, then use the normal logout/login UI flow. Do not retry the original request. `GET /api/auth/me` uses the same behavior for a disabled session, so it cannot bootstrap the app. `GET /api/auth/csrf` remains available solely for the CSRF-protected logout flow; it does not bypass the disabled gate for `/me` or business APIs. `POST /api/auth/logout` remains CSRF-protected and usable/idempotent; missing or invalid CSRF remains its normal `403`, not `ACCOUNT_DISABLED`.

The backend invalidates only the session that reaches it. It does not claim instant global cross-browser/cross-instance revocation, and re-enabling an account does not revive an already invalidated session; the user must authenticate again.

## Admin graph-processing density (DEC-102) — 2026-08-17

`GET /api/admin/reports/graph-processing` remains an ADMIN-only browser-session GET (`credentials: "include"`), requires no CSRF header, and never accepts Bearer authentication. Backend returns real persisted processing work:

```json
{
  "generatedAt": "2026-08-17T00:00:00Z",
  "periodDays": 7,
  "historySupported": true,
  "coverageStart": "2026-08-15T00:00:00Z",
  "points": [
    { "date": "2026-08-17", "nodesBuilt": 14, "edgesBuilt": 10, "runCount": 3 }
  ]
}
```

`date` is bucketed by Backend in `Asia/Ho_Chi_Minh`. Do not recompute day buckets in browser timezone. `points` contains only dates with persisted runs inside the current rolling seven local calendar days; an empty array is normal for pre-cutover/no-activity and must not be padded with synthetic zero or historical values. `coverageStart` is the earliest persisted run overall and is `null` until the first actual run. The former `nodesCreated`, `nodesUpdated`, `edgesCreated`, and `edgesUpdated` fields are removed; use only `nodesBuilt`, `edgesBuilt`, and `runCount`.
