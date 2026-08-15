# SAGA Frontend Handoff

## Avatar / progress / Course weights — 2026-08-15

Current Backend contracts: OpenAPI **150**, migration head **V33**. Browser `JSESSIONID` + `credentials: "include"`; CSRF on unsafe mutations; GET no CSRF; **never Bearer**.

- **Avatar:** render `avatarUrl` từ `GET /api/auth/me` (nullable). Fallback UI khi null. Không gọi Google image API, không gửi provider token/avatar URL.
- **Progress:** `GET /api/v1/courses/{courseId}/students/{studentId}/progress`. MEMBER self only; LEADER own Team members; Lecturer Course owner; Admin retained; MENTOR forbidden.
- **Course weights:** `GET` + `PUT /api/v1/courses/{courseId}/contribution-slice-weights`. Lecturer direct edit exact Course only. Body `{codeWeight, documentWeight, designWeight}` scale 0–100. Mutation gửi CSRF. Normal FE **không** dùng old approval flow.
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
