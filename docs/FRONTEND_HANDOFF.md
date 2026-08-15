# SAGA Frontend Handoff

## Jira task attachments from SAGA (DEC-093) — 2026-08-15

Current Backend contracts: OpenAPI **149**, migration head **V39**.

- `POST /api/v1/projects/{projectId}/tasks/{taskId}/attachments` — multipart `files` (optional, max 5, 10MB each) and/or form field `link` (optional `http`/`https`, max 2048 chars). At least one of files or link is required.
- **Chỉ STUDENT** thành viên Team sở hữu Project. Lecturer và Admin **không** gọi được endpoint này. Requires session + CSRF + `Idempotency-Key`.
- File: SAGA upload lên Jira rồi canonical-fetch, lưu metadata `task_attachment` (không URL tải file). Link: POST Jira remote link, lưu `task_web_link` (URL only). Response `{taskId, attachments[], links[]}`.
- DOCUMENT/RESEARCH contribution cần ≥1 file **hoặc** ≥1 link.

## Absolute weighted slice × peer (DEC-092) — 2026-08-15

Current Backend contracts: OpenAPI **149**, no migration.

- `GET /api/v1/teams/{teamId}/contribution-evaluation`:
  - `sliceScore` = Σ slice (Σ SP × trọng số) **trước** khi nhân peer. `sliceContributionPercentage` = `sliceScore / Σ slice team × 100`.
  - `finalContributionPercentage` = `(Σ slice × project P) / team adjust × 100` — đã gồm peer. Do not re-apply peer on the client.
  - Per sprint: `sprintBreakdowns[].sliceScore` / `sliceContributionPercentage` (chưa nhân `P_s`) và `contributionPercentage` (đã nhân `P_s`). `P_s = 1` nếu sprint chưa có peer.
- Tasks with no sprint do not score. Radar `code/test/document/researchContributionPercentage` stays project-level criterion share.

## Sprint-first contribution % (DEC-091) — 2026-08-15

**SUPERSEDED by DEC-092.** Equal-average of per-sprint mix is no longer the evaluation path.

## Labels-only Task scoring + Jira attachment metadata (DEC-090) — 2026-08-15

Current Backend contracts: OpenAPI **148**, migration head **V38**.

- A DONE Jira Task only scores into a Contribution criterion when it has **exactly one** reserved label: `saga:code`, `saga:test`, `saga:document`, `saga:research`. Ordinary labels (`backend`, `ui-ux`, …) and issue type/title **do not** classify the task anymore.
- Conflicting reserved labels (e.g. `saga:test` + `saga:research`) still exclude the task from all four criteria until fixed — silent in the score, no new error API.
- **DOCUMENT / RESEARCH:** story points count **only if** the task has at least one Jira file attachment **or** one submitted web link. Extra files/links do not add extra points. **CODE / TEST:** story points always count; attachments/links are ignored.
- SAGA still stores **attachment metadata only** (id, filename, mime, size, author) during Jira issue sync **and after student upload**. There is **no** file download and **no** content URL. GitHub attachments are still not ingested.
- Students (Team members) upload via `POST /api/v1/projects/{projectId}/tasks/{taskId}/attachments` (DEC-093).
- Lecturer still edits Course/Team weights directly. Weight-request / Admin-approval APIs remain removed.

## Task is sole numeric Contribution authority + reserved markers (DEC-089) — 2026-08-15

Current Backend contracts: OpenAPI **148**, migration head **V37**. DEC-090 above supersedes the keyword-fallback and “attachments not implemented” notes from this section.

- Lecturer sửa trọng số Course/Team trực tiếp. Các API gửi đơn / lấy danh sách đơn / Admin duyệt trọng số **đã gỡ**.

