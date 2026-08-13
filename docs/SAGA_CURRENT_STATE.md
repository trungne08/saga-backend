## J1K.1 TaskType.REQUEST database enum migration — 2026-08-13

- **CONFIRMED_RUNTIME_SCHEMA_MISMATCH:** production-compatible runtime metadata for nullable `task.type` lacked `REQUEST`; Jira reconciliation therefore reached HTTP 200/search success but failed canonical `saveAndFlush` at `UPSERT_ISSUES` with MySQL 1265.
- **IMPLEMENTED_SOURCE_TEST:** V29 minimally expands the MySQL enum to `BUG, EPIC, FEATURE, REQUEST, STORY, SUBTASK, TASK`, retaining nullable `YES` and default `NULL`. Existing values/rows are not rewritten.
- **CONTRACT:** SQL enum values are asserted equal to all `TaskType.values()` so a future Java enum addition without Flyway coverage fails tests. Persistence covers every enum; Request canonical upsert and reconciliation completion have regression coverage.
- **VERIFICATION:** targeted J1K.1/Jira regression is 114/114 PASS. Full `mvnw clean test` ran 880 tests with 4 failures and 0 errors; the remaining failures are the same pre-existing OpenAPI count, Course roster, and two Lecturer Analytics gaps recorded before J1K.1. All J1K.1 and migration-index suites pass.
- **DEPLOYMENT:** source/test completion does not prove production migration. Apply V29 through configured Flyway startup/deployment, then smoke Jira Request/Story and manual reconciliation. No ad-hoc production ALTER.

## J1K Jira Web create/update/Story Point/delete sync — 2026-08-13

- **IMPLEMENTED / CONFIRMED_SOURCE_TEST:** Jira dynamic webhook đã đăng ký issue created/updated/deleted. Receipt chỉ được persist sau Jira authentication và có provider/delivery dedup; create/update gọi canonical reconciliation, không upsert từ raw webhook body. Retry receipt và reconciliation scheduler giữ fallback hiện hữu.
- Generic reconciliation discovery cả Sprint field và board estimation field. Search projection có Story Point exact field; parser nhận whole non-negative string/number và reject fractional/negative/blank/non-numeric/object/array/overflow. Explicit provider null clear `Task.storyPoint`; field omitted không clear local. Không có hardcode tenant `customfield_*`.
- Issue deleted đi qua authenticated receipt rồi tombstone Task theo stable issue ID, fallback key chỉ khi ID thiếu, luôn scoped bằng `JiraBoard.project`. Duplicate/already-deleted và unknown Task là no-op có kiểm soát; cross-Project không chạm dữ liệu. Reconciliation/upsert giữ nguyên `deletedAt`, nên tombstone không bị stale snapshot hồi sinh.
- Safe diagnostics/health/history giữ local-only và không lộ payload/credential. Health hiện có latest receipt ID/event/status/received/processed/safe error cho từng provider và latest Jira webhook-maintenance result; maintenance persist một safe `SyncJobLog OTHER` để history chứng minh success/failure. Không provider-live call. Targeted Jira/webhook/admin regression pass **13 suites / 184 tests / 0 failures / 0 errors / 0 skipped**. Full clean chạy **134 suites / 848 tests / 2 failures / 0 errors / 0 skipped**: baseline Course roster/DEC-023 đã biết và một notification ordering failure ngoài scope; notification suite rerun riêng pass **8/8**. Runtime Jira Cloud/Railway vẫn **TBD_DEPLOYMENT_SMOKE**.

## J1J Jira Task provider-ID ownership / Update Priority business resolution — 2026-08-10

- **CONFIRMED_SOURCE/TEST:** Update Task có thêm field business `priority`; normal FE không cần Jira numeric/provider ID. Backend resolve từ `editmeta.priority.allowedValues` bằng đúng policy exact-name-first/dedup/fail-closed dùng chung với Create. Chỉ sau unique resolution mới PUT `{ "fields": { "priority": { "id": "<resolved-provider-id>" } } }`, rồi canonical GET/upsert/fresh-read mới complete.
- **CONFIRMED_SOURCE/TEST:** `priorityId` giữ backward-compatible advanced override và validation stale hiện hữu. `priority` + `priorityId` trả local `400 JIRA_PRIORITY_INVALID`; field không editable trả `JIRA_EDIT_FIELD_NOT_ALLOWED`; zero/multiple business candidate trả `JIRA_PRIORITY_RESOLUTION_NOT_FOUND`/`JIRA_PRIORITY_RESOLUTION_AMBIGUOUS`; không provider PUT khi local fail.
- **CONFIRMED_SOURCE/TEST:** fingerprint chứa cả hai representation theo raw request intent; replay cùng business body giữ recovery semantics, priority khác hoặc business-vs-explicit đi qua conflict `JIRA_IDEMPOTENCY_KEY_REUSED` của state machine. Sparse fields khác, J1H/J1I, session/CSRF và authorization không đổi.
- **HISTORICAL AUDIT / J1K SUPERSESSION:** Components still use Jira provider IDs without a proven public options contract; Transition round-trips an issue-scoped provider ID; Assignee/Sprint/Delete use local IDs; Estimation uses an integer. The former Issue Type `CONFIRMED_NOT_IMPLEMENTED` gap is closed by the J1K section below.
- **TBD_DEPLOYMENT_SMOKE:** Source/test không phải bằng chứng production; cần deploy và smoke với metadata priority thực tế.

## J1I Jira Estimation 200 response / canonical parser — 2026-08-10

- **CONFIRMED_SOURCE:** provider PUT estimation không deserialize response body. Sau J1H, remote success đã được mark trước canonical GET; lỗi `JIRA_RESPONSE_INVALID` 502 phù hợp với parser local cũ chỉ nhận `JsonNode.isInt()` cho field Story Point đã discovery.
- **Đã hoàn thành:** parser canonical nhận decimal-string/số whole không âm và trả integer SAGA chính xác; Jira value hợp lệ như `"5.0"` không còn bị map 502. Invalid value vẫn fail-safe, operation giữ `REMOTE_SUCCEEDED` và replay cùng key không mutation lại.
- **Không đổi:** không hardcode field ID, không đổi state-machine J1H, scope, auth/session/CSRF, entity hay migration.

## J1H Jira Task Estimation remote-success finalization — 2026-08-10

- **CONFIRMED_SOURCE:** lỗi 409 `JIRA_WRITE_OPERATION_IN_PROGRESS` xảy ra sau Jira estimation thành công khi `markRemoteSucceeded` đã ghi DB bằng transaction riêng nhưng object request vẫn thiếu `remoteResourceId`; `reconcile` chặn object cũ trước canonical GET/upsert.
- **Đã hoàn thành:** estimation nay đồng bộ object cục bộ rồi đọc Jira canonical cùng estimation field đã discovery, upsert và fresh-read `REQUIRES_NEW` xác nhận chính xác Story Point trước `COMPLETED`. Giá trị `0` hợp lệ.
- **Đã hoàn thành:** lỗi canonical hoặc mismatch giữ `REMOTE_SUCCEEDED`; replay cùng key/body không PUT estimation lần hai. Recovery nền không finalize estimation vì fingerprint không cung cấp target intent để xác minh an toàn.

## J1G Jira Task update edit-metadata — 2026-08-10

- **CONFIRMED:** `JIRA_EDIT_FIELD_NOT_ALLOWED` phát sinh local sau editmeta thành công nếu field cần mutate không hiện diện; trước J1G full-form update gửi mọi field non-null.
- **Đã hoàn thành:** suppress title/priority có thể resolve/dueDate/labels/components nếu canonical bằng nhau; description vẫn gửi khi có mặt. Có safe diagnostic field bị chặn, không payload/secret.
- **Đã hoàn thành:** `unassign` có thể omit khi chỉ gửi `assigneeId`; thiếu `Idempotency-Key` trả `400 INVALID_REQUEST`, không 500.

# SAGA — Trạng thái hiện tại

## A13 — Admin advanced gap closure, 2026-08-10

- **IMPLEMENTED trong A13:** không có API mới; audit xác nhận reuse các route shared đã hỗ trợ ADMIN là contract đúng.
- **CONFIRMED:** Course detail/list/CRUD và roster; Team roster; Task/Sprint; Lecturer analytics; Peer Review; Contribution có ADMIN access theo từng controller/service tương ứng. Đây không phải nguyên tắc ADMIN bypass.
- **BLOCKED:** per-user audit history, role mutation, password reset, manual Course membership, notification broadcast và generic evaluation settings. Dashboard charts anomaly/graph-processing nằm ngoài A13.

## A12 — Admin closure, 2026-08-09

- **CONFIRMED:** Core Admin source/test có user management, master-data retention CRUD, typed
  active Semester, Course progress/XLSX export, local operational reads và global
  team/project visibility.
- **PARTIAL:** A11A durable audit identity chỉ forward cho producer có exact local actor; không
  làm historical coverage complete.
- **BLOCKED/TBD:** per-user audit, broadcast notification, impersonation, role/password mutation,
  membership mutation, Project DELETE, generic settings và browser/deployed smoke evidence.

## Cập nhật 2026-08-09 — Account lifecycle M3B

- **CONFIRMED:** Student và Lecturer có AccountStatus; Admin vẫn null/không có schema. Lecturer default ACTIVE qua V21 và provisioning, không re-login thành ACTIVE khi DB là INACTIVE/SUSPENDED.
- **CONFIRMED:** PATCH Admin chỉ thay status Student/Lecturer, same-status idempotent; Admin target, PENDING target và unknown ID fail controlled.
- **CONFIRMED:** filter browser-session tra DB local cho business API; auth me/csrf/logout không bị chặn, `/me` trả current status. Không Cognito call, role/membership/Course/Project/provider mutation.

## Cập nhật 2026-08-09 — AccountStatus M3A audit

- **CONFIRMED:** Admin user union dùng local profile ID; Student có status, Admin/Lecturer trả null và không có schema status.
- **CONFIRMED:** Status trong `SagaPrincipal` là snapshot session sau OIDC login. Hiện không có request-time DB enforcement, SessionRegistry hoặc invalidation session khi status đổi.
- **TBD:** Admin status transition, self-target và access policy cho PENDING chưa có evidence. Do đó PATCH status chưa triển khai.

## Cập nhật 2026-08-09 — Course M2B

- **CONFIRMED:** `PUT`/`DELETE /api/v1/courses/{id}` đã có, ADMIN-only; PUT dùng `CourseRequest`, DELETE trả 204 khi Course không còn dependency.
- **CONFIRMED:** V20 thêm `course.deleted_at`; active read ẩn tombstone, code tombstone vẫn unique. Create/update từ chối Subject/Class/Semester tombstone bằng 404.
- **CONFIRMED:** Guard xóa kiểm tra Team, Project, StudentCourseInvitation, TaskWeightConfig và trả 409; không có cascade/hard delete.
- **PARTIAL:** import resolve Course active-only. Contribution mutation và các resolver
  analytics/roster/Contribution giữ lookup baseline; không refactor chúng trong M2B.

