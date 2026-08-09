# SAGA — Trạng thái hiện tại

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
- **PARTIAL:** import và Contribution mutation đã resolve Course active-only; các read resolver analytics/roster/Contribution còn raw theo phạm vi cũ, chưa refactor trong M2B.

## Cập nhật 2026-08-09 — Semester Update và Soft Delete

- **CONFIRMED:** `SemesterRequest` được tái sử dụng cho PUT nguyên khối; validation code/name/date và `endDate >= startDate` giữ nguyên. Missing/deleted Semester là 404, duplicate code là 409.
- **CONFIRMED:** DELETE là soft-delete V19, active read loại tombstone và repeated delete 404. Course đang tham chiếu gây 409, không detach/cascade/xóa Course.
- **CONFIRMED:** code của tombstone không được tái sử dụng. Course create/read business logic không thay đổi.

## Cập nhật 2026-08-09 — Admin Read Foundation

- **CONFIRMED:** năm Admin-only GET dưới `/api/admin` đã có controller/service; URL rule `/api/admin/**` và method rule đều yêu cầu ADMIN.
- **CONFIRMED:** Users trả localProfileId/role/fullName/email/status/studentCode an toàn, phân trang DB; Audit Mongo chỉ trả id/action/targetEntity/timestamp.
- **CONFIRMED:** Stats/Teams/Projects chỉ dùng repository local. Project chỉ trả Course summary, Jira connectionStatus và GitHub aggregate; không provider call, secret, repository URL hay Project DELETE.

## Update 2026-08-09 — Create Task không cần Jira numeric IDs

- **CONFIRMED:** normal `POST /api/v1/projects/{projectId}/tasks` dùng `type` và `priority` business optional để resolve exact Jira IDs từ metadata của Jira Project hiện tại.
- **CONFIRMED:** `issueTypeId`/`priorityId` là advanced explicit override optional và chỉ được dùng sau validation local metadata. Issue type invalid trả `400 JIRA_ISSUE_TYPE_INVALID`; priority override invalid trả `400 JIRA_PRIORITY_INVALID`; không forward ID invalid tới Jira.
- **CONFIRMED:** normal auto-resolution zero/multiple candidate fail closed bằng code resolution cụ thể. Assignee vẫn chỉ resolve `IdentityMap ACTIVE -> externalAccountId`; không thêm Jira-side validation hay scope.

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
- Import Excel ở trạng thái **PARTIAL**: scope authorization, identity binding và outbox đã hoàn thành; validation/parser và production email delivery chưa production-ready.

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
| P0 | Chọn và cấu hình mail provider production | Outbox/adapter đã có nhưng delivery thật vẫn TBD | adapter/config/deployment | provider sandbox and retry verification |
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
- **PARTIAL:** `studentsWithoutTeam`/`hasTeam=without` hiện rỗng vì chưa có Student–Course enrollment độc lập; invitation outbox không phải enrollment source. Legacy invalid data nhiều Team cùng Course được đọc không crash nhưng không hợp lệ theo business rule.
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

## Cập nhật 2026-08-09 — Admin global rubric M4B

- **CONFIRMED:** V23 additive thêm `rubric_template.deleted_at DATETIME(6) NULL`; runtime
  production V23 đã thành công. V10/V13/V22 và duplicate FK không bị sửa.
- **CONFIRMED:** ba mutation admin `/api/admin/peer-review-rubrics` chỉ thao tác
  global active rubric. POST luôn tạo `subject=null`, `deletedAt=null`; PUT giữ
  id/subject/deletedAt; DELETE soft-delete, delete lần hai/missing/tombstone là 404.
- **CONFIRMED:** active global tối đa 4 do compatibility `criteriaRatings max=4`;
  active count 0 được phép để default trả `[]` và Team fallback Subject.
  Không có invariant 100%, uniqueness, rebalance, hard-delete hay rewrite history.
- **Verification:** targeted 43 tests pass, bao gồm V23, CRUD/security/CSRF,
  resolver active-only, retention PeerReviewDetail/Assessment, OpenAPI và regression
  AccountStatus/Admin read/Contribution.

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