- New reserved Jira Task labels: `saga:code`, `saga:test`, `saga:document`, `saga:research` — exact match only (typos/case differences do nothing). A DONE Task carrying one of these routes its story points into that Contribution criterion, so `testContributionScore`/`researchContributionScore` can now be genuinely non-zero (previously always `0`). No Task with the marker → still `0`, unchanged.
- A Task with more than one conflicting marker (e.g. `saga:test` + `saga:research`) is silently excluded from all four criteria — no error is surfaced to FE, it just won't show up in any criterion's score until the label conflict is fixed.
- Internal-only change, no new FE-visible field: a commit linked to a Task no longer adds any score on top of that Task's own DONE contribution.

## Contribution weight: Course-default + optional exclusive Team override — 2026-08-15

Current Backend contracts: OpenAPI **151**, migration head **V36**. Supersedes the "Course-wide 4-slice" section below (kept verbatim as history).

- Each Course has exactly one active Contribution config mode: `COURSE` or `TEAM`. **No hybrid** — a Course is never "Team override if set, else Course." If TEAM mode is active and a Team has no override, that Team's Contribution is not computable (`TEAM_WEIGHT_CONFIG_INCOMPLETE`), not silently using Course weights.
- Criteria are now **`CODE/TEST/DOCUMENT/RESEARCH`** — `DESIGN` is retired as a Contribution criterion (still exists as a ProjectType value only, see below; the two are unrelated).
- COURSE mode: `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` — same route, `{codeWeight, testWeight, documentWeight, researchWeight}`, sum 100.
- TEAM mode: `PUT /api/projects/{projectId}/group-weights` is **revived** — `{groupId, codeWeight, testWeight, documentWeight, researchWeight}`, 0..1 scale, sum 1.0. ADMIN or exact Course-instructor LECTURER only — never Team leader/student.
- Mode switch: `PUT /api/v1/courses/{courseId}/contribution-config-mode` `{"mode":"COURSE"|"TEAM"}`. Switching to `TEAM` requires every current Team already has a valid override (atomic; 409 if any is missing). Switching to `COURSE` never deletes Team overrides — they become inactive/historical.
- New team-menu read: `GET /api/v1/courses/{courseId}/contribution-team-weights` (mode + effective weight + source per Team).
- **Test/Research scoring now works for the Task-marker path (see the section above)** — no longer always `0`, but still not the full milestone (attachment/commit-traceability evidence remains unimplemented).
- Existing Course/Team weight rows are **not reset** by these migrations — Code/Document values are untouched; only the new columns are added, defaulting to `0`/`COURSE`.

## Course-wide 4-slice Contribution weights (superseded by the section above) — 2026-08-15

Current Backend contracts: OpenAPI **148**, migration head **V35**.

- Contribution weight authority is **Course-only** now: one Code/Test/Document/Design config per Course applies to every Team in it. No per-Team weight screen.
- `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` — same route, now four fields (`codeWeight`, `testWeight`, `documentWeight`, `designWeight`), sum 100.
- `PUT /api/projects/{projectId}/group-weights` **removed** — drop any per-Team weight override UI/calls.
- Contribution evaluation response gained `testContributionScore` / `testContributionPercentage`; existing fields unchanged.
- **Test scoring is not confirmed/implemented yet.** `testWeight` is stored and echoed back, but it has zero effect on results right now — Backend has no deterministic testing/QA evidence source, so it's always normalized out and `testContributionScore`/`testContributionPercentage` are always `0`. Don't market it as working in UI copy yet.
- Existing Course rows are **not reset** by this migration — their Code/Document/Design values are untouched; only `testWeight` is newly added (`0` for pre-existing Courses).

## ProjectType fixed canonical catalog — 2026-08-15

Current Backend contracts: OpenAPI **149**, migration head **V34**.

- ProjectType is a **fixed canonical catalog** (`DESIGN_ARCHITECTURE`/`RESEARCH`/`TESTER`/`DOCUMENT`), seeded by Backend migration — not something ADMIN creates anymore. Does not decide Contribution weight (see section above).
- `GET /api/project-types` unchanged; always returns exactly those 4 rows now.
- `POST /api/project-types` **removed** — do not build/keep an Admin "create ProjectType" UI or call it.
- Project create unchanged: still send the selected `projectTypeId` from the GET catalog; `PROJECT_TYPE_REQUIRED` / `PROJECT_TYPE_NOT_FOUND` unchanged.
- Projects created before this migration now read `projectType: null` — render that as "no type" rather than an error.