## Cập nhật 2026-08-09 — Semester Update và Soft Delete

- **CONFIRMED:** `SemesterRequest` được tái sử dụng cho PUT nguyên khối; validation code/name/date và `endDate >= startDate` giữ nguyên. Missing/deleted Semester là 404, duplicate code là 409.
- **CONFIRMED:** DELETE là soft-delete V19, active read loại tombstone và repeated delete 404. Course đang tham chiếu gây 409, không detach/cascade/xóa Course.
- **CONFIRMED:** code của tombstone không được tái sử dụng. Course create/read business logic không thay đổi.

## Cập nhật 2026-08-09 — Admin Read Foundation

- **CONFIRMED:** năm Admin-only GET dưới `/api/admin` đã có controller/service; URL rule `/api/admin/**` và method rule đều yêu cầu ADMIN.
- **CONFIRMED:** Users trả localProfileId/role/fullName/email/status/studentCode an toàn, phân trang DB; Audit Mongo chỉ trả id/action/targetEntity/timestamp.
- **CONFIRMED:** Stats/Teams/Projects chỉ dùng repository local. Project chỉ trả Course summary, Jira connectionStatus và GitHub aggregate; không provider call, secret, repository URL hay Project DELETE.

## Update 2026-08-09 — Create Task không cần Jira numeric IDs

### J1C — exact-name-first resolution

- **CONFIRMED runtime:** `Task`/`Spike` cùng map `TASK` và `Critical`/`Highest` cùng map
  `CRITICAL` với provider ID khác nhau, nên J1 dedup ID một mình không loại được ambiguity.
- **CONFIRMED:** sau dedup, canonical name normalize trùng business enum được ưu tiên khi chỉ có
  một ID exact; semantic fallback chỉ resolve khi còn đúng một ID. Nhiều exact hoặc nhiều fallback
  distinct vẫn trả resolution `AMBIGUOUS` trước Jira POST; không pick-first hay hardcode ID.

- **CONFIRMED:** normal `POST /api/v1/projects/{projectId}/tasks` dùng `type` và `priority` business optional để resolve exact Jira IDs từ metadata của Jira Project hiện tại.
- **CONFIRMED:** `issueTypeId`/`priorityId` là advanced explicit override optional và chỉ được dùng sau validation local metadata. Issue type invalid trả `400 JIRA_ISSUE_TYPE_INVALID`; priority override invalid trả `400 JIRA_PRIORITY_INVALID`; không forward ID invalid tới Jira.
- **CONFIRMED:** normal auto-resolution zero/multiple candidate fail closed bằng code resolution cụ thể. Assignee vẫn chỉ resolve `IdentityMap ACTIVE -> externalAccountId`; không thêm Jira-side validation hay scope.

## Cập nhật 2026-08-09 — Task Create canonical confirmation trước COMPLETE

- **CONFIRMED runtime:** DEMO-8 (`remote_resource_id=10009`) và DEMO-9 (`10010`) có operation DB cuối cùng `COMPLETED`, `completed_at` khác null, canonical Task local tồn tại và `safe_error_code=NULL`. WARN `JIRA_WRITE_RECOVERY_REQUIRED` được ghi sau `completed_at`, không chứng minh DB bị stuck `REMOTE_SUCCEEDED`.
- **CONFIRMED source/fix:** Task Create xác nhận `Task(projectId, externalId)` sau canonical GET/upsert rồi mới chuyển operation `COMPLETED`. Khi confirmation không có Task, response là `JIRA_WRITE_RECOVERY_REQUIRED`, operation giữ `REMOTE_SUCCEEDED` và không Jira POST lặp; same Idempotency-Key chỉ canonical recovery. `COMPLETED` thiếu Task local fail-safe, không mutation remote.
- **Không đổi:** reconciliation không finalize write operation; metadata resolution, auth/session/CSRF, Jira scope, webhook, Sprint, entity/schema/migration không đổi.

## Update 2026-08-08 — Jira Sprint state trong list response

- **CONFIRMED:** hai list route Project/Team cùng dùng `SprintSummaryResponse`; mỗi item nay có `state` (`String` nullable) lấy trực tiếp từ canonical local `Sprint.state`. Các giá trị Jira được giữ nguyên, gồm `future`, `active`, `closed`; không tạo business state mới, không suy diễn theo ngày và không gọi Jira provider khi GET.
- **CONFIRMED:** `response.state` cấp danh sách vẫn là `PROJECT_NOT_CREATED` / `EMPTY` / `READY`; `response.sprints[i].state` là trạng thái của Sprint cụ thể. Start/Close write-through hiện hữu làm canonical state đổi, nên GET list kế tiếp phản ánh `active` hoặc `closed`.
- **Verification:** Project/Team list, state future/active/closed, canonical state transition mapping và generated OpenAPI schema được kiểm thử; không thay entity/schema/migration, authorization, provider hay Jira write flow.

## Update 2026-08-08 — CORS preflight cho Jira Task/Sprint idempotency

- **CONFIRMED:** CORS giữ explicit origin allowlist, `allowCredentials=true` và methods `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`. Allowed request headers là `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`; không dùng wildcard.
- **CONFIRMED:** mọi Jira Task/Sprint mutation vẫn bắt buộc `Idempotency-Key`. Vì FE có thể gọi cross-origin bằng browser session với `credentials: "include"`, preflight nay cho phép header này trước khi POST/PUT/PATCH/DELETE tới controller; không thêm Bearer và không đổi session, CSRF hay authorization.
- **Verification:** `SecurityIntegrationTest` kiểm chứng preflight cho cả `/api/v1/projects/{projectId}/sprints` và `/api/v1/projects/{projectId}/tasks`, gồm origin cấu hình, credentials, POST và các request header viết thường theo HTTP case-insensitive semantics. Targeted Maven: 95 tests / 0 failures / 0 errors / 0 skipped.

## Update 2026-08-07 — Jira simple-board Sprint capability probe

- **CONFIRMED runtime:** SDP board `35`, `type=simple`, association `10034/SDP`; Board Features rỗng và Project Features không expose Sprint identifier hữu dụng. Vì vậy metadata feature không còn là nguồn quyết định Sprint capability.
- **CONFIRMED source:** simple board được probe bằng read-only `GET /rest/agile/1.0/board/{boardId}/sprint?maxResults=1` qua 3LO, với `read:sprint:jira-software`. HTTP 200 cùng page hợp lệ (kể cả `values=[]`) xác nhận endpoint-supported; không hydrate, upsert hay log Sprint item.
- **Policy sau patch:** Scrum resolve trực tiếp, không probe. Simple chỉ resolve/persist nếu probe supported; 400 fail closed `JIRA_SPRINT_CAPABILITY_UNCONFIRMED`. 401/403/404/429/5xx-network/malformed-2xx giữ category an toàn tương ứng. Association project vẫn bắt buộc và nhiều candidate trả `JIRA_BOARD_SELECTION_REQUIRED`.
- **Diagnostics:** resolver ghi project/board/type, HTTP status/result probe, candidate reason và kết quả `REJECTED` hoặc `SELECTED`, không raw provider body hay credential. Browser session + CSRF, retained-row relink và Project Manager mutation rule không đổi.
- **TBD production:** deploy và relink SDP để xác nhận probe board 35 trả 200 hợp lệ; khi đó SAGA sẽ persist `35` và Create Sprint dùng origin board này. Full Maven pass: 99 suites / 586 tests / 0 failures / 0 errors / 0 skipped.

## Update 2026-08-07 — Jira relink provider-identity-aware upsert

- **CONFIRMED:** `jira_board` vừa là history anchor theo ownership local `project_id`, vừa có provider identity duy nhất `(cloud_id, jira_project_id)`. Disconnect vẫn giữ row/history nhưng retire credential và webhook state.
- **CONFIRMED:** sau fresh grant, verified resource, scope preflight, canonical Jira Project và Scrum board discovery, relink resolve/lock cả hai identity trong transaction local ngắn. Cùng Project + cùng provider identity luôn update row retained/canonical, giữ `JiraBoard.id` và references; không INSERT duplicate.
- **CONFIRMED:** provider identity đã thuộc SAGA Project khác trả `409 JIRA_PROJECT_ALREADY_LINKED` với message an toàn “This Jira project is already linked to another SAGA project”; không chuyển ownership, không move/xóa Task, Sprint, SyncJobLog hoặc JiraWriteOperation. Retained row muốn đổi sang provider identity khác trả `409 JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED`, không overwrite history anchor.
- **CONFIRMED:** provider I/O không giữ pessimistic DB lock. Race unique constraint được retry bằng local reload/upsert; nếu không thể reconcile thì trả safe `409 JIRA_BOARD_UPSERT_CONFLICT`, không trả SQL/constraint/raw DB error. Test đa luồng xác nhận cùng Project coalesce một row và hai Project tranh cùng external Jira Project chỉ có một owner.
- **Migration:** không thêm migration và giữ `uk_jira_cloud_project` để bảo vệ provider identity uniqueness.
- **Verification:** `./mvnw.cmd clean test` hoàn tất **BUILD SUCCESS**: 99 suites / 560 tests / 0 failures / 0 errors / 0 skipped.
- **TBD runtime:** cần deploy rồi smoke `disconnect → fresh OAuth consent → relink`, cross-project conflict và concurrent relink trên môi trường production; FE tiếp tục dùng browser session + CSRF, không gửi Bearer/provider credential.

## Update 2026-08-07 — Jira OAuth scope và 3LO gateway

- **CONFIRMED:** 3LO Jira Platform và Agile API dùng `api.atlassian.com/ex/jira/{cloudId}`, không dùng trực tiếp `{site}.atlassian.net` với bearer 3LO. `cloudId` chỉ được dùng sau khi match resource từ `accessible-resources` của fresh OAuth grant.
- **CONFIRMED:** `/jira/link` preflight đúng bốn scope dùng tại link: `read:jira-work`, `manage:jira-webhook`, `read:board-scope:jira-software`, `read:project:jira`. Token chỉ có `read:jira-work` nhưng thiếu board scope bị chặn bằng `JIRA_SCOPE_INSUFFICIENT`; link không bị chặn chỉ vì thiếu Sprint/Task write-delete scope không dùng ở link.
- **CONFIRMED:** error mapping sau preflight vẫn là 401 → `JIRA_ACCESS_REVOKED`, 403 → `JIRA_ACCESS_FORBIDDEN`, Sprint 404 → `JIRA_SPRINT_NOT_FOUND`, 429 → `JIRA_RATE_LIMITED`, network/5xx → `JIRA_PROVIDER_UNAVAILABLE`.
- **CONFIRMED:** authorization request mặc định yêu cầu bộ scope as-built gồm classic Platform/webhook, `offline_access`, và Jira Software board/sprint/issue scopes vì source dùng các operation đó. `offline_access` không phải site product scope. Giá trị deploy vẫn có thể override qua property `app.integrations.jira.scopes`; override thiếu capability SAGA sẽ bị chặn trước khi mở project Jira OAuth.
- **Verification:** `./mvnw.cmd clean test` hoàn tất **BUILD SUCCESS**: 97 suites / 549 tests / 0 failures / 0 errors / 0 skipped.
- **TBD:** production smoke test chỉ có giá trị sau deploy, bật scope trong Developer Console và OAuth re-consent mới; FE không gửi Bearer/provider token.