## Avatar / progress / Course weights — 2026-08-15

Current Backend contracts: OpenAPI **150**, migration head **V33**. Browser `JSESSIONID` + `credentials: "include"`; CSRF on unsafe mutations; GET no CSRF; **never Bearer**.

- **Avatar:** render `avatarUrl` từ `GET /api/auth/me` (nullable). Fallback UI khi null. Không gọi Google image API, không gửi provider token/avatar URL.
- **Progress:** `GET /api/v1/courses/{courseId}/students/{studentId}/progress`. MEMBER self only; LEADER own Team members (exact Team, kể cả khi target còn membership Team khác trong Course); Lecturer Course owner; Admin retained; MENTOR forbidden. Không Course-wide.
- **Course weights:** `GET` + `PUT /api/v1/courses/{courseId}/contribution-slice-weights`. Lecturer direct edit exact Course only. Body `{codeWeight, documentWeight, designWeight}` scale 0–100. Mutation gửi CSRF. Không còn luồng gửi đơn / Admin duyệt trọng số.
- **teams-progress:** `activeSprints[]` is authority. One active → keep `currentSprint` UI. Multiple active → list/picker; burndown uses `activeSprints[i].sprintId`. Do not treat `currentSprint` as primary when the list has more than one.

See `FRONTEND_API_INTEGRATION.md` for the detailed 2026-08-15 contracts. DEC-082 (OpenAPI 149 / V32) is a historical snapshot.

## Merged main sync — 2026-08-15 (historical DEC-082 snapshot)

Superseded as **current** baseline by the Avatar/progress/Course weights section above (OpenAPI **150**, V33). Integrate against current merged Backend contracts (OpenAPI **149**, migration head **V32**):

- **Auth:** browser `JSESSIONID` + `credentials: "include"`; CSRF on unsafe mutations; GET no CSRF; **never Bearer**.
- **Project V1:** `GET/POST /api/project-types`; create Project requires `projectTypeId`; `PUT /api/projects/{projectId}/group-weights`.
- **Lecturer Dashboard:** four `GET .../dashboard/*` routes; early warning remains `OVERDUE_TASK` only.
- **Admin Dashboard V1:** `GET /api/admin/reports/anomalies` and `.../graph-processing` (ADMIN); unsupported anomaly counts are JSON `null`.
- **AI:** only public `/api/v1/ai/**`; do not call `/internal/ai/**`. Full suite is **not** green (994/23 failures classified); contracts above remain source-confirmed.

See `FRONTEND_API_INTEGRATION.md` for the detailed 2026-08-15 matrix.

## Student Team Leader — Contribution read

Student có exact `RoleInTeam.LEADER` gọi API hiện hữu của chính Team:

```ts
fetch(`/api/v1/teams/${teamId}/contribution-evaluation`, {
  credentials: "include",
});
```

GET không cần CSRF và không dùng Bearer. ADMIN xem mọi Team; LECTURER chỉ Team thuộc Course mình
phụ trách; Student chỉ LEADER của exact Team. MEMBER, MENTOR, Leader Team khác và Student không
membership nhận 403; backend là authority, UI ẩn/hiện không thay authorization.

FE có thể render `member.fullName`, `member.studentCode`, `member.finalContributionPercentage`
và các metric/breakdown/warnings Contribution hiện hữu. Response không chứa email, Cognito subject,
reviewer/comment, token, credential hoặc raw Jira/GitHub payload. Đây là current aggregate, không
phải historical snapshot. Leader không được mở UI/API contribution override hay slice-weight mutation.

## Team detail → GitHub repository navigation