## Update 2026-08-06 — Jira hydration/disconnect/relink hardening

- **CONFIRMED:** reconciliation hydrate Sprint từ cả issue batch (`ISSUE_BATCH`) và local active Sprint (`LOCAL_SPRINT`), kể cả khi search Jira trả HTTP 200/0 issues hoặc local Sprint chưa có canonical dates.
- **CONFIRMED:** `getSprint` trả safe category riêng: 401 `JIRA_ACCESS_REVOKED`, 403 `JIRA_ACCESS_FORBIDDEN`, 404 `JIRA_SPRINT_NOT_FOUND`, 429 `JIRA_RATE_LIMITED`, 5xx/network `JIRA_PROVIDER_UNAVAILABLE`. 404 Sprint không bị gộp thành access revoked.
- **CONFIRMED:** một Sprint hydration lỗi không corrupt Sprint khác; finalization là `PARTIAL_FAILURE`, cursor không advance. Safe diagnostics chứa board/project/Sprint/status/category/job/stage/source, không chứa token hay raw provider body.
- **CONFIRMED:** disconnect không hard-delete `jira_board`, Task, Sprint hay history; nó retire credential/webhook state. Relink dùng fresh OAuth grant và retained row; OAuth state cũ cùng project bị vô hiệu. Scheduler, claim và state-write không chạy/resurrect integration `DISCONNECTED`.
- **PARTIAL:** chưa có concurrent-relink integration test đa luồng thực sự.
- **TBD:** externalSprintId/upstream status của incident lịch sử và policy xử lý Sprint đã bị xóa trên Jira.
- **Known limitation:** local Sprint 404 chưa tự tombstone; reconciliation có thể tiếp tục retry/ghi `PARTIAL_FAILURE` cho đến khi có policy cleanup an toàn.
- **Verification:** `./mvnw.cmd clean test` — `BUILD SUCCESS`, 97 suites / 546 tests / 0 failures / 0 errors / 0 skipped.

## Update 2026-08-06 - Swagger/OpenAPI tiếng Việt

- Generated `/v3/api-docs` hiện có 96 operation được audit trực tiếp. Mọi operation sinh ra có summary/description tiếng Việt và ít nhất một tag tiếng Việt có description.
- Nhóm tag bao phủ xác thực, master data, nhóm/dự án, Jira Task/Sprint, GitHub, tích hợp, đồng bộ, đóng góp, đánh giá, webhook và chính sách riêng tư. Controller nghiệp vụ có `@Tag` tiếng Việt tương ứng.
- Swagger giữ browser session, `withCredentials` và CSRF interceptor hiện hữu; không thêm Bearer token, OAuth input giả hay CSRF header lặp. `Idempotency-Key` vẫn chỉ xuất hiện ở mutation Jira có source evidence.
- Full Maven: 97 suites / 538 tests / 0 failures / 0 errors / 0 skipped. Kiểm chứng UI trên môi trường production vẫn TBD.

## Update 2026-08-06 - Project read and integration completion

- Project update supports `name` and nullable `description`; migration `V18__add_project_description.sql` adds `project.description` as `MEDIUMTEXT`.
- `GET /api/projects/{projectId}/dashboard-stats` returns generated-at time, task total/completed/incomplete/percentage, and local GitHub repository/commit/pull-request counts. It never calls a provider.
- GitHub repository detail reads are session-authenticated backend calls: `GET /api/projects/{projectId}/github/repositories/{repositoryId}/branches?page=0&size=20` and `GET /api/projects/{projectId}/github/repositories/{repositoryId}/commits?branch=feature/x&page=0&size=20`. Pages allow `size` 1..100; invalid branch/page input is rejected without a provider call.
- `POST /api/projects/{projectId}/github/repositories/{repositoryId}/connect` returns 202 only for a disconnected repository whose installation still contains that repository. Repeated connect returns `GITHUB_RECONNECT_NOT_REQUIRED`; missing/revoked installation returns `GITHUB_RECONNECT_REQUIRES_INSTALLATION`.
- Sync history is finalized as `GET /api/projects/{projectId}/sync-history`; it is paged/filterable and scoped to this project's Jira/GitHub targets. `/sync-status` remains manager-only compact top-20 compatibility/status data.
- Project DELETE is intentionally absent: the dependency guard is not proven safe across current inbound references, so no destructive API was introduced.
- Full Maven: 96 suites / 537 tests / 0 failures / 0 errors / 0 skipped. Production provider connectivity is TBD.

## Cập nhật 2026-08-06 — Jira Sprint time và board

- **CONFIRMED:** HTTP Sprint dùng Instant có offset rõ ràng; FE dùng `Intl.DateTimeFormat`, không cộng cứng `+7`.
- **CONFIRMED:** Jira link discover Agile board theo project canonical, persist external numeric ID (không phải UUID local). Zero/multiple Scrum board trả `JIRA_SCRUM_BOARD_NOT_FOUND`/`JIRA_BOARD_SELECTION_REQUIRED`; legacy ID thiếu/sai được lazy-repair.
- **CONFIRMED:** numeric board ID là `originBoardId`; config invalid không gọi Jira Create Sprint. UTC chỉ chuyển sang `JIRA_TIME_ZONE` khi sinh JQL. Safe provider errors và idempotency/recovery không đổi; không raw payload log/API.
- **Migration:** không thêm vì `jira_board_id` đã có. **Tests:** full Maven tại `c770438` pass 94 suites / 529 tests / 0 failures / 0 errors / 0 skipped. Runtime production smoke test TBD.

## 1. Thông tin checkpoint

| Mục | Giá trị |
|---|---|
| Branch được audit | `main` |
| Commit được audit | `4f3dee9` (`4f3dee969ebd7ee03a94eb1b8133987ad622c66d`); các SHA cũ bên dưới là checkpoint lịch sử |
| Ngày cập nhật | 2026-08-06 (Asia/Saigon, UTC+07:00) |
| Working tree hiện tại | Sạch trước task; task chỉ cập nhật bốn Markdown, không sửa source/test/config/migration |
| Phạm vi thay đổi của task | Source/config/test ở HEAD và working tree là bằng chứng mạnh nhất |

> Lưu ý lịch sử: các đoạn phía dưới gắn với SHA và test count cũ là snapshot của
> audit cũ; mục 2026-08-06 dưới đây supersede chúng khi mô tả trạng thái hiện hành.

## Update 2026-08-06 — Admin master data, Jira Task/Sprint và Student profile

### A. Admin Master Data

- **CONFIRMED:** Subject và Class có ADMIN-only `PUT /api/v1/subjects/{id}`,
  `DELETE /api/v1/subjects/{id}`, `PUT /api/v1/classes/{id}` và
  `DELETE /api/v1/classes/{id}`. DELETE là soft delete bằng `deletedAt`, không
  cascade/hard delete. GET detail/list và lookup chỉ trả record active.
- **CONFIRMED:** Course dependency guard trả 409 khi Subject/Class còn được Course
  sử dụng. Code đã tombstone vẫn bị unique check chặn tái sử dụng. V15 thêm
  `subject.deleted_at`; V16 thêm `class.deleted_at`.
- **TBD:** Semester/Course Update + soft delete chưa có trong controller/service.
  Manual Course student add/remove và Course enrollment độc lập cũng chưa có.

### B. Jira Task Read

- **CONFIRMED:** `GET /api/v1/projects/{projectId}/tasks` và
  `GET /api/v1/projects/{projectId}/tasks/{taskId}` đọc local canonical Task snapshot.
- List hỗ trợ `keyword` (externalKey/title), `sprintId`, `assigneeId`, `status`;
  `sortBy` chỉ nhận `externalKey|title|status|priority|storyPoint|dueDate|externalUpdatedAt`,
  `sortDirection=asc|desc`, mặc định `externalKey/asc`; `page=0`, `size=20`, size
  hợp lệ 1..100. Sort có tie-break `id`; Task soft-deleted bị loại khỏi list/detail.
- ADMIN đọc mọi Project; LECTURER đọc Project thuộc Course mình phụ trách; STUDENT
  đọc Project của Team mình. Anonymous 401; ngoài scope 403.

### C. Jira Task/Sprint Management

**Task routes được source xác nhận:**

- `POST /api/v1/projects/{projectId}/tasks`
- `PUT /api/v1/projects/{projectId}/tasks/{taskId}`
- `DELETE /api/v1/projects/{projectId}/tasks/{taskId}`
- `GET|POST /api/v1/projects/{projectId}/tasks/{taskId}/transitions`
- `PUT /api/v1/projects/{projectId}/tasks/{taskId}/assignee`
- `PUT /api/v1/projects/{projectId}/tasks/{taskId}/sprint` (Sprint hoặc backlog)
- `PUT /api/v1/projects/{projectId}/tasks/{taskId}/estimation`

**Sprint routes được source xác nhận:**

- `GET|PUT|DELETE /api/v1/projects/{projectId}/sprints/{sprintId}`
- `POST /api/v1/projects/{projectId}/sprints`
- `POST /api/v1/projects/{projectId}/sprints/{sprintId}/start`
- `POST /api/v1/projects/{projectId}/sprints/{sprintId}/close`
- `GET /api/v1/projects/{projectId}/sprints`
- `GET /api/v1/teams/{teamId}/sprints`

- **CONFIRMED:** Jira là source of truth; SAGA DB là canonical snapshot/read model.
  Mutation dùng browser session + CSRF, bắt buộc `Idempotency-Key`, lấy actor từ
  `SagaPrincipal`, gọi Jira trước rồi canonical fetch/upsert local. Provider dùng
  metadata/discovery, không hardcode `customfield_*` trong production source.
- **CONFIRMED:** persisted Jira write operation hỗ trợ recovery; không blind retry
  Create/Delete/Transition khi outcome không rõ và không lưu token/raw provider
  payload. Duplicate idempotency claim reload trong transaction mới sau transaction
  insert bị rollback.
- **CONFIRMED:** Task delete tombstone Task. Sprint delete gỡ association `Task.sprint`
  rồi tombstone Sprint; audit, Contribution và Peer Review data không bị hard-delete.

### D. Sprint date sync

- **CONFIRMED:** `startDate`, `endDate`, `completeDate` từ full Jira Agile Sprint
  response được normalize UTC. Canonical full snapshot được phép set/null cả ba ngày;
  embedded Sprint trong Issue chỉ cập nhật association/reference và không clear ngày.
- Backfill, reconciliation và webhook dùng chung sync/hydration; tập Sprint id là
  distinct nên mỗi ID fetch tối đa một lần/job, đồng thời local Sprint hiện hữu có
  null dates vẫn được repair.
- **Known limitation:** Jira canonical Sprint response không có `updated/version`;
  hai canonical snapshot cạnh tranh dùng last-processed-wins.

### E. Production startup schema fix

- **CONFIRMED từ source/schema:** V17 tạo `jira_write_operation`, thêm Sprint state/
  complete date và Task/Sprint tombstone. Entity mapping của `actor_profile_id` và
  `request_fingerprint` dùng explicit JDBC `CHAR`, khớp convention CHAR của V17.
  Không sửa V17 và không có V18.
- **TBD runtime production:** repository không có deploy log chứng minh đủ
  `Initialized JPA EntityManagerFactory`, `Started BeApplication` và health HTTP 200.

### F. Student Basic Info

- **CONFIRMED:** `GET /api/v1/courses/{courseId}/students/{studentId}` dùng browser
  session, GET không cần CSRF. ADMIN đọc mọi Course; LECTURER chỉ Course mình được
  phân công; STUDENT 403; anonymous hoặc Bearer-only 401.
- DTO: `courseId`, `studentId`, `studentCode`, `fullName`, `email`, nullable
  `avatarUrl`, `accountStatus`, `team { teamId, teamName, roleInTeam }`.
  `avatarUrl` luôn null vì `Student` chưa có nguồn avatar. `accountStatus` là
  `ACTIVE|INACTIVE|SUSPENDED|PENDING`, không phải Course enrollment status;
  `roleInTeam` là `LEADER|MEMBER|MENTOR`.
- Membership chỉ được xác định qua `TeamMember -> Team -> Course`: Course/student/
  membership không phù hợp trả 404; nhiều legacy membership trong cùng Course trả
  409 và không tự sửa dữ liệu. Không dùng Contribution. Student thuộc Course nhưng
  chưa có Team chưa được hỗ trợ vì không có `CourseEnrollment` độc lập.

### G. Tests

- **CONFIRMED từ Surefire reports:** full Maven gần nhất có **90 suites, 504 tests,
  0 failures, 0 errors, 0 skipped**. Targeted Course Student Basic Info + roster có
  19/19 test đạt. Task documentation-only này không chạy Maven lại.

## Update 2026-08-04 — Contribution engine and Jira task snapshots

- **CONFIRMED:** V9 stores a nullable canonical Jira description and JSON
  component snapshot (`id`/`name`) on `Task`. Search requests these fields and
  upsert replaces them; labels retain their existing V8 replace-all behavior.
- **CONFIRMED:** internal read-only Contribution calculation implements scores
  from mapped commits, Documents, DONE Tasks and PeerReview, with `BigDecimal`.
  Null Task story point contributes one. Results are calculated on demand and
  are not persisted.
- **SUPERSEDED 2026-08-06:** nhận định field id bị hard-code là trạng thái cũ;
  source hiện discovery Jira sprint/estimation field id và không hardcode
  `customfield_*` trong production code.
- **TBD:** peer config precedence, persisted contribution overrides, specified
  final-distribution edge cases, classification rules and Contribution API actor
  policy.
- **Historical verification tại checkpoint 2026-08-04:** targeted Jira/persistence/
  repository/calculation tests: 5 suites, 33 tests; full Maven: 70 suites, 299 tests.
  Số liệu hiện hành nằm tại mục G của update 2026-08-06.

## 2. Đã hoàn thành

- **CONFIRMED:** Spring Security OAuth2/OIDC với Cognito, browser session `JSESSIONID`, profile local và role mapping đã có implementation. Evidence: `SecurityConfig#securityFilterChain`, `CognitoAuthenticationSuccessHandler#onAuthenticationSuccess`, `AuthenticatedProfileService#synchronize`.
- **CONFIRMED:** `/api/auth/login`, `/api/auth/me`, `/api/auth/csrf` và logout Spring Security tồn tại. Evidence: `AuthController`, `SecurityConfig#securityFilterChain`.
- **CONFIRMED:** application roles là `ADMIN`, `LECTURER`, `STUDENT`; team roles là `LEADER`, `MEMBER`, `MENTOR`. Evidence: `ApplicationRole`, `RoleInTeam`.
- **CONFIRMED:** master-data Class/Course/Subject/Semester có API read/create; create được bảo vệ bằng ADMIN. Evidence: bốn controller master-data và `@PreAuthorize`.
- **CONFIRMED:** Jira và GitHub có code OAuth/App, linking, webhook, sync/backfill, reconciliation và encrypted secret handling. Evidence: `integration/callback`, `integration/project`, `integration/provider`, `integration/webhook`, `integration/sync`, `IntegrationSecretCipher`.
- **CONFIRMED:** MySQL/JPA là store domain chính; MongoDB lưu `SystemAuditLog`. Evidence: `application.properties`, entities/repositories.
- **CONFIRMED:** `GET /privacy` public cho anonymous và mọi role, trả HTML UTF-8 từ `static/privacy.html`; exact matcher trong `SecurityConfig` không mở wildcard, không đổi OAuth/session/CSRF/CORS. Contact public được validate từ `app.privacy.contact-url` / `PRIVACY_CONTACT_URL`; deploy phải cấu hình URL contact thực. Evidence: `PrivacyPolicyController`, `SecurityConfig`, `PrivacyPolicyIntegrationTest`.
- **CONFIRMED/PARTLY SUPERSEDED:** Jira issue labels vẫn là immutable replace-all
  snapshot và webhook dùng shared reconciliation; không có Label entity. Từ
  2026-08-06 đã có Task HTTP list/detail, frontend labels response và SAGA→Jira
  Task create/update write-through API. Evidence hiện hành nằm tại update phía trên.
- **PARTIAL:** import Excel sinh viên có authorization course scope, transaction rollback, identity bind an toàn, invitation outbox và application guard một Student/một Team/mỗi Course. Parser/header-preview/error DTO và database invariant trực tiếp Student+Course chưa hoàn chỉnh. Evidence: `CourseController#importStudents`, `CourseImportAuthorizationService`, `ExcelImportService#importStudentsToCourse`, `AuthenticatedProfileService`, `StudentInvitationOutboxService`.

## 3. Đã kiểm chứng

| Hạng mục | Cách kiểm chứng | Kết quả |
|---|---|---|
| Import authorization integration test | `-Dtest=CourseImportSecurityIntegrationTest test` | 13 tests, 0 failures/errors/skips; `BUILD SUCCESS` |
| Existing Security integration test | `-Dtest=SecurityIntegrationTest test` (three repeated runs) | 13 tests/run, all pass |
| Maven test suite (checkpoint lịch sử 2026-08-04) | `./mvnw.cmd test` | 70 suites, 299 tests; đã bị số liệu update 2026-08-06 supersede |
| Jira labels targeted regression | provider, upsert, H2 persistence, dispatcher, webhook processor | 37 tests, 0 failures/errors/skips; `BUILD SUCCESS` |
| Privacy/security/integration regression | `PrivacyPolicyIntegrationTest`, `PrivacyPolicyControllerTest`, `SecurityIntegrationTest`, `SwaggerUiCsrfIntegrationTest`, Jira/GitHub callback/security tests | 32 tests, 0 failures/errors/skips; `BUILD SUCCESS` |
| Checkpoint trước Swagger-CSRF commit | mốc audit trước đó | 51 suites, 228 tests, 0 failures, 0 errors, 0 skipped; không phải số liệu hiện tại |
| Source/test audit count | quét `src/main` và `src/test` | 15 REST controllers; 1 `@RestControllerAdvice`; 45 controller HTTP methods; 6 `@PreAuthorize`; 0 `@Secured`; 62 test source classes |
| Self-team/roster/security regression | targeted integration tests | 6 suites, 62 tests, 0 failures/errors/skips; gồm endpoint self-team, roster cũ, guard, project authorization, security và import |
| Compile | Maven compile trong test lifecycle | 229 main source files và 44 test source files compile thành công |
| Security/CSRF/CORS | `SecurityIntegrationTest` | 16 tests pass, gồm anonymous 401 cho protected API, role 403, CSRF, preflight và logout framework-managed |
| Profile/OIDC | `AuthenticatedProfileServiceTest`, `OidcIdentityServiceTest`, security tests | pass trong Maven suite |
| Jira/GitHub/webhook/sync | các unit/integration tests trong `src/test/java/com/saga/be/integration/**` | pass trong Maven suite |
| Cognito account-linking Lambda | `npm.cmd test` trong `infra/lambda/cognito-account-linking` | 23 tests pass, 0 fail/skipped/cancelled |
| Health/Swagger/login runtime trên Railway | Không kiểm tra dashboard/runtime trong task này | TBD; không suy ra từ test local |

## 4. Đang thực hiện

- Cập nhật sáu tài liệu làm checkpoint/source-of-truth kỹ thuật cho các lượt tiếp theo.
- Import Excel ở trạng thái **PARTIAL** vì validation/parser/preview/error DTO; scope authorization, identity binding, outbox và Gmail adapter đã hoàn thành ở source/test. Production Gmail delivery vẫn chờ deployment smoke.

## 5. Chưa hoàn thành

- Import Excel chưa có download template, preview, validation toàn file/nhóm và DTO lỗi theo dòng. Authorization ADMIN/lecturer ownership, rollback, identity binding và invitation dedup đã có test.
- Chưa có application API đầy đủ cho nhiều entity assessment/risk/meeting/notification/AI dù entity đã tồn tại.
- Chưa chứng minh session persistence qua Railway redeploy hoặc horizontal scaling.
- Chưa có runtime E2E browser test cho localhost frontend → Railway backend với third-party cookie/CSRF.
- Chưa xác nhận hạ tầng Cognito thực tế đã gắn Lambda trigger/Google IdP đúng với code repository.

## 6. Known issues