`GET /api/v1/courses/{courseId}/teams/{teamId}/detail` trả additive
`project.repositories: [{repositoryId,repositoryName}]`. `project` vẫn `null` nếu Team chưa có Project;
nếu đã có Project nhưng chưa link GitHub thì danh sách rỗng. Một Project có thể có nhiều repository, vì
vậy FE hiển thị repository selector và không tự pick phần tử đầu làm canonical.

`repositoryId` là số `int64` dùng trực tiếp cho
`/api/projects/{projectId}/github/repositories/{repositoryId}/branches`,
`/commits` và filter `repositoryId` của GitHub Issue list. `repositoryName` là `owner/name` an toàn.
Response lấy từ local DB, không chứa URL/installation/credential và không gọi GitHub provider. Quyền Team
detail giữ nguyên ADMIN/LECTURER đúng Course; STUDENT không được mở quyền mới.

## Project GitHub Issues / traceability

Màn mới được backend hỗ trợ: `Project > GitHub > Issues`.

- List gọi `GET /api/projects/{projectId}/github/issues` với `state`, `repositoryId`, `keyword`,
  `assignedToMe`, `page`, `size`. Render counters `open`, `closed`, `assignedToMe`, `unassigned`.
- Detail gọi `GET /api/projects/{projectId}/github/issues/{localIssueId}`; render metadata local,
  linked Jira Tasks, linked PRs, linked Commits và timeline. Respect `truncated`; không tự recursive
  fetch toàn graph.
- Task detail gọi `GET /api/v1/projects/{projectId}/tasks/{taskId}/traceability` để render
  Planning → Development tracking → Implementation.
- Project activity gọi `GET /api/projects/{projectId}/traceability?limit=50`; `limit` tối đa 100.
- Manager link/unlink bằng POST/DELETE
  `/api/v1/projects/{projectId}/tasks/{taskId}/github-issues/{localIssueId}`. Gửi cookie và CSRF như
  mọi unsafe browser mutation; không gửi actor, Bearer hay `Idempotency-Key`.
- Link/unlink chỉ đổi SAGA local DB; không tự thay đổi Jira/GitHub. Duplicate link là 200 replay;
  repeated unlink là 204.
- `author`/`assignee` có thể null khi identity mapping chưa resolve. Không dùng provider ID ẩn để tự
  ghép người dùng.
- PR/Commit lists có thể rỗng vì provider authoritative auto-link còn PARTIAL. Không hiển thị
  `REFERENCE` như “closes/fixes”. GitHub Issue create/edit/close/assign chưa có backend API.

Checklist triển khai FE cho browser session, invitation email và Notification/Firebase. Tài liệu chi tiết: `docs/FRONTEND_API_INTEGRATION.md`.

## Environment

```text
API_BASE_URL=https://<saga-backend-host>
FIREBASE_CONFIG=<public web config for environment>
FIREBASE_VAPID_PUBLIC_KEY=<public key when Web Messaging rollout is enabled>
```

Không đưa Firebase Admin service account, private key, Gmail App Password, Cognito client secret, session cookie, CSRF token hoặc FID thật vào source/log.

## Auth/session setup

- Login: browser navigation `GET {API_BASE_URL}/api/auth/login`.
- Mọi API call: `credentials: "include"`; không Bearer.
- Hydrate user: `GET /api/auth/me`.
- Bootstrap CSRF: `GET /api/auth/csrf`; mutation gửi `X-XSRF-TOKEN`.
- Logout: POST form/navigation tới `/api/auth/logout`; không dùng GET logout.

## API theo feature