1. **Medium:** import tạo Student `PENDING` không có `cognitoSub` cho tới login đầu tiên; source không chứng minh deployed Cognito self-sign-up/Google configuration. Evidence: `ExcelImportService#importStudentsToCourse`, `AuthenticatedProfileService#synchronize`.
2. **High:** parser chỉ dựa extension, sheet đầu tiên và index cột; thiếu header, identity, group-leader, row-limit và formula validation. Evidence: `ExcelImportService`.
3. **Medium:** localhost và Railway khác site; browser có thể block credential cookie. FE không đọc được cookie backend bằng `document.cookie`; `/api/auth/csrf` JSON là cơ chế hiện có nhưng vẫn cần E2E browser test. Evidence: `CorsConfig`, `SecurityConfig#csrfTokenRepository`, `AuthController#csrf`.
4. **Medium:** session mặc định không có shared store trong code; restart/multi-instance có nguy cơ mất hoặc lệch session. Evidence: `SecurityConfig#securityContextRepository`, không thấy Spring Session dependency/config.
5. **Medium:** error contract chưa đồng nhất cho Bean Validation, `ResponseStatusException` và uncaught import error. Evidence: `GlobalExceptionHandler`.
6. **Low:** `accountStatus` là null hợp lệ cho ADMIN/LECTURER; FE không được biến JSON null thành chuỗi `"null"`. Evidence: `AuthenticatedProfileService#toProfile(Admin/Lecturer)`, `AuthMeResponse#from`.

## 7. Bước tiếp theo theo thứ tự ưu tiên

| Ưu tiên | Việc cần làm | Lý do | File liên quan | Cách kiểm chứng |
|---|---|---|---|---|
| P0 | Hoàn thiện import Excel validation/preview/error DTO | Scope authorization, rollback, idempotency và identity bind đã có; parser contract vẫn thiếu | `ExcelImportService`, DTO | validation/preview/row-error tests |
| P0 | Deploy và smoke Gmail REST API | Adapter/config source đã có; delivery thật vẫn TBD | deployment secrets/outbox/log | thư thật, inbox/spam, FAILED→SENT retry verification |
| P0 | Browser E2E cookie/CORS/CSRF | localhost→Railway có third-party-cookie risk | `CorsConfig`, `SecurityConfig`, profiles | login→me→csrf→mutation trên browser thật |
| P1 | Chuẩn hóa error response | FE cần contract ổn định | `GlobalExceptionHandler`, DTO | MockMvc contract tests cho 400/404/409/500 |
| P1 | Xác minh session deployment topology | Tránh mất session khi redeploy/scale | Railway config và session config | restart/replica test trên môi trường staging |
| P1 | Đưa test vào Railway/CI build | `railway.json` đang build với `-DskipTests` | `railway.json`, CI TBD | pipeline fail khi test fail |

## 8. Runtime facts do người dùng cung cấp

> Các mục sau **không phải code evidence**; đây là runtime facts do người dùng cung cấp và chưa được task này tái kiểm chứng bằng dashboard/log production.

- Frontend development origin: `http://localhost:3000`.
- Backend production origin: `https://saga-backend-production-3951.up.railway.app`.
- Frontend success route dự kiến: `http://localhost:3000/auth/callback`.
- `/privacy` đã public thành công.
- Atlassian Distribution đã ở trạng thái Sharing.
- Privacy Policy URL đã được cấu hình. Giá trị URL không được lưu trong tài liệu.
- Cognito OAuth callback thuộc Backend: `/login/oauth2/code/cognito` (đồng thời được code config chứng minh về path).
- Tài khoản test ADMIN và LECTURER đã đăng nhập thành công.
- `/api/auth/me` đã trả đúng `applicationRole` cho hai tài khoản trên.
- Jira và GitHub sync trước đó đã được người dùng kiểm tra thành công.

Không lưu username, password, token hoặc secret của tài khoản test trong tài liệu.

## 9. Checkpoint copy nhanh

```text
ĐÃ HOÀN THÀNH: OIDC/session/profile/roles; master data; team authorization; Jira/GitHub integration; CSRF/CORS; health/OpenAPI configuration.
ĐÃ KIỂM CHỨNG (snapshot lịch sử): full Maven 70 suites / 299 tests; số hiện hành là 90 suites / 504 tests / 0 failures / 0 errors / 0 skipped theo update 2026-08-06.
ĐANG LÀM: Xác minh runtime production sau deploy cho stale GitHub job và optimistic locking.
CHƯA LÀM: Import production-ready validation/provider delivery; browser E2E localhost→Railway; session scaling/redeploy verification.
VẤN ĐỀ ĐANG MỞ: Import validation/identity; third-party cookie; error contract; session store; hạ tầng Cognito/Railway còn TBD.
BƯỚC TIẾP THEO: Chốt parser/error DTO, policy email exposure và provider email, chạy E2E cookie/CSRF.
BASE HEAD của snapshot cũ: `0bc30be`. HEAD audit hiện hành là `4f3dee9`; endpoint Student self-scoped tại `250f514` và `200d866` là checkpoint lịch sử.
```

## Update 2026-08-04 — OAuth completion callback redirect

- **CONFIRMED:** Jira common, personal GitHub, project GitHub and GitHub provider-alias completion callbacks return `302` to `app.integration.callback-redirect-uri` with only `resultId`.
- **CONFIRMED:** Safe callback summaries are in current HTTP session for `app.integration.callback-result-ttl` (default `PT5M`), bounded to ten and consumed once by authenticated, CSRF-protected POST. Invalid/missing/replayed state still fails closed.
- **TBD:** Browser E2E confirmation for cross-site cookie and multi-instance session behavior.

## Cập nhật 2026-08-04 — Sync UTC và GitHub concurrency

- **Đã hoàn thành / CONFIRMED:** `SyncStatusResponse.Job.startedAt/completedAt` là `Instant`, JSON UTC có `Z`; entity/schema `SyncJobLog` vẫn `LocalDateTime`/`DATETIME(6)` với UTC semantics. Write path SyncJobLog dùng UTC Clock có chủ đích.
- **Đã hoàn thành / CONFIRMED:** `GitHubSyncJobService` claim cùng GitRepo bằng `PESSIMISTIC_WRITE`; `GitRepoStateService` reload managed row trong `REQUIRES_NEW`; `SyncJobFinalizationService` finalize theo jobId, idempotent và độc lập với degrade; `SyncJobStaleRecoveryScheduler` recover job GitHub stale.
- **Đã kiểm chứng tại checkpoint lịch sử:** targeted GitHub concurrency/recovery 40 tests; full Maven 70/299. Số full Maven hiện hành là **90 suites / 504 tests / 0 failures / 0 errors / 0 skipped**.
- **Chưa hoàn thành / TBD:** xác minh sau deploy rằng row production cũ được recover và không còn optimistic-lock/async uncaught exception. Không migration/schema change; OAuth/session/CSRF/CORS/webhook không đổi.

## 10. Update — provisioning, invitation, roster và Swagger CSRF

- **CONFIRMED:** ADMIN import mọi Course; LECTURER chỉ import Course mình là instructor; STUDENT bị từ chối; mutation vẫn cần JSESSIONID và CSRF.
- **CONFIRMED:** Imported Student `PENDING` được bind bằng subject, hoặc bằng cặp email verified + studentCode cùng trỏ tới đúng một record unlinked. Bind giữ nguyên Course/Team/RoleInTeam, không tạo Student/TeamMember mới và chuyển chỉ `PENDING` sang `ACTIVE`.
- **CONFIRMED:** Outbox `student_course_invitation` có dedup `studentId + courseId + invitationType`, gửi sau commit, ghi `SENT`/`FAILED`, retry tối đa năm lần. Nội dung email không chứa password, token, session hoặc CSRF.
- **CONFIRMED:** V6 tạo outbox với unique database key Student/Course/type; V7 bổ sung/backfill `student.version` an toàn để Hibernate `validate` có thể chạy sau migration. Worker chỉ reclaim `PROCESSING` stale theo timeout cấu hình, không gửi lại `SENT`; semantics là at-least-once.
- **CONFIRMED:** `POST /api/auth/logout` là Spring Security framework-managed, cần `X-XSRF-TOKEN`; CSRF hợp lệ trả 302 Cognito logout, thiếu/sai trả 403. Swagger fetch có thể báo `Failed to fetch` khi theo cross-origin redirect; browser dùng top-level form/navigation.
- **CONFIRMED:** Swagger dùng `withCredentials`, cookie `XSRF-TOKEN` và interceptor same-origin chỉ cho POST/PUT/PATCH/DELETE; không có Bearer application API.
- **CONFIRMED:** `GET /api/v1/courses/{courseId}/teams/{teamId}/members` trả `Page<TeamMemberResponse>`; ADMIN mọi Team, Lecturer Course mình dạy, Student đúng Team (LEADER/MEMBER), 401/403/404; response không có email/cognitoSub/version.
- **PARTIAL/TBD:** Không có production mail provider trong `pom.xml` hay source; adapter mặc định đánh dấu failed an toàn. Parser/preview/error DTO, Cognito self-sign-up deployed và database invariant trực tiếp `UNIQUE(student_id, course_id)` còn mở.
- **Railway runtime fact (user-provided):** deployment từng fail vì database thiếu `student.version`; V6/V7 phải migrate trước Hibernate `validate`. Repository không có production log, nên trạng thái migration production là **TBD**, không CONFIRMED.
- **CONFIRMED:** Course roster được đưa vào tại checkpoint lịch sử `52a8c71` và vẫn có trong HEAD `200d866`: lấy `TeamMember -> Team -> Course`, ADMIN mọi Course/LECTURER instructor, anonymous 401, STUDENT 403, Course thiếu 404; GET không cần CSRF. Filter/sort chạy trước pagination, metadata tính trên toàn tập sau filter và tie-break theo id. `hasTeam=all|with|without`; sortBy `studentCode|fullName|email|teamName|projectName`; direction `asc|desc`; query invalid 400.
- **PARTIAL / SOURCE DRIFT:** `studentsWithoutTeam`/`hasTeam=without` theo contract phải rỗng vì chưa có Student–Course enrollment độc lập; invitation outbox không phải enrollment source. Current baseline `CourseService#getCourseRoster` vẫn đọc invitation outbox và làm fail contract test DEC-023; behavior này không được coi là feature. Legacy invalid data nhiều Team cùng Course được đọc không crash nhưng không hợp lệ theo business rule.
- **CONFIRMED:** Lecturer options được đưa vào tại checkpoint lịch sử `52a8c71` và vẫn có trong HEAD `200d866`: ADMIN-only; anonymous 401, LECTURER/STUDENT 403; keyword chỉ `fullName`/`email`, không tìm/trả `cognitoSub`; sortBy `fullName|email`, direction `asc|desc`, invalid query 400, GET không cần CSRF.
- **ACCEPTED (Product Owner):** Student có thể ở nhiều Course nhưng tối đa một Team trong mỗi Course; role và Project độc lập theo Team/Course. Nhiều Team/Project cùng Course hợp lệ nếu mỗi Project thuộc Team khác; cùng Student ở hai Team của cùng Course là không hợp lệ.
- **CONFIRMED:** Rule TeamMember được đưa vào tại checkpoint lịch sử `52a8c71` và vẫn có trong HEAD `200d866`: `ExcelImportService` là production write path duy nhất tạo TeamMember. Lock `PESSIMISTIC_WRITE` trên Student rồi query Student+Course: chưa có thì tạo; cùng Team idempotent không đổi role; Team khác cùng Course conflict 409, không move/delete/update membership; khác Course hợp lệ. Local seed không tạo dữ liệu trái rule.
- **PARTIAL/TBD:** application concurrency guard được test bằng hai thread/hai transaction; database chưa có `UNIQUE(student_id, course_id)`. Roster trả email Student cho ADMIN/Lecturer owner và options trả email Lecturer cho ADMIN, nhưng business/UI justification vẫn TBD; response không chứa cognitoSub, version, token hay credential.
- **CONFIRMED:** `GET /api/me/courses/{courseId}/team/members` đã được commit tại `250f514` và vẫn có trong HEAD `200d866`: chỉ cho STUDENT; anonymous 401, ADMIN/LECTURER 403, không cần CSRF. Backend resolve Student từ `SagaPrincipal.localProfileId` và Team theo Student+Course; no membership/Course thiếu 404, legacy nhiều Team 409. Response trả resolved teamId cho FE dùng Project/integration flow, Project nullable và page members không email/cognitoSub/version/token.
- **CONFIRMED:** endpoint roster cũ vẫn giữ ADMIN/LECTURER/STUDENT exact-Team authorization; page member được tái sử dụng trong `TeamRosterService`. Project LEADER/MEMBER authorization không đổi.
- **Verification của snapshot lịch sử:** full Maven 70 suites/299 tests; update 2026-08-06 supersede bằng **90 suites/504 tests/0 failures/0 errors/0 skipped**. OAuth callback, role priority, session và import authorization không đổi.
## Cập nhật 2026-08-05 — Lecturer Analytics read APIs

- **CONFIRMED:** thêm tám GET route cho Team detail, Student progress/activities,
  Contribution read adapter, Course early warnings, interaction graph, heatmap và velocity.
- **CONFIRMED:** actor lấy từ `SagaPrincipal`; ADMIN mọi Course, LECTURER đúng instructor,
  STUDENT 403; GET dùng session và không cần CSRF.
- **PARTIAL:** warnings chỉ `OVERDUE_TASK`; heatmap chỉ Commit; interaction chỉ Peer Review;
  velocity là current planned points; activities không có Jira transition history.
- Không sửa nhóm Contribution/Peer Review/Slice Weights, migration hoặc provider sync.
- **Verification:** targeted analytics 21 tests và Team roster security 13 tests pass;
  regression GitHub/Jira/Contribution 20 tests pass; full Maven 77 suites / 339 tests /
  0 failures / 0 errors / 0 skipped.
# Update 2026-08-07 — P1 response/error semantics

- **CONFIRMED:** `GET /api/v1/teams/{teamId}/sprints` giữ nguyên authorization hiện có và kiểm quyền trước khi xét Team chưa có Project. Team tồn tại, actor được phép, `projectId = null` trả `200` với `projectId: null`, `teamId`, `state: PROJECT_NOT_CREATED`, `sprints: []`; Project có zero Sprint trả `state: EMPTY`; có Sprint trả `state: READY`.
- **CONFIRMED:** Team không tồn tại trả `404` với error code `TEAM_NOT_FOUND`. `SprintListResponse` chỉ bổ sung trường `state`; không bỏ/đổi tên field cũ.
- **CONFIRMED:** error JSON thống nhất `timestamp`, `status`, `error`, `message`, `path`. Generic validation/request/security/not-found/conflict/runtime có code ổn định; `IntegrationException` tiếp tục giữ nguyên các code `JIRA_*`, `GITHUB_*`, `INTEGRATION_*`.

## Cập nhật 2026-08-09 — Rubric schema repair M4-R2

- **Runtime fact trước V22 do người dùng cung cấp:** production/baselined MySQL ghi
  V10/V13 `SUCCESS`; `rubric_template.subject_id` là `CHAR(36) NOT NULL`, có 0 rubric
  và hai FK `subject_id -> subject(id)`.
- **Đã hoàn thành trong source:** V22 đổi duy nhất cột đó thành nullable cho
  **EXISTING_BASELINED_DB_UPGRADE**. Không có seed rubric, Admin CRUD hoặc thay đổi
  Peer Review/Contribution.
- **PARTIAL:** contract test xác nhận V22 chỉ có `ALTER`, không DML và V10/V13 không
  đổi source. Full Maven pass 105 suites / 646 tests / 0 failures / 0 errors /
  0 skipped. MySQL execution test chưa có vì repository không có MySQL/Testcontainers
  infrastructure.
- **Tách biệt:** **REPLAY_FROM_EXTERNAL_V1_BASELINE** vẫn cần external baseline và
  compatibility decision trước V13; **TRUE_EMPTY_DATABASE_BOOTSTRAP** là
  `BLOCKED_EXISTING_BASELINE_GAP`, không tạo V1 trong milestone này.

### Runtime verification production sau deploy — 2026-08-09

- **CONFIRMED:** V19/V20/V21/V22 đều `SUCCESS`. `semester.deleted_at` và
  `course.deleted_at` nullable `datetime(6)`; `lecturer.account_status` là
  `varchar(20) NOT NULL DEFAULT 'ACTIVE'`; `rubric_template.subject_id` nullable
  `char(36)`; rubric row count sau V22 là 0.
- **CONFIRMED:** duplicate FK rubric subject không chặn nullable repair, nhưng vẫn
  tồn tại. Không có cleanup FK, seed, Admin CRUD hoặc thay đổi Peer Review.

## Scope rollback M4B — 2026-08-10

- **CONFIRMED:** Admin rubric CRUD, `deletedAt` entity/repository lookup và resolver
  active-only của Peer Review đã được trả về behavior baseline trước M4B.
- **CONFIRMED:** V23 `rubric_template.deleted_at` đã chạy production nên còn nguyên,
  bất biến và không được application sử dụng. Không có reverse migration, seed,
  hard-delete hay sửa historical data.

## Cập nhật 2026-08-09 — Admin Course progress overview M5

- **CONFIRMED:** endpoint GET `/api/admin/course-progress-overview` trả `Page` Course
  active với filter `keyword`, `semesterId`, `lecturerId`; ADMIN session nhận 200,
  anonymous 401, Lecturer/Student 403, GET không cần CSRF.
- **CONFIRMED:** aggregate SQL một trang, local-only, đếm distinct Team/Student/Project,
  Sprint active/non-deleted theo state và PeerReview. Không gọi Jira/GitHub và không
  loop gọi `ContributionCalculationService` theo Team.
- **PARTIAL/TBD:** Assessment chỉ là entity/repository không có lifecycle/API; raw
  PeerReview count không được diễn giải thành completion percentage hay completion status.

## Cập nhật 2026-08-09 — Admin Course report export M6

- **CONFIRMED:** ADMIN tải XLSX từ `/api/admin/reports/courses/{courseId}/export`.
  Missing/tombstoned Course trả 404; anonymous 401; Lecturer/Student 403; GET không
  cần CSRF. Empty Course vẫn tạo workbook hợp lệ.
- **CONFIRMED:** Course metadata, Team Member, Sprint canonical local, Task canonical
  local và raw PeerReview không comment được bulk-load theo Course. Không có provider
  call, Assessment sheet hoặc Current Contribution sheet.
- **Verification:** targeted XLSX parse/security/privacy/provider-isolation/OpenAPI
  regression pass. Assessment và official grade vẫn không được implement.

## Admin global user import M7 — 2026-08-09

- **Đã hoàn thành / CONFIRMED:** ADMIN có `POST /api/admin/users/import` multipart,
  `role=STUDENT|LECTURER` do request enum kiểm soát; XLSX không có cột role. 200 chỉ trả
  summary an toàn (`role`, `createdCount`, `reusedCount`); 400 file/schema/role sai, 409
  identity conflict, 401 anonymous và 403 CSRF/role sai.
- **Đã hoàn thành / CONFIRMED:** Student email+studentCode exact mới reuse; record mới
  PENDING không subject/Course/Team/invitation. Lecturer email exact mới reuse; record mới
  ACTIVE. Import không overwrite field, status, subject hay profile role hiện hữu.
- **Đã hoàn thành / CONFIRMED:** validate toàn bộ XLSX trước write, reject formula,
  header/required/duplicate sai; transaction rollback toàn bộ. `ADMIN` import unsupported
  do thiếu governance. Targeted M7/OpenAPI/Course-import-idempotency: 20 tests pass.

## Admin active Semester setting M8A — 2026-08-09

- **Đã hoàn thành / CONFIRMED:** default Semester toàn hệ thống là lựa chọn explicit của ADMIN, không suy từ `startDate`/`endDate` và không thêm status/active field vào Semester. `GET`/`PUT /api/admin/settings/active-semester` là ADMIN session; PUT CSRF-protected, body `{ "semesterId": "uuid" }`.
- **Đã hoàn thành / CONFIRMED:** V24 thêm bảng typed singleton có FK Semester và seed singleton unset. Một setting tối đa; missing/tombstone 404, repeated PUT idempotent/deterministic. Response trả current setting, kể cả unset với field Semester null.
- **Đã hoàn thành / CONFIRMED:** selecting không đổi Course/Semester hay ép filter Course. DELETE Semester active trả 409; không clear/cascade. Targeted M8A + Semester/migration/OpenAPI/Admin M1–M7 pass.

## M9 Admin notification broadcast — BLOCKED sau audit 2026-08-09

- **CONFIRMED:** `Notification` là entity JPA không được sử dụng với `recipientId`,
  `recipientRole`, `title`, `message`, `isRead`; không có repository, enum type, service,
  controller, route HTTP, producer, consumer hay test. Không có GET user notification,
  polling, WebSocket/SSE hoặc email delivery; invitation outbox không phải notification transport.
- **BLOCKED:** baseline Flyway V1 legacy không có trong repository và V2–V24 không chứa
  migration notification, nên physical schema production (FK/nullable/index/unique/length)
  không được xác nhận. Ba bảng profile local độc lập cũng không cung cấp common recipient FK.
- **QUYẾT ĐỊNH:** chưa triển khai `POST /api/admin/notifications/broadcast`; insert DB không
  có consumer là không hữu ích. Không thêm migration/model/read API/fanout khi audience,
  policy status, retention và read lifecycle chưa có business decision.
- **RECOMMENDED:** nếu được phê duyệt, dùng contract plain-text bounded, audience typed và
  versioned broadcast master + receipt per recipient khi cần read/unread; không trả recipient list,
  không gọi Cognito/provider và không reuse invitation outbox.
- **M8B:** `GENERIC_SYSTEM_SETTINGS = TBD_NOT_IMPLEMENTED_NO_CONFIRMED_GLOBAL_SETTINGS`.

## M10 Support & Diagnostics — 2026-08-09

### A11A durable audit identity — 2026-08-09