| Feature | API | Role | Khi gọi |
|---|---|---|---|
| Current user | `GET /api/auth/me` | Authenticated | App boot/sau login |
| CSRF | `GET /api/auth/csrf` | Authenticated | Trước mutation |
| Team contribution | `GET /api/v1/teams/{teamId}/contribution-evaluation` | ADMIN; LECTURER đúng Course; STUDENT exact Team LEADER | Xem current aggregate, không CSRF |
| Course grouping import | `POST /api/v1/courses/{courseId}/import-students` | ADMIN hoặc LECTURER phụ trách | Upload XLSX |
| Admin Course import | `POST /api/v1/courses/{courseId}/admin-import-students-template` | ADMIN | Upload XLSX 5 cột |
| Bell list | `GET /api/me/notifications?page=0&size=20` | ADMIN/LECTURER/STUDENT | App boot, refresh, sau FCM |
| Unread count | `GET /api/me/notifications/unread-count` | ADMIN/LECTURER/STUDENT | App boot và badge refresh |
| Mark read | `PATCH /api/me/notifications/{id}/read` | Owner | User mở/đọc item |
| Register browser FID | `POST /api/me/firebase-installations` | Authenticated | Sau login và Firebase init |
| Revoke browser FID | `DELETE /api/me/firebase-installations/{installationId}` | Owner | Tắt push/xóa device registration |
| Admin broadcast | `POST /api/admin/notifications/broadcast` | ADMIN | Gửi manual audience |
| Course broadcast | `POST /api/v1/courses/notifications/broadcast` | LECTURER | Gửi tới TeamMember của Course sở hữu |

## Notification integration

- SAGA DB/Bell API là source of truth; FCM chỉ báo có thay đổi.
- Foreground/background push đều dẫn tới refetch Bell list và unread count.
- `actionUrl` hiện nullable; không invent route.
- Broadcast cần `Idempotency-Key`; cùng intent dùng lại key, intent khác tạo key mới.
- Automatic Task/Sprint/Integration/deadline notification không có send endpoint cho FE.

## Firebase Web boundary

- Dùng Firebase Web SDK và public environment config.
- Gửi placeholder format `{ "firebaseInstallationId": "example-browser-fid" }` tới backend.
- Lưu installation UUID backend trả về để revoke; không coi FID là UUID này.
- Không đưa Firebase Admin credential xuống browser.
- FCM production receipt/service-worker/VAPID vẫn cần deployment smoke.

## Invitation email

- Email invitation là side effect của Course student import/membership, không phải API gửi mail riêng.
- HTTP import success chỉ xác nhận dữ liệu/import và số invitation enqueue; không xác nhận email đã đến inbox.
- Delivery bất đồng bộ; lỗi Gmail API/config không rollback membership.
- Không dựng provider-mail UI, không gọi Gmail API từ browser và không gọi `/send-mail`.

## Common errors

| Status | FE xử lý |
|---|---|
| `400` | Hiển thị validation/query/file/audience lỗi; không retry mù |
| `401` | Session không còn hợp lệ; đưa user về login |
| `403` | User không có role/ownership hoặc CSRF thiếu/sai; refresh CSRF một lần khi phù hợp |
| `404` | Resource không tồn tại hoặc không thuộc owner |
| `409` | Idempotency-Key bị tái sử dụng khác intent, FID thuộc owner khác, hoặc business conflict |

## Do / Don't

Do:

- Dùng `credentials: "include"` và CSRF cho mutation.
- Refetch Bell API sau push.
- Dùng một Idempotency-Key ổn định cho cùng broadcast/Jira mutation intent.
- Hiển thị import/email state đúng: queued khác sent.

Don't:

- Không dùng Bearer cho browser business API.
- Không gửi actorId/recipientId/FID list trong broadcast.
- Không coi FCM payload là notification history.
- Không invent generic send-mail/send-notification endpoint.
- Không tự coi invitation là Course enrollment hoặc thay đổi DEC-023.

## Remaining deployment checks

- Browser E2E: login, `/me`, `/csrf`, mutation, logout với cookie topology thật.
- Firebase Web: public config/VAPID/service worker, foreground/background receipt và Bell refetch.
- Gmail: outbox chuyển `PENDING/FAILED -> SENT` và kiểm tra inbox/spam.
- Quyết định product về revoke FID khi logout; backend hiện không tự revoke.