- **CONFIRMED:** Event `SystemAuditLog` mới từ `SagaPrincipal`/login profile có thêm
  `actorLocalProfileId` (UUID canonical text) và `actorRole`. `actorId` vẫn là Cognito subject.
  Webhook, system và identity conflict thiếu profile chính xác ghi hai field mới là `null`, không
  invent UUID/role. Document cũ có thể thiếu field; không backfill hay rewrite Mongo.
- **PARTIAL/BLOCKED:** Coverage durable là forward-only và không bao phủ mọi event historical;
  chưa có `GET /api/admin/users/{id}/audit-logs` hoặc per-user history contract.

- **Đã hoàn thành / CONFIRMED:** `GET /api/admin/integrations/health` là ADMIN browser
  session GET local-only. Response chỉ có enabled flag, linked-project count, raw
  connection/installation/webhook-receipt status counts, Jira stored-webhook-id count và
  latest persisted sync timestamp. Không provider call, health score synthetic, token,
  secret, encrypted credential, webhook ID, payload, URL hoặc Cognito subject.
- **BLOCKED:** audit Mongo không có stable `localProfileId` trên mọi event; `actorId` là
  Cognito subject nên `GET /api/admin/users/{id}/audit-logs` không được thêm. Không có
  impersonation/delegated session/restore/audit model; không thêm token/JWT/Bearer.
- **TBD/BLOCKED:** role mutation và password reset cần Cognito/governance contract; Student
  thuộc Course qua TeamMember nên manual add/remove Course không rõ Team/Project/history.

## I1 Course import hardening — 2026-08-09

- **Đã hoàn thành / CONFIRMED:** existing Course import đã có schema XLSX exact, formula rejection, giới hạn 1 MiB/1.000 data rows, duplicate-in-file và safe error code; không có preview, validate hoặc template endpoint.
- **Đã hoàn thành / CONFIRMED:** parse + bulk preflight identity/Team/membership chạy trước write trong transaction. Lỗi `INVALID_HEADER`, `FORMULA_NOT_ALLOWED`, `MALFORMED_WORKBOOK`, `FILE_TOO_LARGE`, `ROW_LIMIT`, `INVALID_ROW`, `DUPLICATE_IN_FILE` là 400; `IDENTITY_CONFLICT` và `COURSE_TEAM_MEMBERSHIP_CONFLICT` là 409; không trả raw workbook/row values.
- **PARTIAL:** invariant một Student/một Team/mỗi Course vẫn là application guard có lock, chưa có DB unique invariant trực tiếp; không tự sửa dữ liệu legacy.

## D1 Session/CSRF/CORS production readiness — 2026-08-09

- **CONFIRMED_SOURCE/TEST:** browser-session security giữ token-free `SagaPrincipal`, session fixation migration, CSRF cookie/header (`XSRF-TOKEN`/`X-XSRF-TOKEN`), explicit credentialed CORS và logout POST có CSRF, invalidate session, xóa hai cookie rồi redirect Cognito. AccountStatus filter chạy sau CSRF và miễn `/api/auth/me`, `/csrf`, `/logout`.
- **CONFIRMED_SOURCE:** prod default cookie Secure=true, SameSite=none, session HttpOnly=true; CSRF path `/`, HttpOnly=false và mirror Secure/SameSite. Cookie Domain, session Path, max-age và session timeout không explicit.
- **PARTIAL:** Railway/source yêu cầu đúng một replica nhưng không có Spring Session/shared store. Restart/redeploy làm mất HttpSession; nhiều replica không an toàn nếu không sticky session hoặc shared store.
- **TBD_RUNTIME:** Cognito login thật, browser cross-site cookie, proxy forwarded header, logout redirect và Railway deploy chưa được quan sát runtime.

## Admin managed users và audit timestamp — 2026-08-09

- **CONFIRMED:** `GET /api/admin/users` đã supersede contract cũ: chỉ `STUDENT` + `LECTURER`; `ADMIN` không có `AccountStatus` lifecycle nên bị loại trong SQL union trước pagination/count. `role=ADMIN` giữ parser enum tương thích nhưng trả page rỗng.
- **CONFIRMED:** Audit mới dùng `Instant` → BSON Date → JSON UTC `Z`; tài liệu BSON Date cũ đọc đúng same instant, không migration/backfill. FE phải parse timestamp ISO bằng `Date`/`Intl` và format `Asia/Ho_Chi_Minh`, không cộng cứng +7.
- **CONFIRMED:** System stats không đổi, vẫn là count profile toàn cục Admin + Lecturer + Student.

## J1F TASK_SPRINT remote-success finalization — 2026-08-10

- **CONFIRMED:** production DEMO-24 cho thấy `TASK_CREATE=COMPLETED`; `TASK_SPRINT=REMOTE_SUCCEEDED`, `remote_resource_id=10026`, `remote_resource_key=DEMO-24`, `safe_error_code=NULL` và `completed_at=NULL`. Remote mutation đã xảy ra, nhưng normal request dùng operation object cũ chưa có remote id nên trả `JIRA_WRITE_OPERATION_IN_PROGRESS` trước canonical recovery.
- **Đã hoàn thành:** Sprint flow nay canonical GET/upsert, áp target local trong `REQUIRES_NEW`, fresh-read xác nhận Sprint/backlog rồi complete. Failure sau remote success giữ `REMOTE_SUCCEEDED`; replay cùng key không gọi provider move thêm.
- **Đã hoàn thành:** recovery nền không finalize `TASK_SPRINT` vì operation không lưu target intent ngoài fingerprint; same-key endpoint recovery mới có request target để xác nhận an toàn.

## J1D Jira Task canonical confirmation — 2026-08-10

- **CONFIRMED_RUNTIME/SOURCE:** incident `CANONICAL_ISSUE_FETCH` với `REMOTE_SUCCEEDED` phù hợp stale outer snapshot MySQL `REPEATABLE_READ`: outer create đã đọc Project/board/local state trước child canonical upsert `REQUIRES_NEW` commit.
- **CONFIRMED:** create và recovery Task canonical flow dùng `JiraCanonicalTaskReadService.findResponse/exists`, mỗi call là fresh `REQUIRES_NEW` read-only transaction. Chỉ complete sau confirmation; missing Task giữ recovery-required/`REMOTE_SUCCEEDED` và không POST Jira lần hai.
- **PARTIAL:** test infrastructure hiện chỉ H2 MySQL-mode, không có Testcontainers/Docker MySQL; không tuyên bố đã tái tạo MySQL MVCC trong test.
## Student Course Invitation Gmail REST API — 2026-08-11

- **IMPLEMENTED / CONFIRMED_SOURCE_TEST:** production delivery adapter dùng OAuth 2.0 refresh-token qua HTTPS và Gmail `users.messages.send`; MIME UTF-8 plain text + HTML được gửi Base64URL trong JSON `raw`. Spring Mail/JavaMail và SMTP properties đã được loại bỏ vì audit không thấy consumer khác.
- **FAIL-SAFE:** thiếu bất kỳ biến nào trong `GMAIL_API_CLIENT_ID`, `GMAIL_API_CLIENT_SECRET`, `GMAIL_API_REFRESH_TOKEN`, `GMAIL_API_SENDER_EMAIL`, `GMAIL_API_SENDER_NAME` thì backend vẫn start và dùng unavailable adapter. Không có default/fake credential, startup provider call hay Gmail live health probe; connect/read timeout dùng convention integration 3/10 giây mặc định.
- **OUTBOX UNCHANGED:** `StudentInvitationProcessor` chỉ mark `SENT` sau send success; config/provider exception mark `FAILED`, retry/max-attempt/stale recovery/SENT no-resend giữ nguyên và membership đã commit không rollback.
- **SECURITY:** access token chỉ cache thread-safe trong process và refresh trước expiry; log không chứa recipient/body/secret/token/Authorization/form/raw MIME/raw response. Header injection bị từ chối trước network call.
- **FAILURE POLICY:** network/429/5xx và 403 rate/quota được nhận diện retryable; invalid/revoked OAuth, malformed response, 400/401 và 403 permission/sender là non-retryable. Processor/outbox schema chưa dùng cờ này để dừng retry: mọi failure vẫn `FAILED` và retry tới max attempts theo policy cũ.
- **VERIFICATION:** targeted context/Gmail/invitation/import regression **10 suites / 70 tests / 0 failures / 0 errors**. Full suite chạy **131 suites / 822 tests**; Gmail scope pass. Baseline DEC-023 vẫn fail như trước. Full-order còn làm lộ một `JiraBoardResolutionServiceTest` error ngoài scope; method này pass khi rerun riêng và không có source diff liên quan, nên không sửa trong milestone.
- **TBD_DEPLOYMENT_SMOKE:** Gmail API production delivery, inbox/spam và outbox transition trên deployment thật chưa được xác nhận.
## Notification Bell / Firebase FID — 2026-08-11

- **CONFIRMED_SOURCE_TEST:** user-owned notification persistence/read state, unread count, FID registration/revocation, FCM adapter, and durable per-installation delivery/retry are implemented. The backend starts with an unavailable delivery adapter when Firebase credentials are missing/invalid, so DB/API notification behavior remains available.
- **API:** `GET /api/me/notifications`, `GET /api/me/notifications/unread-count`, `PATCH /api/me/notifications/{id}/read`, `POST /api/me/firebase-installations`, `DELETE /api/me/firebase-installations/{id}`. Existing OIDC session + CSRF contract applies; there is no Bearer auth.
- **PRODUCER:** only a newly-created grouped-import `TeamMember` produces `COURSE_MEMBERSHIP_ADDED`. No notification is emitted for invitation-only, ungrouped, or idempotent membership rows.
- **UNCHANGED:** Course roster/enrollment, grouping, DEC-023, Cognito provisioning, session/CSRF, and Admin broadcast. DEC-056 is superseded only for user-owned infrastructure; broadcast remains BLOCKED.
- **VERIFICATION:** targeted Notification/Firebase **16/16 PASS**. Full suite: **122 suites / 769 tests / 1 failure / 0 errors / 0 skipped**; sole failure is **PREEXISTING_BASELINE_SOURCE_CONFLICT_WITH_DEC_023** in `CourseRosterAndLecturerOptionsIntegrationTest`, unchanged.
- **TBD_DEPLOYMENT_SMOKE:** Railway migration, real Firebase credential initialization, FID send/receive, and retry transitions have not yet been observed in production.
## Notification producers and manual broadcast — 2026-08-11

- **CONFIRMED:** `POST /api/admin/notifications/broadcast` (ADMIN) and `POST /api/v1/courses/notifications/broadcast` (LECTURER) use existing browser session, CSRF and required `Idempotency-Key`. They return only safe broadcast counters/status.
- **CONFIRMED:** Admin audience is Student/Lecturer; Lecturer course recipient source is distinct confirmed TeamMember membership. One Bell notification per recipient is persisted even with zero FIDs; each active FID gets its own delivery row.
- **CONFIRMED:** personal Jira/GitHub verified links and project Jira-board/GitHub-installation success notify their initiating actor only. V27 event keys prevent duplicate notification rows for replay of the same recipient/event. Course membership notification remains unchanged.
- **HISTORICAL/SUPERSEDED_BY_DEC_071:** Admin inclusion in ALL_USERS and AccountStatus filtering remain TBD, but Task/Sprint recipients and the date-only deadline model are now decided and implemented below.
- **TBD_DEPLOYMENT_SMOKE:** real Railway migrations V26/V27 and FCM broadcast delivery/retry.

## Jira Task/Sprint notification producer — 2026-08-11

- **IMPLEMENTED:** completed SAGA Jira Task and Sprint writes reuse Bell DB/FID delivery; Task recipient is canonical assignee else owning Team, Sprint is owning Team, actor excluded.
- **IMPLEMENTED:** date-only reminders use `JIRA_TIME_ZONE` calendar Tomorrow/Today/Overdue semantics. No due-time/3-hour/24-hour inference.
- **IMPLEMENTED:** `TASK_CREATE`, `TASK_UPDATE`, `TASK_ASSIGN`, `TASK_SPRINT`, `TASK_ESTIMATION`, `TASK_TRANSITION`, `TASK_DELETE`, `SPRINT_CREATE`, `SPRINT_UPDATE`, `SPRINT_START`, `SPRINT_CLOSE`, and `SPRINT_DELETE` map to one logical per-recipient event only after durable `COMPLETED`. Failed, unknown, and unresolved `REMOTE_SUCCEEDED` writes do not notify; reconciliation/webhook sync never produces these mutation events.
- **RECIPIENT:** Task uses the canonical assignee and falls back to the unique owning Team only when assignee is null. Sprint always uses the owning Team. Actor exclusion applies when the actor is a Student recipient; Admin/Lecturer are not guessed into Team membership. AccountStatus is not newly filtered.
- **DEADLINE:** public requests use `LocalDate`, Jira parsing yields a date-only start-of-day `LocalDateTime`, and DB retains that legacy storage type. Hourly bounded scans use `NOTIFICATION_DEADLINE_PROCESSING_ENABLED` and `NOTIFICATION_DEADLINE_SCAN_DELAY_MS`; DONE, CANCELLED, deleted, and null-due tasks are excluded. Event identity includes task, due-date revision, reminder type, and recipient ownership columns.
- **DELIVERY/RECOVERY:** V27 recipient/event uniqueness prevents duplicate Bell rows; each active FID receives its own durable delivery row. Producer or FCM failure cannot roll back the already-completed Jira write. `actionUrl` remains null because no canonical internal FE Task/Sprint route was confirmed.
- **VERIFICATION:** targeted Notification/Firebase/Broadcast/Jira recovery/Team authorization/Migration/CSRF regression ran **26 suites / 289 tests / 1 failure / 0 errors / 0 skipped**. Full `mvnw clean test` ran **129 suites / 795 tests / 1 failure / 0 errors / 0 skipped**. The sole failure is the unchanged `CourseRosterAndLecturerOptionsIntegrationTest#courseRosterHasTeamContractIsExplicitAndDoesNotTreatOutboxAsEnrollment`, classified **PREEXISTING_BASELINE_SOURCE_CONFLICT_WITH_DEC_023**.
- **TBD_DEPLOYMENT_SMOKE:** Flyway V25-V27, scheduled deadline execution, Firebase FID delivery/retry, and Bell display require Railway/browser runtime verification.
## GitHub Issue traceability milestone — 2026-08-11

- **CONFIRMED:** FE có local GitHub Issue list/detail, Task-centered traceability và bounded Project
  timeline. Task–Issue là explicit many-to-many local link, same-project only; duplicate link
  idempotent, missing unlink idempotent, manager authorization/session/CSRF reuse exact source.
- **CONFIRMED:** V28 là migration mới nhất và tạo ba normalized link tables; legacy single Issue FK
  trên PullRequest/CommitData không bị xóa hoặc dùng làm nguồn truth song song.
- **REQUIRED_RUNTIME_MYSQL_PREFLIGHT:** V28 source/test đã xác nhận shape nhưng production legacy V1
  không nằm đầy đủ trong repository. Trước first deploy phải chạy read-only
  `docs/integrations/mysql-traceability-v28-preflight.sql`; chỉ tiếp tục khi UUID type/charset/collation,
  FK engine, database defaults và collision summary đều cho `V28_PREFLIGHT_READY=PASS`. Trạng thái V28 là
  **CONFIRMED_SOURCE_TEST + REQUIRED_RUNTIME_MYSQL_PREFLIGHT**, không phải production proof.
- **PARTIAL:** Issue–PR và Issue–Commit normalized read model đã có, nhưng current GitHub provider
  snapshot chưa chứng minh linked/closing relation nên reconciliation chưa populate chúng.
- **NOT_IMPLEMENTED:** GitHub remote Issue CRUD, provider permission change, lifecycle notification,
  hoặc GitIssue contribution/analytics. GET traceability không gọi Jira/GitHub realtime.

## Team detail GitHub repository references — 2026-08-11

- **CONFIRMED:** `GET /api/v1/courses/{courseId}/teams/{teamId}/detail` giữ response hiện hữu và thêm
  `project.repositories[]` gồm GitHub provider `repositoryId` kiểu `Long` cùng `repositoryName` an toàn.
  Project–GitRepo là one-to-many; endpoint trả mọi repository có provider ID theo `fullName`, rồi
  `repositoryId`, không pick-first. Team chưa có Project trả `project: null`; Project chưa có repository
  trả `repositories: []`.
- **CONFIRMED:** repository ID này dùng trực tiếp cho GitHub branches/commits path và Issue repository
  filter hiện hữu. Team detail chỉ query local DB, không gọi provider và không expose URL, installation,
  token hoặc credential. Authorization ADMIN/LECTURER đúng Course, STUDENT 403, session và GET/CSRF
  semantics giữ nguyên. Đây là additive response field nên không tạo DEC mới.
## Student Team Leader Contribution read — 2026-08-12

- **IMPLEMENTED / CONFIRMED_SOURCE_TEST:** existing
  `GET /api/v1/teams/{teamId}/contribution-evaluation` nhận principal từ browser session.
  ADMIN đọc mọi Team; LECTURER phải là `team.course.instructor`; STUDENT phải có exact
  membership `teamId + localProfileId + RoleInTeam.LEADER`.
- MEMBER, MENTOR, Student không membership, cross-Team Leader và Lecturer Course khác đều 403;
  anonymous 401; Team thiếu 404. Không tạo `ROLE_LEADER` và không nhận actor ID từ request.
- Response privacy audit PASS và DTO giữ nguyên. Calculation/current aggregate, override,
  slice-weight và Peer Review behavior không đổi. Targeted regression 53/53 PASS. Full clean chạy
  132 suites / 831 tests / 1 failure / 0 errors / 0 skipped; failure duy nhất là baseline DEC-023
  trong `CourseRosterAndLecturerOptionsIntegrationTest`, không có diff `CourseService`.
## J1K Jira Task Issue Type Update — 2026-08-13

- **IMPLEMENTED / CONFIRMED_SOURCE_TEST:** main sparse Task Update accepts `type` as SAGA `TaskType`; `REQUEST` was added to the same enum and canonical Jira `Request` now maps back to `REQUEST`. Normal FE sends business values only and never owns Jira issue-type IDs.
- Backend reads the exact issue `editmeta`, requires editable `issuetype`, and resolves exclusively from its `allowedValues` with provider-ID deduplication, unique exact-name priority, unique semantic fallback, and fail-closed zero/ambiguity behavior. Provider mutation is one sparse `fields` object and local resolution failure causes zero Jira PUT.
- Same-type diff suppression preserves the existing all-no-op `JIRA_TASK_UPDATE_EMPTY` response. Mixed title/type and other existing fields remain one provider mutation. EPIC/SUBTASK hierarchy crossing is rejected; no move/parent/hierarchy workaround is attempted.
- Update fingerprint includes raw `type`. Remote success is completed only after canonical GET/upsert/fresh read confirms the requested business type. Mismatch/failure remains `REMOTE_SUCCEEDED`; same-key replay performs canonical recovery without another PUT. Background `TASK_UPDATE` recovery remains pending for that same target-aware retry because the stored fingerprint is not reversible.
- Assignee/Sprint/Estimation/Transition/Delete, provider scopes, session/CSRF/Idempotency-Key, authorization and schema are unchanged. No hardcoded issue-type ID, provider metadata persistence, Bearer flow, migration, or `CourseService` change. Runtime deploy smoke is **TBD_DEPLOYMENT_SMOKE**.
- **TEST STATUS:** targeted J1K regression is green at **6 suites / 240 tests**. Full clean ran **134 suites / 868 tests / 4 failures / 0 errors**: one known DEC-023 roster baseline and three stable unrelated failures (OpenAPI operation count and two Lecturer Analytics expectations). No J1K targeted failure; `CourseService` diff is empty.

## Student account lifecycle V2 — 2026-08-14

- **IMPLEMENTED / CONFIRMED_SOURCE_TEST:** `AccountStatus` is independent from Course membership. Only an imported/pre-provisioned Student awaiting its first accepted identity bind is `PENDING`; successful accepted STUDENT authentication creates or recovers an `ACTIVE` account regardless of TeamMember.
- Import-first creates an unlinked `PENDING` Student plus the existing TeamMember/invitation. Exact first login binds the same Student and changes only `PENDING -> ACTIVE`, retaining TeamMember, role and multi-Course memberships.
- Login-first with no local match creates an `ACTIVE` Student with `cognitoSub` and no TeamMember. Later exact import reuses the same Student ID, keeps `ACTIVE`, creates/reuses TeamMember, preserves the requested role, enqueues the invitation and makes the Course visible immediately.
- Legacy `PENDING + cognitoSub` recovers on successful same-subject login without membership. ACTIVE stays ACTIVE; INACTIVE/SUSPENDED remain blocked and are never auto-reactivated. Identity validation, ambiguity/cross-profile conflicts and subject uniqueness are unchanged.
- Course membership remains `Student -> TeamMember -> Team -> Course`; no enrollment-status entity/field exists. Invitation is informational and is neither activation nor enrollment truth. No Lambda/Cognito, session/CSRF, account-status filter or `CourseService` behavior changed.
- **VERIFICATION:** targeted auth/OIDC/import/TeamMember/invitation/account-status regression passes **11 suites / 86 tests / 0 failures / 0 errors / 0 skipped**. Full clean ran **138 suites / 888 tests / 5 failures / 0 errors / 0 skipped**: four stable known baselines (DEC-023, OpenAPI count, two Lecturer Analytics) plus one unrelated notification-order assertion that passed immediate isolated rerun **1/1** and is classified non-deterministic. No lifecycle test failed; `CourseService` diff is empty.
