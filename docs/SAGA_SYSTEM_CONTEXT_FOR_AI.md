# SAGA — Context kỹ thuật hệ thống hiện tại

## Cập nhật 2026-08-09 — Account lifecycle M3B

**CONFIRMED:** `AccountStatus` áp dụng cho Student và Lecturer; Admin không có status. V21 thêm `lecturer.account_status NOT NULL DEFAULT 'ACTIVE'`, backfill row cũ và Lecturer mới/cũ null đều ACTIVE. `PATCH /api/admin/users/{id}/status` là ADMIN + CSRF, resolve localProfileId cùng Admin user union; chỉ Student/Lecturer, chỉ nhận ACTIVE/INACTIVE/SUSPENDED, PENDING bị provisioning Student sở hữu.

**CONFIRMED:** Business request trong browser session dùng current local DB status mỗi request: ACTIVE cho phép; Student PENDING/INACTIVE/SUSPENDED và Lecturer INACTIVE/SUSPENDED bị 403. `/api/auth/me`, `/api/auth/csrf`, `/api/auth/logout` được miễn; `/me` trả current DB status. Status mutation không cascade Course, membership, Project hay provider/history.

## Cập nhật 2026-08-09 — Audit AccountStatus M3A

**CONFIRMED:** Chỉ `Student` sở hữu `AccountStatus` (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `PENDING`); Admin/Lecturer không có field này. Principal trong JSESSIONID mang snapshot status từ login; không có DB status check theo request, SessionRegistry hay thu hồi session khi status DB thay đổi. First-login imported Student chỉ PENDING -> ACTIVE; ACTIVE giữ nguyên; INACTIVE/SUSPENDED bị từ chối bind/activate.

**TBD / BLOCKED BY POLICY:** Source không chứng minh Admin được set status nào, self-target, hay policy enforce API. `PATCH /api/admin/users/{id}/status` chưa được tạo; không thêm schema Admin/Lecturer, Cognito call hay arbitrary transition.

## Cập nhật 2026-08-09 — Course Update và Soft Delete

**CONFIRMED:** Course có `deletedAt` qua V20. `PUT`/`DELETE /api/v1/courses/{id}` là ADMIN-only và CSRF-protected. Create/update chỉ resolve Subject, Class, Semester active; Course tombstone không xuất hiện ở detail/list/filter và không tái dùng courseCode. DELETE chỉ soft-delete Course chưa dùng; Team, Project, StudentCourseInvitation hoặc TaskWeightConfig còn tham chiếu thì 409. Không hard-delete, cascade, detach hay thay đổi membership/import delivery.

## Cập nhật 2026-08-09 — Semester Update và Soft Delete

**CONFIRMED:** Semester có `deletedAt` theo migration V19. `PUT`/`DELETE /api/v1/semesters/{id}` là ADMIN-only, cần browser session và CSRF. DELETE đặt tombstone, active detail/list/search không trả row đó; hard delete/cascade không tồn tại. `CourseRepository.existsBySemesterId` chặn `409` khi Course còn tham chiếu. Code tombstone vẫn chiếm uniqueness. Course service không đổi trong milestone này.

## Cập nhật 2026-08-09 — Admin Read Foundation

**CONFIRMED:** `GET /api/admin/users`, `/audit-logs`, `/system-stats`, `/teams` và `/projects` yêu cầu session `ROLE_ADMIN`. Chúng chỉ đọc snapshot MySQL/Mongo local, không mutation hay provider call. DTO không trả Cognito subject, token, provider/raw audit payload, IP, repository URL hoặc secret integration. Users union ba local profile table với phân trang/count tại DB; audit logs sort timestamp giảm dần.

## Update 2026-08-09 — Jira Task Create metadata ownership

- **CONFIRMED:** Backend sở hữu Jira create metadata theo từng Jira Project; FE normal gửi business `type` (`TaskType`) và `priority` (`Priority`), không nhập Jira numeric ID.
- **CONFIRMED:** `issueTypeId` và `priorityId` vẫn là optional advanced override tương thích ngược. Backend lấy issue-type metadata trước, validate explicit issue type thuộc Project rồi mới gọi create-fields; explicit priority phải thuộc `priority.allowedValues`.
- **CONFIRMED:** auto-resolution dùng đúng normalization đã có ở canonical Jira upsert. Zero hoặc nhiều candidate fail closed; không hardcode Jira ID, không cache metadata cross-project, không đổi write-operation/session/CSRF/authorization.
- **CONFIRMED:** task-create diagnostics chỉ ghi projectId, operation/stage/resource type, resolution mode/result, upstream status, error category và write-operation status; không ghi credential, raw response hay Idempotency-Key.

## Update 2026-08-08 — Jira Sprint state trong list response

- **CONFIRMED:** `Sprint.state` đã tồn tại là `String` nullable trong canonical local read model và `JiraSprintResponse` đã trả nguyên representation này. `SprintSummaryResponse` nay trả thêm `state` từ trực tiếp `Sprint#getState()` cho cả `GET /api/v1/projects/{projectId}/sprints` và `GET /api/v1/teams/{teamId}/sprints`.
- **CONFIRMED:** list service chỉ đọc repository local, không inject/gọi Jira provider, không suy diễn state theo thời gian. Top-level `SprintListResponse.state` giữ nguyên `PROJECT_NOT_CREATED` / `EMPTY` / `READY`; item `sprints[i].state` là state Jira Sprint như `future`, `active`, `closed`.
- **CONFIRMED:** Start/Close hiện hữu canonical fetch/upsert local trước khi hoàn tất, nên list read model phản ánh state mới mà không đổi business write flow, authorization, idempotency, entity hay migration.

## Update 2026-08-07 — Jira simple-board Sprint capability probe

- **CONFIRMED runtime evidence:** SDP có đúng một board nhìn thấy được: external ID `35`, `type=simple`, association `projectId=10034` / `projectKey=SDP`. Board Features trả machine-identifier rỗng; Project Features không có Sprint identifier hữu dụng. Hai metadata source này không đủ để quyết định capability.
- **CONFIRMED source behavior:** `JiraProviderClientImpl` probe read-only qua 3LO `GET /rest/agile/1.0/board/{boardId}/sprint?maxResults=1`, có scope `read:sprint:jira-software`. Probe chỉ xác thực page object có `values` array; page rỗng cũng là bằng chứng endpoint được hỗ trợ. Không parse/persist Sprint và không tạo public SAGA endpoint.
- **Policy:** `scrum` là Sprint-capable trực tiếp và không probe. `simple` chỉ thành candidate khi probe trả 200 với page hợp lệ; association `10034/SDP` vẫn bắt buộc, zero/multiple candidate vẫn fail closed. Khi chỉ có board `35` supported, external ID `35` được persist và Create Sprint tiếp tục dùng `originBoardId=35` qua flow hiện hữu.
- **HTTP mapping:** 400 -> `JIRA_SPRINT_CAPABILITY_UNCONFIRMED`; 401/403/404/429 -> `JIRA_ACCESS_REVOKED` / `JIRA_ACCESS_FORBIDDEN` / `JIRA_BOARD_NOT_FOUND` / `JIRA_RATE_LIMITED`; 5xx/network -> `JIRA_PROVIDER_UNAVAILABLE`; malformed 2xx -> `JIRA_RESPONSE_INVALID`.
- **Safe diagnostics:** capability log có `projectKey`, `boardId`, `boardType`, `sprintCapabilityProbeHttpStatus`, `sprintCapabilityProbeResult`, `candidateReason`, `selectionResult`; feature diagnostics cũ vẫn chỉ là machine metadata. Không log raw body, Sprint name, token, Authorization, cookie hay credential.
- **Verification:** targeted Jira provider/resolver/link/Sprint tests pass; full `./mvnw.cmd clean test`: 99 suites / 586 tests / 0 failures / 0 errors / 0 skipped. Production probe SDP vẫn **TBD**; session + CSRF, retained-row relink và mutation authorization không đổi.

## Update 2026-08-07 — Jira relink retained-row upsert và provider identity

- **CONFIRMED:** `ProjectIntegrationService#linkJira` hoàn tất fresh grant/resource/scope/canonical project/Scrum board discovery trước local persistence. `JiraBoardLinkPersistenceService` sau đó khóa cả `project_id` và `(cloud_id, jira_project_id)`; provider HTTP (discovery/webhook) không chạy khi giữ DB lock.
- **CONFIRMED:** nếu retained row theo Project và provider identity khớp, hoặc provider-identity row đã thuộc đúng Project, service update chính managed row đó với credential mới, metadata site/project/board/status/webhook; `JiraBoard.id` và historical references được giữ. Không có nhánh tạo `JiraBoard` mới khi canonical provider row đã tồn tại.
- **CONFIRMED:** provider identity thuộc Project khác fail closed bằng `409 JIRA_PROJECT_ALREADY_LINKED` và message an toàn. Một retained Project có Jira identity khác cũng không bị overwrite mù; trả `409 JIRA_PROJECT_IDENTITY_CHANGE_NOT_ALLOWED`. Unique `uk_jira_cloud_project` giữ nguyên.
- **CONFIRMED:** race insert được coi là fallback hiếm: retry transaction mới để reload canonical row; same Project/provider coalesce, khác Project thành 409. Nếu vẫn không reconcile, API trả `JIRA_BOARD_UPSERT_CONFLICT`, không expose `DataIntegrityViolationException`, SQL, tên constraint, cloudId hay local row ID. Log chỉ chứa projectId/stage/conflict type an toàn, không token/raw provider body.
- **CONFIRMED:** concurrent DB integration tests bao phủ same-Project relink và two-Project same-provider race; disconnect/relink, OAuth fresh grant, scope preflight, board discovery, sync và Task/Sprint write regressions vẫn pass. Full Maven: 99 suites / 560 tests / 0 failures / 0 errors / 0 skipped.
- **TBD runtime:** deploy/smoke production vẫn cần thiết cho OAuth consent, dynamic webhook và concurrent requests thật; không thêm Bearer, không đổi session/CSRF và không thêm migration.

## Update 2026-08-07 — Jira 3LO resource, scope preflight và link diagnostics

- **CONFIRMED:** mọi Jira site-specific request của `JiraProviderClientImpl` đều đi qua 3LO gateway `https://api.atlassian.com/ex/jira/{verifiedCloudId}{apiPath}` cho Jira Platform (`/rest/api/3/...`) và Jira Software Agile (`/rest/agile/1.0/...`). URI builder chỉ nhận cloudId/path segment hợp lệ; không ghép site URL do FE gửi vào provider request.
- **CONFIRMED:** callback đổi authorization code xong gọi `GET /oauth/token/accessible-resources`; SAGA lưu resource đã trả về trong session-bound fresh grant, bao gồm `cloudId`, site URL và `scopes`. `POST /jira/link` chỉ chấp nhận cloudId khớp đúng resource này; site không khớp trả `JIRA_SITE_NOT_AUTHORIZED` trước khi gọi provider.
- **CONFIRMED:** preflight của `/jira/link` chỉ kiểm tra site product scopes cho đúng provider call của link: `read:jira-work`, `manage:jira-webhook`, `read:board-scope:jira-software`, `read:project:jira`. Thiếu scope trả `409 JIRA_SCOPE_INSUFFICIENT` với thông điệp an toàn, không map thành `JIRA_ACCESS_REVOKED` và không gọi provider. Sprint/Task write-delete kiểm scope tương ứng ngay tại runtime operation.
- **CONFIRMED:** authorization request mặc định yêu cầu toàn bộ capability SAGA thực sự dùng: `read:jira-work`, `write:jira-work`, `manage:jira-webhook`, `offline_access`, `read:board-scope:jira-software`, `read:board-scope.admin:jira-software`, `read:project:jira`, `read:sprint:jira-software`, `write:sprint:jira-software`, `delete:sprint:jira-software`, `write:board-scope:jira-software`, `write:issue:jira-software`. `offline_access` chỉ là authorization-request scope để refresh token, không được đối chiếu với `accessible-resources.scopes`. Các scope Agile không được suy diễn từ `read:jira-work`.
- **CONFIRMED:** link stages có safe structured diagnostic gồm `projectId`, stage/provider operation, verified cloudId, upstream HTTP status, error category, số scope cần và tên scope còn thiếu; không log access/refresh token, authorization code/state, cookie/header hay provider raw body.
- **CONFIRMED:** thứ tự link là manager authorization → fresh session grant → verified accessible resource → link-scope preflight → resolve project → discover Scrum board → persist retained/new row và credential → dynamic webhook → dispatch backfill sau commit. Lỗi trước webhook không đưa integration thành active/backfill và không dispatch sync.
- **TBD runtime:** trước production phải bật đúng scope trong Atlassian Developer Console, deploy, disconnect/re-consent OAuth, rồi smoke test link, board discovery, Get Sprint và Sprint CRUD. Thay đổi scope trên app hoặc consent không tự ban quyền mới cho token/grant cũ.

## Update 2026-08-06 — Jira hydration, disconnect và relink

- **CONFIRMED:** Jira search HTTP 200 với 0 issues vẫn hydrate mọi Sprint local active (`deletedAt is null`) của board. Candidate hydration được gắn nguồn `ISSUE_BATCH` hoặc `LOCAL_SPRINT`; Sprint local có thể còn thiếu canonical dates.
- **CONFIRMED:** `getSprint` phân loại 401 `JIRA_ACCESS_REVOKED`, 403 `JIRA_ACCESS_FORBIDDEN`, 404 `JIRA_SPRINT_NOT_FOUND`, 429 `JIRA_RATE_LIMITED`; 5xx sau retry GET và network/timeout là `JIRA_PROVIDER_UNAVAILABLE`. Không đổi OAuth scope, session, CSRF, timezone hay Jira mutation/recovery.
- **CONFIRMED:** lỗi hydrate được cô lập theo Sprint: Sprint còn lại vẫn upsert; job là `PARTIAL_FAILURE` và không advance cursor. Diagnostics chỉ ghi board local/external ID an toàn, projectKey, externalSprintId, upstream status/category, job/stage/source; không ghi token, Authorization hay raw provider body.
- **CONFIRMED:** disconnect giữ row `jira_board` và lịch sử tham chiếu, nhưng retire credential, expiry, scopes và webhook state. Relink khóa retained row, dùng fresh OAuth grant từ session và không reuse credential cũ. Scheduler/claim/state-write loại `DISCONNECTED`; worker recheck trước `getSprint` để không tiếp tục hydration sau disconnect.
- **PARTIAL:** chưa có integration test đa luồng thực sự cho concurrent Jira relink.
- **TBD:** externalSprintId và upstream HTTP status của incident lịch sử chưa có vì không có diagnostics production an toàn từ lần lỗi cũ. Policy cho Sprint bị xóa ngoài Jira vẫn chưa được phê duyệt.
- **Known limitation:** Sprint local nhận 404 không tự tombstone/hard-delete. Nếu vẫn active local, reconciliation có thể retry và ghi `PARTIAL_FAILURE` cho đến khi có cleanup policy an toàn.
- **Verification:** `./mvnw.cmd clean test` hoàn tất `BUILD SUCCESS`: 97 suites / 546 tests / 0 failures / 0 errors / 0 skipped.

## Update 2026-08-06 - Swagger/OpenAPI tiếng Việt

- **CONFIRMED:** springdoc `3.0.3` sinh 96 operation từ `/v3/api-docs`. Mỗi operation có summary, description và tag tiếng Việt; tag có description để Swagger UI hiển thị nhóm chức năng rõ ràng.
- **CONFIRMED:** metadata được chuẩn hóa tại OpenAPI generation, không thay route, HTTP method, DTO, status, authorization, session, CSRF hay business rule. Schema và parameter chưa có mô tả được bổ sung metadata tiếng Việt tại thời điểm sinh tài liệu.
- **CONFIRMED:** Swagger tiếp tục dùng browser session `JSESSIONID` và interceptor CSRF same-origin toàn cục. Không có Bearer scheme và không khai báo lặp `X-XSRF-TOKEN` theo từng operation; header `Authorization` chỉ còn ở webhook provider có evidence chữ ký riêng.
- **Verification:** generated OpenAPI test, OpenApiConfig/Swagger UI CSRF/security regression và full Maven pass 97 suites / 538 tests / 0 failures / 0 errors / 0 skipped. Runtime Swagger UI sau deploy vẫn TBD.

## Update 2026-08-06 - Project dashboard and GitHub repository reads

- **CONFIRMED:** Project has nullable `description` (V18); update normalizes blank text to null.
- **CONFIRMED:** `GET /api/projects/{projectId}/dashboard-stats` applies existing project read authorization and returns only local task/repository/commit/PR aggregates; deleted tasks are excluded.
- **CONFIRMED:** GitHub repository reads are backend-mediated: `GET .../branches` and `GET .../commits?branch=...` page provider data without exposing installation credentials. Branch names containing `/` stay in the query value.
- **CONFIRMED:** Manager-only reconnect is `POST .../github/repositories/{repositoryId}/connect` (session + CSRF). It requires a disconnected row and a still-authorized installation, locks the local repository state, then schedules the same initial-backfill claim after commit. The shared claim path serializes competing requests and coalesces an active job.
- **CONFIRMED:** `GET /api/projects/{projectId}/sync-history` is the paged/filterable history contract (`page`, `size`, optional `targetSystem`, `status`, `jobType`); it remains manager-only. Legacy `/sync-status` remains the compact top-20 status view.
- **BLOCKED:** There is no Project DELETE endpoint. Do not add one until an explicit dependency guard/retention design covers Team, Task, GitRepo, JiraBoard, JiraWriteOperation, document/risk/assessment and other project references.
- **Verification:** full Maven at current working tree passed 96 suites / 537 tests / 0 failures / 0 errors / 0 skipped. Provider production credentials and external GitHub runtime smoke test remain TBD.

## Update 2026-08-06 — Jira Sprint UTC và Scrum board

- **CONFIRMED:** UTC là operational timeline. Create/Update Sprint nhận ISO-8601 có offset và `JiraSprintResponse` trả Instant UTC có `Z`; không cộng cứng UTC+7 hay đổi timezone JVM.
- **CONFIRMED:** JQL chỉ chuyển cursor UTC sang `JIRA_TIME_ZONE` để tạo literal Jira. `Asia/Ho_Chi_Minh` là zone UI/JQL, không đổi instant vận hành.
- **CONFIRMED:** external Jira Agile board ID là số trong `JiraBoard.jiraBoardId`; UUID `JiraBoard.id` chỉ là ID local. Link flow discover Scrum board, zero/multiple fail closed, legacy missing/malformed ID lazy-repair trước Create Sprint.
- **CONFIRMED:** Create Sprint resolve board trước idempotency claim/mutation; canonical fetch/upsert, recovery, session/CSRF và route giữ nguyên. Provider không log raw Jira response.
- **Migration:** không cần, `jira_board.jira_board_id` đã tồn tại. **Verification:** full Maven tại `c770438` pass 94 suites / 529 tests / 0 failures / 0 errors / 0 skipped. Runtime Jira Agile access vẫn TBD.

> Mục đích: tài liệu **as-built** cho AI assistant và developer mới. Mọi kết luận về hành vi hiện tại được phân loại: **CONFIRMED** (được source/config chứng minh), **PARTIAL** (có code nhưng chưa đầy đủ), **PLANNED** (chỉ thấy trong tài liệu), **TBD** (không xác định được từ repository), **RECOMMENDED** (đề xuất, không phải hành vi hiện tại).

## 1. Metadata của bản audit

| Mục | Giá trị |
|---|---|
| Branch | `main` |
| Commit | `4f3dee969ebd7ee03a94eb1b8133987ad622c66d` (`4f3dee9`); các SHA được nêu ở phần lịch sử chỉ là checkpoint cũ |
| Thời điểm audit | 2026-08-06 (Asia/Saigon, UTC+07:00) |
| Working tree | Sạch trước task; task documentation-only chỉ cập nhật bốn Markdown được chỉ định, không sửa source/test/config/migration. |
| Java / Spring Boot | Java 17 / Spring Boot 4.1.0 |
| Profile tìm thấy | mặc định, `local`, `prod`, `test` |
| Phạm vi | `src/main`, `src/test`, `pom.xml`, cấu hình, Railway, Lambda Cognito, scripts và docs hiện hữu |

Evidence: `pom.xml`; `src/main/resources/application*.properties`; `railway.json`.

> Lưu ý lịch sử: các đoạn bên dưới gắn với SHA cũ phản ánh đúng snapshot tại thời
> điểm viết nhưng đã bị mục cập nhật 2026-08-06 supersede khi mô tả trạng thái hiện hành.

## Update 2026-08-06 — Jira Task/Sprint và Student profile

- **CONFIRMED:** repository `D:/SAGA_BE/be-clean`, branch `main`, HEAD
  `4f3dee969ebd7ee03a94eb1b8133987ad622c66d`; HEAD bằng `origin/main` và working
  tree sạch trước task. Migration mới nhất là V17. Source hiện khai báo trực tiếp
  73 HTTP route trong 20 controller; `POST /api/auth/logout` là route do Spring
  Security quản lý, không nằm trong số này.
- **CONFIRMED:** Surefire reports hiện có 90 suite, 504 test, 0 failure, 0 error,
  0 skipped. Đây là số liệu từ full Maven gần nhất; task tài liệu không chạy Maven lại.
- **CONFIRMED:** browser API tiếp tục dùng HTTP session `JSESSIONID`; frontend gửi
  `credentials: "include"`, không dùng Bearer. GET không cần CSRF; POST/PUT/PATCH/
  DELETE cần CSRF, ngoại trừ đúng hai provider webhook được cấu hình miễn.
- **CONFIRMED:** Jira là source of truth cho Task/Sprint; database SAGA là canonical
  snapshot/read model cục bộ. Mutation gọi Jira trước, sau đó fetch canonical issue
  hoặc Sprint và upsert local trước khi trả response. Source production không
  hardcode `customfield_*`; sprint/estimation field id được discovery từ Jira.
- **CONFIRMED:** mọi Task/Sprint mutation route bắt buộc `Idempotency-Key`; actor lấy
  từ `SagaPrincipal.localProfileId`. `JiraWriteOperation` persist fingerprint,
  trạng thái `PENDING`, `REMOTE_SUCCEEDED`, `COMPLETED`, `FAILED`, `UNKNOWN` và chỉ
  safe error code/remote identity; không persist token hay raw provider payload.
  Duplicate claim rollback transaction insert rồi reload canonical operation trong
  transaction `REQUIRES_NEW` khác. Recovery chỉ hoàn tất remote success đã persist,
  không blind retry mutation có outcome không rõ.
- **CONFIRMED:** Task delete gọi Jira rồi soft-delete local bằng `deletedAt`. Sprint
  delete gọi Jira, gỡ `Task.sprint`, flush association rồi soft-delete Sprint; không
  hard-delete audit, Contribution hay Peer Review data.
- **CONFIRMED:** canonical Jira Agile Sprint response có quyền replace cả
  `startDate`, `endDate`, `completeDate`, kể cả null; provider normalize offset về
  UTC `LocalDateTime`. Embedded Sprint trong issue chỉ cập nhật reference/name nên
  không clear canonical dates. Backfill, reconciliation và webhook cùng shared sync/
  hydration; distinct Sprint id được fetch tối đa một lần mỗi job, kể cả local Sprint
  còn null dates. Vì Jira Sprint snapshot không có remote `updated/version`, hai
  canonical snapshot cạnh tranh theo last-processed-wins.
- **CONFIRMED:** UUID scalar `actor_profile_id` dùng explicit JDBC `CHAR` mapping
  phù hợp V17 `CHAR(36)`; fingerprint dạng chuỗi dùng JDBC `CHAR` phù hợp
  `request_fingerprint CHAR(64)`. Giữ nguyên V17; không có V18.
- **CONFIRMED:** `GET /api/v1/courses/{courseId}/students/{studentId}` trả
  `courseId`, `studentId`, `studentCode`, `fullName`, `email`, `avatarUrl`,
  `accountStatus` và `team { teamId, teamName, roleInTeam }`. ADMIN đọc mọi Course;
  LECTURER chỉ Course được phân công; STUDENT 403; anonymous 401. Membership được
  xác định qua `TeamMember -> Team -> Course`; không có membership trả 404, legacy
  nhiều membership trả 409. `avatarUrl` hiện luôn null vì `Student` chưa có nguồn
  avatar; `accountStatus` là trạng thái tài khoản, không phải Course enrollment.
  Model chưa hỗ trợ Student thuộc Course nhưng chưa có Team vì không có
  `CourseEnrollment` độc lập.
- **TBD:** runtime production sau V17 chưa được repository chứng minh bằng đủ log
  `Initialized JPA EntityManagerFactory`, `Started BeApplication` và health HTTP 200.

### Privacy Policy public (2026-08-03)

- **CONFIRMED:** `GET /privacy` là public, anonymous và mọi role đều nhận HTML UTF-8; route dùng matcher chính xác cho `GET /privacy`, không dùng wildcard, không redirect/login và không phụ thuộc feature flag integration. Evidence: `PrivacyPolicyController#getPrivacyPolicy`, `SecurityConfig#securityFilterChain`, `PrivacyPolicyIntegrationTest`.
- **CONFIRMED:** policy được render từ `static/privacy.html`; URL liên hệ công khai lấy từ `app.privacy.contact-url` / `PRIVACY_CONTACT_URL`, chỉ chấp nhận URL absolute `http`/`https` không userinfo. Deploy phải cấu hình URL contact thực trước khi public route phục vụ 200; test dùng `https://support.example.test/saga`. Không có secret/provider credential trong HTML hoặc response.
- **CONFIRMED:** OAuth callback, provider scope, session, CORS, CSRF configuration và hai webhook CSRF exemptions không thay đổi. `POST /privacy` không có mapping và tiếp tục bị CSRF/security từ chối.

### Contribution data foundation and Jira raw task snapshot (2026-08-04)

- **CONFIRMED:** `Task` now persists Jira labels, components (`id`/`name`) and a
  canonical plain-text description; missing/null components are empty and upsert
  replaces the full snapshot. V9 adds nullable `description` and
  `components_json` for existing rows.
- **CONFIRMED:** `ContributionCalculationService` is a read-only internal service.
  It aggregates mapped GitHub commits, SAGA Documents by type, DONE Jira tasks
  (null story point = 1) and peer reviews scoped to the Project/Team.
- **SUPERSEDED 2026-08-06:** nhận định story-point/sprint field dùng tenant-specific
  id không còn đúng; source hiện discovery field id từ Jira và không hardcode
  `customfield_*`. Peer-review config precedence của snapshot này vẫn là lịch sử.
- **TBD:** final-distribution policy for invalid per-student overrides,
  all-overridden remainder, positive remainder with zero base and rounding.
- **RECOMMENDED:** classifier stays deferred; raw Jira fields are available for a
  later deterministic classifier after category rules are approved.

### Jira issue labels snapshot (2026-08-04)

- **CONFIRMED:** Jira enhanced search request thêm `labels`; `JiraIssueSnapshot.labels` là immutable `List<String>`. Missing/null/empty array thành empty list; non-array hoặc phần tử không phải string bị xử lý như provider response invalid, không stringify im lặng. Labels không thay thế `issue.id`/`issue.key` làm định danh Task.
- **CONFIRMED:** `Task.labels` lưu JSON array trong cột `task.labels_json` kiểu `TEXT` qua `StringListJsonConverter`; getter/setter dùng defensive immutable copy. V8 chỉ thêm cột nullable nên Task hiện hữu đọc thành empty list. `JiraIssueUpsertService` replace toàn bộ snapshot labels; empty snapshot xóa labels local, không merge/append.
- **CONFIRMED/PARTLY SUPERSEDED:** Jira webhook vẫn trigger shared `reconcileJira`,
  không parse labels riêng; không có normalized Label entity. Từ 2026-08-06 đã có
  Task list/detail, labels response và Jira Task create/update write-through API.

## 2. Tóm tắt nhanh hệ thống

**CONFIRMED.** SAGA là backend Spring Boot quản lý dữ liệu học thuật (lớp, môn, học kỳ, course), team/project và tích hợp Jira Cloud/GitHub để ingest, đồng bộ, lưu dữ liệu công việc/mã nguồn và audit. Authentication là browser session sau OIDC với Cognito; backend không trả OAuth token cho frontend. MySQL là datastore JPA chính; MongoDB chỉ lưu `SystemAuditLog`. Evidence: `SecurityConfig#securityFilterChain`, `CognitoAuthenticationSuccessHandler#replaceWithTokenFreeSessionAuthentication`, `SystemAuditLog`, `SystemAuditLogRepository`.

| Module | Trạng thái |
|---|---|
| OIDC/Cognito, profile local, session, CSRF/CORS | CONFIRMED |
| Master data Class/Course/Subject/Semester | CONFIRMED, API đơn giản |
| Team project và authorization theo team | CONFIRMED |
| Jira/GitHub OAuth, webhook, sync/backfill | CONFIRMED, có feature flag/config bắt buộc |
| Đánh giá/AI/risk/meeting/notification domain | PARTIAL: entity/repository tồn tại nhưng không có controller/service API tương ứng trong source audit |
| Import Excel sinh viên | PARTIAL: authorization course scope, transaction rollback, identity bind an toàn và invitation outbox đã có; parser/preview/error DTO/DB uniqueness vẫn chưa hoàn chỉnh |
| Frontend application | TBD: không nằm trong repository này |

## 3. Kiến trúc tổng thể

```mermaid
flowchart LR
    FE[Frontend trình duyệt] -->|session cookie + CSRF| BE[Spring Boot SAGA]
    BE -->|OIDC authorization-code| COG[AWS Cognito Hosted UI]
    GOOGLE[Google Identity Provider] --> COG
    COG -->|Pre Sign-up trigger| LAMBDA[AWS account-linking Lambda]
    BE --> MYSQL[(MySQL / JPA)]
    BE --> MONGO[(MongoDB / SystemAuditLog)]
    BE <-->|OAuth, API, webhook| JIRA[Jira Cloud]
    BE <-->|GitHub App/OAuth, API, webhook| GITHUB[GitHub]
    RAILWAY[Railway] --> BE
```

**CONFIRMED** từ `application.properties`, `SecurityConfig`, `infra/lambda/cognito-account-linking/index.mjs`, `railway.json`, các package `integration/**`. Railway dashboard, User Pool trigger wiring và Google IdP configuration là **TBD** vì không có quyền xem hạ tầng.

### Cấu trúc source code

| Package/thư mục | Trách nhiệm | Class chính | Trạng thái |
|---|---|---|---|
| `config` | security, CORS, OpenAPI, property binding, Mongo health, local seed | `SecurityConfig`, `CorsConfig`, `IntegrationPublicUrlValidator` | CONFIRMED |
| `security`, `auth`, `service` | OIDC claims, role, local profile, session/login/logout | `CognitoAuthenticationSuccessHandler`, `AuthenticatedProfileService` | CONFIRMED |
| `controller` | HTTP API | 20 controller có 73 HTTP route khai báo trực tiếp và 1 `@RestControllerAdvice` không endpoint | CONFIRMED tại HEAD `4f3dee9` |
| `entity`, `repository` | JPA/MySQL domain và Mongo audit | `Student`, `Team`, `Project`, `SystemAuditLog` | CONFIRMED |
| `integration/identity` | personal identity mapping/review | `IdentityMappingService`, `IdentityMappingReviewService` | CONFIRMED |
| `integration/project` | team project, Jira/GitHub link flow | `ProjectIntegrationService`, `TeamProjectService` | CONFIRMED |
| `integration/provider` | Jira/GitHub HTTP clients và DTO snapshot | `JiraProviderClientImpl`, `GitHubProviderClientImpl` | CONFIRMED |
| `integration/webhook`, `integration/sync` | verify, receipt, dispatch, upsert, scheduler | `WebhookIngestionService`, `AutomaticSyncDispatcherImpl` | CONFIRMED |
| `infra/lambda/cognito-account-linking` | Lambda account linking Google→native Cognito | `index.mjs` | CONFIRMED |
| `docs`, `scripts`, `infra` | runbook, migration preflight, Railway/Lambda resources | `railway.json` | PARTIAL/documentation support |

## 4. Authentication flow

**CONFIRMED.** FE bắt đầu bằng browser navigation đến `GET /api/auth/login`; controller trả `302 /oauth2/authorization/cognito`. Spring Security OAuth2 client dùng authorization-code và callback backend `/login/oauth2/code/cognito`. `CognitoAuthenticationSuccessHandler` extract OIDC claims, provision/synchronize profile, thay authentication bằng `SagaPrincipal` không có token, lưu `SecurityContext` vào HTTP session, rồi redirect `AUTH_SUCCESS_REDIRECT_URI`.

```mermaid
sequenceDiagram
    participant FE as Frontend/browser
    participant BE as Spring Boot
    participant C as Cognito Hosted UI
    participant G as Google/native Cognito
    participant DB as MySQL
    FE->>BE: GET /api/auth/login
    BE-->>FE: 302 /oauth2/authorization/cognito
    FE->>C: authorization request
    C->>G: đăng nhập/federation nếu chọn Google
    C-->>BE: GET /login/oauth2/code/cognito?code=...
    BE->>BE: OIDC claim + role resolution
    BE->>DB: synchronize profile
    BE->>BE: tạo JSESSIONID + SagaPrincipal token-free
    BE-->>FE: 302 AUTH_SUCCESS_REDIRECT_URI
    FE->>BE: GET /api/auth/me (credentials include)
    BE-->>FE: AuthMeResponse, đồng thời materialize CSRF cookie
```

| Câu hỏi | Kết luận |
|---|---|
| Session hay Bearer | **CONFIRMED:** HTTP session (`JSESSIONID`); OpenAPI khai báo cookie API key, không phải bearer. |
| FE nhận access/ID/refresh token? | **CONFIRMED:** Không. Success handler thay authentication bằng `SagaPrincipal`; `NoStoreOAuth2AuthorizedClientRepository` không lưu authorized client. |
| `credentials: "include"`? | **CONFIRMED cho cross-origin browser fetch:** cần cookie credential; CORS bật `allowCredentials`. |
| Login redirect hay fetch? | **CONFIRMED:** redirect/browser navigation (`302`). |
| Swagger | **CONFIRMED:** schema `JSESSIONID`; mutation vẫn chịu CSRF như mọi request unsafe. |
| Callback backend vs frontend | **CONFIRMED:** OIDC callback backend là `/login/oauth2/code/cognito`; success redirect frontend lấy từ `AUTH_SUCCESS_REDIRECT_URI`. Giá trị `http://localhost:3000/auth/callback` do người dùng cung cấp là **RUNTIME FACT DO NGƯỜI DÙNG CUNG CẤP**, không phải config repo. |

Evidence: `AuthController#login`, `SecurityConfig#securityFilterChain`, `CognitoAuthenticationSuccessHandler#onAuthenticationSuccess`, `OpenApiConfig#customOpenAPI`, `NoStoreOAuth2AuthorizedClientRepository`.

### Cognito và Lambda

- **CONFIRMED:** OIDC role lấy claim Cognito groups, normalize uppercase, ưu tiên `ADMIN`, sau đó `LECTURER`, rồi `STUDENT`. Nếu claim có nhiều group, code chọn role đầu tiên theo thứ tự này. Evidence: `CognitoRoleResolver#resolve`.
- **CONFIRMED:** OIDC identity bắt buộc Cognito subject, email hợp lệ/verified, name; Student code được trích từ local-part email với regex `([A-Za-z]{2}\d{6})$`. Evidence: `OidcIdentityService#extract`, `StudentCodeExtractor#extract`.
- **CONFIRMED:** Profile local được tìm theo `cognitoSub` và email trong `Admin`, `Lecturer`, `Student`; duplicate/khác role là conflict. Student mới có `PENDING`; admin/lecturer không có `AccountStatus`. Evidence: `AuthenticatedProfileService#synchronize`, `#create`.
- **CONFIRMED:** Lambda chỉ xử lý trigger `PreSignUp_ExternalProvider`, chỉ tin `Google`, yêu cầu email verified và link Google subject vào đúng một native Cognito user cùng email; log chỉ hash email/username. Evidence: `infra/lambda/cognito-account-linking/index.mjs#createHandler`.
- **TBD:** User Pool đã gắn Lambda trigger, Google IdP đã cấu hình, priority Cognito group thực tế và cách tạo native username/password nằm ngoài repository.
- **PLANNED/PARTIAL:** README Lambda mô tả deploy/IAM nhưng không chứng minh deployment đã thực hiện.

## 5. Role và phân quyền

| Role | Loại | Quyền được code chứng minh | Phạm vi | Evidence |
|---|---|---|---|---|
| `ADMIN` | application role | Tạo master data; override team manager; review identity mapping | toàn hệ thống/team | `ClassController#createClass`, `ProjectIntegrationAuthorizationService#requireTeamManager`, `IdentityMappingReviewService#requireReviewer` |
| `LECTURER` | application role | Team/project manager nếu là instructor của `team.course` ; review mapping của student thuộc course mình dạy | course/team liên quan | `ProjectIntegrationAuthorizationService#requireTeamManager`, `IdentityMappingReviewService#requireReviewer` |
| `STUDENT` | application role | personal integration; manager team/project nếu membership `LEADER` | team của chính student | `ProjectIntegrationAuthorizationService#requireTeamManager` |
| `LEADER` | `RoleInTeam` domain role | điều kiện cho Student quản lý integration/team project | một Team | `RoleInTeam`, `TeamMember`, authorization service |
| `MEMBER` | `RoleInTeam` domain role | membership không có quyền manager riêng trong code | một Team | `RoleInTeam` |
| `MENTOR` | `RoleInTeam` domain role | enum tồn tại; chưa thấy authorization rule riêng | TBD | `RoleInTeam` |

Application role khác team role. Một Student có thể là `LEADER`; điều này **không** biến họ thành `LECTURER` hoặc `ADMIN`.

### Authorization model

- **CONFIRMED:** mọi route trừ OAuth/login/error, static GET, health, hai webhook POST (và Swagger khi flag bật) cần authenticated session. `/api/admin/**` cần `ROLE_ADMIN`. Evidence: `SecurityConfig#securityFilterChain`.
- **CONFIRMED:** method security bật: 5 `@PreAuthorize`, 0 `@Secured`. Create Class/Course/Subject/Semester là ADMIN-only; import student chặn role tổng quát ADMIN/LECTURER, sau đó service kiểm tra course scope. Evidence: controller master data, `CourseController#importStudents`, `CourseImportAuthorizationService`.
- **CONFIRMED:** team/project integration dùng service-level ownership: ADMIN, lecturer là `Course.instructor`, hoặc student là Team LEADER. Evidence: `ProjectIntegrationAuthorizationService#requireTeamManager`.
- **CONFIRMED:** identity mapping reviewer là ADMIN, hoặc LECTURER có membership/couse instructor relationship. Evidence: `IdentityMappingReviewService#requireReviewer`.
- **CONFIRMED:** CSRF áp dụng cho HTTP unsafe; chỉ `POST /api/webhooks/github` và `POST /api/webhooks/jira` bị exempt. Evidence: `SecurityConfig#securityFilterChain`.
- **CONFIRMED:** không phải toàn bộ CRUD chỉ dành cho Lecturer. Các GET master data chỉ authenticated; import Excel cho ADMIN mọi Course hoặc LECTURER là instructor của Course; STUDENT bị từ chối. Evidence: `CourseImportAuthorizationService#requireImportAccess`.
- **TBD:** account status không được SecurityConfig hay authorization services kiểm tra để chặn API; không suy ra status policy.

## 6. API endpoint matrix

Bảng dưới là baseline lịch sử tại HEAD `200d866` và không còn exhaustive. Tại HEAD
`4f3dee9`, source có 73 HTTP route khai báo trực tiếp trong 20 controller;
Task/Sprint và Course Student Basic Info deltas được ghi trong update 2026-08-06.
`GlobalExceptionHandler` là `@RestControllerAdvice`, không khai báo endpoint.
`POST /api/auth/logout` là endpoint framework-managed, không phải controller method.
CSRF áp dụng cho POST/PUT/PATCH/DELETE, không áp dụng cho GET và chỉ miễn hai webhook POST.

| Method | Path | Controller#Method | Public/Auth | Role/scope | CSRF | Request → Response | Evidence |
|---|---|---|---|---|---|---|---|
| GET | `/privacy` | `PrivacyPolicyController#getPrivacyPolicy` | Public | anonymous/ADMIN/LECTURER/STUDENT | Không | → public HTML UTF-8 | controller + `SecurityConfig` |
| GET | `/api/auth/login` | `AuthController#login` | Public | — | Không | → 302 | controller |
| GET | `/api/auth/me` | `#me` | Auth | principal | Không | → `AuthMeResponse` | controller |
| GET | `/api/auth/csrf` | `#csrf` | Auth | principal | Không | → `CsrfTokenResponse` | controller |
| GET | `/api/v1/classes/{id}` | `ClassController#getClassById` | Auth | — | Không | → `Class` | controller |
| POST | `/api/v1/classes` | `#createClass` | Auth | ADMIN | Có | `ClassRequest` → `Class` | controller |
| GET | `/api/v1/classes` | `#getClasses` | Auth | — | Không | query → `Page<Class>` | controller |
| GET | `/api/v1/courses/{id}` | `CourseController#getCourseById` | Auth | — | Không | → `Course` | controller |
| POST | `/api/v1/courses` | `#createCourse` | Auth | ADMIN | Có | `CourseRequest` → `Course` | controller |
| GET | `/api/v1/courses` | `#getCourses` | Auth | — | Không | query → `Page<Course>` | controller |
| GET | `/api/v1/courses/instructors` | `#getLecturersForCourseAssignment` | Auth | ADMIN; anonymous 401, LECTURER/STUDENT 403 | Không | keyword chỉ fullName/email; `sortBy` fullName/email; `sortDirection` asc/desc; invalid query 400; page/size → `Page<LecturerOptionResponse>` | controller/service |
| GET | `/api/v1/courses/{courseId}/students` | `#getCourseStudents` | Auth | ADMIN mọi Course; LECTURER phải là instructor; anonymous 401, STUDENT/lecturer ngoài scope 403, Course thiếu 404 | Không | keyword; `hasTeam` all/with/without; sortBy studentCode/fullName/email/teamName/projectName; sortDirection asc/desc; invalid query 400; page/size → `CourseStudentRosterResponse` | controller/service |
| POST | `/api/v1/courses/{courseId}/import-students` | `#importStudents` | Auth | ADMIN mọi Course; LECTURER phải là instructor; STUDENT bị chặn; Team khác cùng Course cho cùng Student trả 409 | Có | multipart `file` → String | controller + `CourseImportAuthorizationService` |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/members` | `TeamRosterController#getMembers` | Auth | ADMIN mọi Team; Lecturer chỉ Course mình dạy; Student phải thuộc đúng Team, LEADER và MEMBER đều được | Không | page/size → `Page<TeamMemberResponse>` (không email/cognitoSub/version) | controller + `TeamRosterService` |
| GET | `/api/me/courses/{courseId}/team/members` | `MyCourseTeamController#getMyCourseTeamMembers` | Auth | STUDENT-only; backend lấy Student từ `SagaPrincipal.localProfileId` và tự resolve Team theo Student+Course; 404 Course/membership thiếu, 409 legacy nhiều Team | Không | page/size → `MyCourseTeamMembersResponse` | controller + `TeamRosterService` |
| GET | `/api/v1/subjects/{id}` | `SubjectController#getSubjectById` | Auth | — | Không | → `Subject` | controller |
| POST | `/api/v1/subjects` | `#createSubject` | Auth | ADMIN | Có | `SubjectRequest` → `Subject` | controller |
| GET | `/api/v1/subjects` | `#getSubjects` | Auth | — | Không | query → `Page<Subject>` | controller |
| GET | `/api/v1/semesters/{id}` | `SemesterController#getSemesterById` | Auth | — | Không | → `Semester` | controller |
| POST | `/api/v1/semesters` | `#createSemester` | Auth | ADMIN | Có | `SemesterRequest` → `Semester` | controller |
| GET | `/api/v1/semesters` | `#getSemesters` | Auth | — | Không | query → `Page<Semester>` | controller |
| POST | `/api/teams/{teamId}/projects` | `TeamProjectController#create` | Auth | team manager | Có | `CreateTeamProjectRequest` → `ProjectResponse` | `TeamProjectController#create`; `TeamProjectService#create` |
| GET | `/api/integrations/identity-mappings` | `IdentityMappingReviewController#mappings` | Auth | reviewer scope | Không | `studentId` → list | controller/service |
| PATCH | `/api/integrations/identity-mappings/{mappingId}` | `#review` | Auth | reviewer scope | Có | review DTO → connection | controller/service |
| GET | `/api/me/integrations` | `PersonalIntegrationController#connections` | Auth | own principal | Không | → connections | controller |
| GET | `/api/me/integrations/jira/connect` | `#connectJira` | Auth | own principal | Không | → 302 | controller |
| DELETE | `/api/me/integrations/jira` | `#disconnectJira` | Auth | own principal | Có | → 204 | controller |
| GET | `/api/me/integrations/github/connect` | `#connectGitHub` | Auth | own principal | Không | → 302 | controller |
| GET | `/api/me/integrations/github/callback` | `#githubCallback` | Auth | session/state | Không | → connection | controller |
| DELETE | `/api/me/integrations/github` | `#disconnectGitHub` | Auth | own principal | Có | → 204 | controller |
| GET | `/api/integrations/jira/callback` | `JiraIntegrationCallbackController#callback` | Auth | session/state | Không | → object | controller |
| GET | `/api/projects/{projectId}/integrations` | `ProjectIntegrationController#integrations` | Auth | team manager | Không | → status | controller/service |
| GET | `/api/projects/{projectId}/jira/connect` | `#jiraConnect` | Auth | team manager | Không | → 302 | controller/service |
| POST | `/api/projects/{projectId}/jira/link` | `#jiraLink` | Auth | team manager/session grant | Có | Jira DTO → status | controller/service |
| DELETE | `/api/projects/{projectId}/jira` | `#jiraDisconnect` | Auth | team manager | Có | → 204 | controller/service |
| GET | `/api/projects/{projectId}/github/install` | `#githubInstall` | Auth | team manager | Không | → 302 | controller/service |
| GET | `/api/projects/{projectId}/github/setup` | `#githubSetup` | Auth | session/state | Không | → 302 | controller/service |
| GET | `/api/projects/{projectId}/github/callback` | `#githubCallback` | Auth | session/state | Không | → installation | controller/service |
| POST | `/api/projects/{projectId}/github/repositories` | `#githubRepositories` | Auth | team manager/installation owner | Có | GitHub DTO → status | controller/service |
| DELETE | `/api/projects/{projectId}/github/repositories/{repositoryId}` | `#githubRepositoryDisconnect` | Auth | team manager | Có | → 204 | controller/service |
| GET | `/api/projects/{projectId}/sync-status` | `#syncStatus` | Auth | team manager | Không | → sync status | controller/service |
| GET | `/api/integrations/github/setup` | `ProjectIntegrationCallbackController#githubSetup` | Auth | session/state | Không | → 302 | controller |
| GET | `/api/integrations/github/project/callback` | `#githubCallback` | Auth | session/state | Không | → installation | controller |
| POST | `/api/webhooks/github` | `WebhookController#github` | Public | signature verification | Miễn | raw bytes → 200/202 | controller/service |
| POST | `/api/webhooks/jira` | `#jira` | Public | token/JWT authentication | Miễn | raw bytes → 202 | controller/service |
| GET | `/oauth2/authorization/cognito` | Spring OAuth2 authorization request filter | Public | OIDC initiation | Không | → 302 Cognito | `SecurityConfig#securityFilterChain` |
| GET | `/login/oauth2/code/cognito` | Spring OAuth2 login filter | Public | state/code validation | Không | provider callback → session/redirect | OAuth registration + `SecurityConfig` |
| POST | `/api/auth/logout` | Spring Security logout filter | Auth | own session | Có | → Cognito logout 302 | `SecurityConfig#securityFilterChain`; `CognitoLogoutSuccessHandler` |
| GET | `/actuator/health` | Spring Boot Actuator | Public | — | Không | → health JSON | `application.properties`; `SecurityConfig` |
| GET | `/v3/api-docs/**` | Springdoc | Public khi flag bật | — | Không | → OpenAPI JSON | `SecurityConfig`; `OpenApiConfig` |
| GET | `/swagger-ui/**`, `/swagger-ui.html` | Springdoc | Public khi flag bật | — | Không | → Swagger UI/assets | `SecurityConfig`; `OpenApiConfig` |

Static GET `/`, `/index.html`, `/favicon.ico`, `/assets/**`, `/css/**`, `/js/**`, `/images/**` cũng public theo `SecurityConfig`; `GET /privacy` là controller mapping public exact riêng, không phải wildcard static mapping. Swagger/OpenAPI public chỉ khi corresponding enable flag bật.

### Frontend integration contract

**CONFIRMED:** dùng browser navigation cho login/authorization redirect, `fetch`/Axios có `credentials: "include"` cho API, không dùng `Authorization: Bearer`, không đọc/lưu OAuth JWT/token trong localStorage. Sau login gọi `/api/auth/me`, lấy CSRF qua cookie hoặc `GET /api/auth/csrf`; mutation gửi `X-XSRF-TOKEN`. Swagger UI cùng origin dùng global interceptor để bootstrap/read cookie và chỉ gắn header cho unsafe method. 401/403 trả JSON error, frontend phải xử lý theo status. Logout gọi `POST /api/auth/logout` có CSRF và browser nhận redirect Cognito. Jira/GitHub completion callbacks redirect `302` về FE với opaque `resultId`; FE consume kết quả an toàn qua endpoint POST authenticated có CSRF. Evidence: `AuthController`, callback controllers, `IntegrationCallbackResultController`, `SecurityConfig`, `SwaggerUiCsrfConfiguration`, `CognitoLogoutSuccessHandler`, `docs/FRONTEND_API_INTEGRATION.md`.

**RUNTIME FACT DO NGƯỜI DÙNG CUNG CẤP:** frontend dev `http://localhost:3000`; production backend `https://saga-backend-production-3951.up.railway.app`; FE success route dự kiến `/auth/callback`; OIDC callback vẫn backend. Các giá trị chỉ hoạt động nếu environment `FRONTEND_ORIGINS`, `AUTH_SUCCESS_REDIRECT_URI`, cookie settings triển khai tương ứng.

**RUNTIME FACT DO NGƯỜI DÙNG CUNG CẤP:** `/privacy` đã public thành công; Atlassian Distribution đã ở Sharing; Privacy Policy URL đã được cấu hình. Không có URL contact hoặc secret được ghi tại đây.

## 7. CORS, cookie và CSRF

- **CONFIRMED:** origin lấy từ `app.cors.allowed-origins=${FRONTEND_ORIGINS}`, cấm wildcard/path/query; methods GET/POST/PUT/PATCH/DELETE/OPTIONS; allowed request headers `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`; expose `Location`; `allowCredentials=true`; preflight cache 3600s. `Idempotency-Key` là bắt buộc cho Jira Task/Sprint mutation nên phải được CORS preflight cho phép khi FE gọi cross-origin. Evidence: `CorsConfig#corsConfigurationSource`, `SecurityIntegrationTest`.
- **CONFIRMED:** CSRF cookie repository là `CookieCsrfTokenRepository.withHttpOnlyFalse()`, path `/`, cookie name mặc định `XSRF-TOKEN`; request handler mặc định đọc `X-XSRF-TOKEN`; webhook exempt. Evidence: `SecurityConfig#csrfTokenRepository`, `#securityFilterChain`.
- **CONFIRMED:** `JSESSIONID` HttpOnly do servlet session cookie; CSRF cookie deliberately không HttpOnly. Local sets `secure=false`, `same-site=lax`; prod defaults `SESSION_COOKIE_SECURE=true`, `SESSION_COOKIE_SAME_SITE=none`. Evidence: `application-local.properties`, `application-prod.properties`.
- **CONFIRMED:** `/api/auth/csrf` tồn tại và trả token/header/parameter, không trả session/OAuth secret. Evidence: `AuthController#csrf`, `CsrfTokenResponse`.
- **RỦI RO / PARTIAL:** localhost HTTP → Railway HTTPS là cross-origin và thường là cross-site (schemeful same-site); browser có thể chặn third-party cookie dù SameSite=None; Secure. `document.cookie` ở FE origin **không thể** đọc cookie domain Railway. FE có thể gọi `/api/auth/csrf` với credentials để nhận token JSON; mutation cross-origin vẫn phụ thuộc browser cho phép credential cookie. Đây là constraint trình duyệt, không phải code chứng minh production đang hoạt động.
- **RECOMMENDED:** kiểm thử bằng browser production-like và cân nhắc cùng-site custom domain/BFF nếu third-party cookies bị chặn.

## 8. Configuration và environment variables

Không tìm thấy `application.yml`/`application-*.yml`; project dùng `application.properties`, `application-local.properties`, `application-prod.properties` và `application-test.properties`. Không ghi giá trị secret. Các property dưới đây lấy từ các file này và `.env.example`.

| Property | Environment variable | Profile/default | Secret | Mục đích |
|---|---|---|---|---|
| `server.port` | `PORT` | `8080` | Không | HTTP port |
| `app.public-base-url` | `PUBLIC_BASE_URL` | local có `http://localhost:8080` | Không | public backend origin |
| `app.cors.allowed-origins` | `FRONTEND_ORIGINS` | bắt buộc | Không | explicit CORS origins |
| auth success/logout/domain | `AUTH_SUCCESS_REDIRECT_URI`, `AUTH_LOGOUT_REDIRECT_URI`, `COGNITO_DOMAIN` | logout fallback success | domain không secret | redirect/logout |
| OIDC issuer/client | `COGNITO_ISSUER_URI`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET` | bắt buộc | client secret | Cognito client |
| session cookie secure/same-site | `SESSION_COOKIE_SECURE`, `SESSION_COOKIE_SAME_SITE`; Spring relaxed names `SERVER_SERVLET_SESSION_COOKIE_SECURE`, `SERVER_SERVLET_SESSION_COOKIE_SAME_SITE` cũng map property | local false/lax; prod true/none | Không | session/CSRF cross-site |
| MySQL | `DATABASE_JDBC_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`; legacy `AIVEN_*` | — | password | JPA datasource |
| Flyway | `FLYWAY_ENABLED`, `FLYWAY_BASELINE_ON_MIGRATE` | false | Không | schema migration |
| Mongo | `MONGO_URI`, `MONGO_DATABASE`, `MONGO_HEALTH_TIMEOUT` | timeout PT5S | URI | Mongo audit |
| Swagger | `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`, legacy `SWAGGER_ENABLED` | local true; default false | Không | OpenAPI UI |
| Jira | `JIRA_*` và `JIRA_INTEGRATION_ENABLED`, `JIRA_TIME_ZONE` | enabled true base; local false | client secret | Jira OAuth/webhook |
| GitHub | `GITHUB_*`, `GITHUB_INTEGRATION_ENABLED` | enabled true base; local false | secret/private key/webhook secret | GitHub App/OAuth/webhook |
| encryption | `INTEGRATION_TOKEN_ENCRYPTION_KEY`, `_KEY_ID`, `_PREVIOUS_KEYS` | key id `primary` | keys | encrypt integration credentials |
| scheduler/network | `INTEGRATION_*`, `SYNC_JOB_STALE_AFTER`, `STALE_SYNC_JOB_RECOVERY_DELAY_MS` | defined defaults | Không | sync/reconciliation |
| local seed | `LOCAL_DEMO_SEED_ENABLED`, `LOCAL_DEMO_LEADER_COGNITO_SUB` | disabled | subject identifier | local demo only |

## 9. Database và domain model

**CONFIRMED:** 40 JPA entities use MySQL; `SystemAuditLog` is one Mongo document. Hibernate validates (`ddl-auto=validate`) outside tests; Flyway migrations V2–V7 are committed, baseline V1 is intentionally external/legacy. No generic soft-delete field/annotation found. Evidence: `application.properties`, `src/main/resources/db/migration/*`, entities.

```mermaid
erDiagram
    CLASS ||--o{ COURSE : clazz
    COURSE ||--o{ TEAM : course
    TEAM ||--o{ TEAM_MEMBER : team
    STUDENT ||--o{ TEAM_MEMBER : student
    TEAM ||--o| PROJECT : project
    PROJECT ||--o| JIRA_BOARD : board
    PROJECT ||--o{ GIT_REPO : repositories
    STUDENT ||--o{ IDENTITY_MAP : identities
    PROJECT ||--o{ SYNC_JOB_LOG : jobs
    GIT_REPO ||--o{ COMMIT_DATA : commits
    GIT_REPO ||--o{ GIT_ISSUE : issues
```

| Nhóm | Datastore/repository | Quan hệ hoặc unique đáng chú ý | Trạng thái |
|---|---|---|---|
| `Admin`, `Lecturer`, `Student` | MySQL / repo tương ứng | Student unique `cognitoSub`, `studentCode`, `email`; status ACTIVE/INACTIVE/SUSPENDED/PENDING | CONFIRMED |
| Class/Subject/Semester/Course | MySQL | Course→Class/Subject/Semester/Lecturer | CONFIRMED |
| Team/TeamMember/Project | MySQL | Team→Course; Team→Project one-to-one unique; member has domain `RoleInTeam`. Product rule: Student tối đa một Team mỗi Course; application guard có, DB invariant trực tiếp chưa có | CONFIRMED/PARTIAL |
| Jira/GitHub integration | MySQL | `JiraBoard`, `GitHubInstallation`, `GitRepo`, migration unique external IDs | CONFIRMED |
| external data | MySQL | task/sprint/issue/PR/review/commit/comment, upsert/dedup constraints migration | CONFIRMED |
| audit | Mongo / `SystemAuditLogRepository` | collection `system_audit_log` | CONFIRMED |
| assessment/risk/meeting/document/AI | MySQL entities | relationships exist in annotations; no full HTTP use-case proven | PARTIAL |

Transactions are declared on service methods, notably profile sync, team/project/integration/sync and `ExcelImportService#importStudentsToCourse`. Cascade behavior is entity-specific; no global rule should be assumed. `IntegrationSecretCipher` handles encrypted provider credentials; do not log/decrypt values.

## 10. Jira integration

**CONFIRMED:** project and personal Jira use OAuth state bound to HTTP session; callback exchanges code, stores encrypted credential/board state, then project linking may register dynamic webhook and trigger sync. Webhook ingestion validates Jira token/JWT, persists receipt then dispatcher processes/upserts with cursor/overlap window, job log and reconciliation/stale-job scheduler. Availability flag returns safe `INTEGRATION_NOT_CONFIGURED` when disabled.

Key classes: `PersonalIntegrationService`, `JiraOAuthCallbackService`, `ProjectIntegrationService#beginJira/#linkJira`, `JiraProviderClientImpl`, `JiraCredentialService`, `WebhookIngestionService`, `JiraWebhookAuthenticator`, `AutomaticSyncDispatcherImpl`, `JiraSyncJobService`, `JiraWebhookMaintenanceService`.

## 11. GitHub integration

**CONFIRMED:** GitHub App/OAuth routes support personal connect and project installation/setup/callback, then repository link. `ProjectIntegrationAuthorizationService` and installation-owner checks constrain project flow; webhook HMAC signature is verified before receipt processing. Initial backfill/reconciliation dispatch GitHub snapshots into deduplicating upsert services; installation token uses App private key but value is never documented here.

**CONFIRMED:** `GitHubSyncJobService#claim` là claim dùng chung cho initial backfill và reconciliation. Transaction `REQUIRES_NEW` khóa đúng row `GitRepo` bằng `PESSIMISTIC_WRITE`, kiểm tra active job rồi mới tạo `SyncJobLog` `IN_PROGRESS`; cùng GitRepo có active non-stale job sẽ coalesce, GitRepo khác vẫn song song. `GitRepoStateService` reload/khóa row managed theo id cho complete/degrade; không save entity cũ đã đi qua provider I/O. `SyncJobFinalizationService` finalize bằng jobId trong `REQUIRES_NEW`, khóa job và giữ nguyên terminal state. Scheduler stale recovery cover GitHub job quá ngưỡng cấu hình.

Key classes: `ProjectIntegrationService#beginGitHubInstallation/#linkGitHubRepositories`, `GitHubProviderClientImpl`, `GitHubWebhookSignatureVerifier`, `GitHubInitialBackfillJobService`, `GitHubSyncJobService`, `GitRepoStateService`, `SyncJobFinalizationService`, `SyncJobStaleRecoveryScheduler`, `GitHubDataUpsertService`, `WebhookReceiptProcessor`.

### Timestamp sync vận hành (2026-08-04)

- **CONFIRMED:** `SyncJobLog.startedAt`/`completedAt` vẫn là `LocalDateTime` và schema vẫn dùng `DATETIME(6)`, nhưng các write path production của SyncJobLog dùng `Clock.systemUTC()` và `LocalDateTime.ofInstant(..., ZoneOffset.UTC)`.
- **CONFIRMED:** `SyncStatusResponse.Job` map hai field này sang `Instant`; JSON trả UTC offset rõ ràng, ví dụ `2026-08-04T05:13:49Z`. `null` vẫn là `null`.
- **CONFIRMED:** không cộng cứng UTC+7, không đổi timezone JVM/Railway hay `JIRA_TIME_ZONE`. Các `LocalDateTime` nghiệp vụ khác không được suy diễn là UTC chỉ từ contract này.
- **HISTORICAL/SUPERSEDED:** quét source tại `0bc30be` từng có 16 REST controller,
  46 controller HTTP methods và full Maven 70 suites/299 tests. Số hiện hành tại
  `4f3dee9` là 20 controller/73 routes và 90 suites/504 tests như update 2026-08-06.

## 12. Deployment

**CONFIRMED từ repository:** Railway uses Railpack, builds `mvn clean package -DskipTests`, starts jar, health checks `/actuator/health`, restarts on failure up to 10. `server.port` honors `PORT`; forwarded headers strategy is `framework`. Local profile enables Swagger and disables integrations; prod defaults Secure+SameSite=None. Evidence: `railway.json`, application profiles.

**TBD:** Railway variables, actual active profile, database connectivity, session persistence across redeploy/horizontal replicas, deployed Lambda and real production URL/dashboard. HTTP session is in-memory by default in the shown code; absent shared session store, loss after restart and non-sticky multi-replica risk is **RECOMMENDED to verify**, not a confirmed deployment fact.

### Error handling

| Status | Trường hợp | Format/evidence |
|---|---|---|
| 400 | invalid integration input/callback/config request | `IntegrationException.invalid`, `GlobalExceptionHandler` |
| 401 | unauthenticated | `JsonAuthenticationEntryPoint`, `UnauthenticatedRequestException` |
| 403 | authorization hoặc CSRF | `JsonAccessDeniedHandler`; CSRF filter |
| 404 | master data not found | services throw `ResponseStatusException` |
| 409 | identity conflict/project duplication | `IdentityConflictException`, `IntegrationException.conflict` |
| 422 | invalid OIDC identity | `InvalidIdentityException` |
| 500 | uncaught runtime/validation/import paths | Spring default; no uniform custom handler proven |
| 502/503 | provider failure/not configured | `IdentityServiceException`, `IntegrationException` |

`ApiErrorResponse` fields: timestamp/status/error/message/path. Integration errors use stable `error` code; framework validation and `ResponseStatusException` body are not normalized by this advice. Stack trace hiding is configured in `application.properties`.

### Test hiện có

Có 62 test source classes. **CONFIRMED tại HEAD `200d866`:** full `./mvnw.cmd test` pass 60 suites / 278 tests / 0 failures / 0 errors / 0 skipped. `JiraProviderClientImplTest` bao phủ field request, labels/components missing/null/empty/multiple, ADF description và invalid provider shape; `JiraIssueUpsertServiceTest` bao phủ replace-all Task snapshots; `TaskLabelsPersistenceTest` bao phủ H2 round-trip và legacy null. `ContributionAggregationRepositoryTest` bao phủ Project/Student scoping và null story point = 1; `ContributionCalculationServiceTest` bao phủ formulas, no-review default, configured multiplier, valid overrides, deterministic result và precedence ambiguity fail-closed. `PrivacyPolicyIntegrationTest` bao phủ anonymous, tất cả application roles, HTML UTF-8, absence của literal test credential, POST không có route public và protected API vẫn 401; `PrivacyPolicyControllerTest` bao phủ URL contact thiếu/sai trả 503 có kiểm soát. `MyCourseTeamMembersIntegrationTest` bao phủ Student self-scope, 401/403/404/409, project nullable, privacy, pagination/400, multi-Course và OpenAPI. `CourseRosterAndLecturerOptionsIntegrationTest` bao phủ authorization, filter/sort/pagination, invalid query, email exposure và legacy invalid data nhiều Team không crash. `CourseTeamMembershipGuardIntegrationTest` bao phủ idempotency, conflict 409, role độc lập khác Course và hai transaction cạnh tranh. Provisioning/invitation tests bao phủ reuse imported Student, conflict, membership/role preservation, competitive bind, outbox dedup/template/failure/retry, concurrent claim và stale recovery. Maven dùng Java runtime 21.0.7 trên máy audit, trong khi project compile target Java 17.

Evidence: `src/test/java/**`, `infra/lambda/cognito-account-linking/test/index.test.mjs`.

## Update 2026-08-04 — integration callback result handoff

- **CONFIRMED:** Four Jira/GitHub OAuth completion callbacks now return `302 Found` to the configured frontend integration callback URI. URL query contains only cryptographically random opaque `resultId`; provider code, state, token, secret and raw payload are never included.
- **CONFIRMED:** `POST /api/integrations/callback-results/{resultId}/consume` is authenticated and uses global CSRF. Results are session/principal-bound, expire by configured TTL, are read-once, and project results recheck manager authorization while personal results remain Student-only.
- **PARTIAL:** No shared Spring Session store is configured, so restart/multi-instance session continuity remains deployment TBD.

## 13. Known issues

| Severity | Vấn đề | Evidence | Khuyến nghị |
|---|---|---|---|
| Medium | Import tạo Student `PENDING` không có Cognito sub cho tới lần login đầu; binding contract đã có nhưng deployed Cognito self-sign-up vẫn TBD | `ExcelImportService#importStudentsToCourse`, `AuthenticatedProfileService#synchronize` | E2E với Cognito deployment thật |
| High | Import chỉ check tên `.xlsx`, sheet đầu tiên, magic indexes; không header/email/group/leader/duplicate validation | `ExcelImportService` | không deploy như import production |
| Medium | Membership guard ở application level theo Student+Course; chưa có database invariant trực tiếp `UNIQUE(student_id, course_id)` | `ExcelImportService`, `StudentRepository#findForTeamMembershipWriteById`, `TeamMemberRepository#findByStudentIdAndTeamCourseId` | RECOMMENDED: thiết kế migration/invariant DB sau khi có kế hoạch xử lý legacy data |
| Medium | API master-data trả JPA entities trực tiếp | Class/Course/Subject/Semester controllers | tạo response DTO ổn định trước FE lớn |
| Medium | Error format không thống nhất cho validation/ResponseStatusException/uncaught runtime | `GlobalExceptionHandler` | thêm error advice chung |
| Medium | Cross-site cookie/CSRF giữa localhost và Railway phụ thuộc browser third-party cookie policy | CORS/security/prod profile | E2E browser test và chiến lược same-site |
| Low | Swagger interceptor chỉ đọc được cookie backend khi Swagger UI cùng origin API; browser E2E cross-site frontend vẫn phải kiểm chứng riêng | `SwaggerUiCsrfConfiguration`, `AuthController#csrf` | dùng `/api/auth/csrf` cho FE khác origin và chạy browser E2E |
| Low | `AccountStatus` có thể null cho Admin/Lecturer và `AuthMeResponse` có null | profile service/DTO | xác định UI contract, không render string `"null"` |

### Việc cần làm tiếp theo

### Bắt buộc trước khi FE tích hợp

1. Hoàn thiện import Excel: preview, validation/error DTO và production email provider. Permission ADMIN/lecturer scope, auth/CSRF/rollback/idempotency/identity-binding và membership concurrency guard đã có test.
2. Chốt FE cookie topology và test browser cross-origin. Rủi ro: login/API mutation không giữ session. Xác minh: `me`, `csrf`, POST từ origin FE thật.
3. Chuẩn hóa error contract cho validation/404/runtime. Rủi ro: FE không parse được lỗi. Xác minh: contract tests.

### Bắt buộc trước production

1. Set và validate toàn bộ secret/environment trên Railway, không commit `.env`; `IntegrationPublicUrlValidator`. Xác minh: startup safe and health.
2. Xác minh session persistence/scaling/redeploy. Rủi ro: logout ngẫu nhiên. Xác minh: rolling restart/multiple instances.
3. Run full Maven + Lambda test suites; Railway currently builds with `-DskipTests`.

### Nên cải thiện sau

1. DTO cho master data; 2. API/authorization coverage tests; 3. expose only intentional operational endpoints; 4. document/develop assessment domain APIs when implemented.

### Runbook kiểm tra nhanh

1. Local: copy `.env.example` to untracked `.env`, populate placeholders without sharing secrets; select `local` profile; run `./mvnw spring-boot:run` (Windows: `./mvnw.cmd`).
2. Health: `GET /actuator/health` is public. Swagger only when its flags are enabled.
3. Login: navigate browser to `/api/auth/login`, finish Cognito, then call `/api/auth/me` with credentials.
4. CSRF: call `GET /api/auth/csrf`; send returned token in `X-XSRF-TOKEN` plus session cookie for unsafe API.
5. Role checks: exercise ADMIN create master data; lecturer/student/team-leader project integration negative and positive cases using seeded/controlled data.
6. Jira/GitHub: only enable configured provider in a non-production test tenant; verify connect callback, webhook signature/auth and sync status.
7. CORS/cookie: inspect browser Network/Application, not just curl; verify preflight, JSESSIONID, XSRF token and a mutation.
8. Railway: inspect dashboard logs/health/environment separately; it is outside this repository.

## 14. Context tóm tắt cho AI

```text
PROJECT: SAGA Spring Boot backend.
CURRENT ARCHITECTURE: Spring MVC + Spring Security/OIDC + MySQL JPA + Mongo audit + Jira/GitHub integrations.
AUTH MODEL: Cognito OIDC authorization-code; browser JSESSIONID; SagaPrincipal replaces token-bearing auth.
ROLES: ADMIN, LECTURER, STUDENT; team roles LEADER/MEMBER/MENTOR.
FRONTEND ORIGIN: Configured by FRONTEND_ORIGINS; localhost value is user runtime fact.
BACKEND ORIGIN: Configured by PUBLIC_BASE_URL; Railway URL is user runtime fact.
LOGIN ENTRY: GET /api/auth/login (302).
OIDC CALLBACK: /login/oauth2/code/cognito.
CURRENT USER ENDPOINT: GET /api/auth/me.
SESSION: HTTP session; no bearer/OAuth token returned to FE.
CSRF: XSRF-TOKEN cookie / X-XSRF-TOKEN header; /api/auth/csrf exists; webhooks exempt.
DATABASES: MySQL for JPA; MongoDB system_audit_log only.
INTEGRATIONS: Jira OAuth/webhook/sync; GitHub App/OAuth/webhook/backfill.
DEPLOYMENT: Railway config exists; runtime dashboard/state TBD.
KNOWN ISSUES: Excel import identity/validation contract is incomplete; cookie cross-site risk; inconsistent error DTOs.
CURRENT NEXT STEP: Complete import validation/provider delivery and browser E2E CORS/CSRF/session testing.
```

## Update 2026-08-02 — Imported Student provisioning and invitation outbox

- **CONFIRMED:** Import normalizes email (trim/lowercase) and student code (trim/uppercase). Existing data is reused only when both values identify the same Student; a partial or split match is a 409 conflict.
- **CONFIRMED:** A STUDENT login first looks up `cognitoSub`. If absent, verified Cognito email plus the existing `StudentCodeExtractor` result must both identify one unlinked Student. The bind uses a transaction and pessimistic row lock; it writes only `cognitoSub` and changes `PENDING` to `ACTIVE`. It never creates/replaces TeamMember, Team, Course, email or student code.
- **CONFIRMED:** `ACTIVE` remains active; `INACTIVE` and `SUSPENDED` are not auto-activated. Admin/Lecturer provisioning retains its former path.
- **CONFIRMED:** Import creates a deduplicated `student_course_invitation` outbox record after a TeamMember exists. V6 stores the outbox unique key; V7 supplies the Student optimistic-lock version/backfill required before Hibernate validate. AFTER_COMMIT processing records `SENT` or `FAILED`, retries pending/failed work up to five attempts, and only reclaims stale `PROCESSING` claims after configurable timeout. Delivery failure never rolls back import; delivery semantics are at-least-once.
- **PARTIAL:** The default adapter deliberately reports delivery unavailable. No production email provider/dependency/configuration exists in this repository, so production delivery is **TBD**.
- **TBD:** Spreadsheet header/schema validation, preview, row-level error DTO, a database unique constraint for `team_member(team_id, student_id)`, and deployed Cognito self-sign-up configuration.
- **CONFIRMED:** Login URL is configuration-driven by `STUDENT_INVITATION_LOGIN_URL` through `app.student-invitation.login-url`; no localhost/Railway URL or callback route is hard-coded.
- **CONFIRMED:** Logout is Spring Security framework-managed: `POST /api/auth/logout` needs `X-XSRF-TOKEN`, returns 302 to Cognito with valid CSRF and 403 otherwise. Swagger fetch can show `Failed to fetch` for the cross-origin Cognito redirect; browser clients use top-level form/navigation.
- **CONFIRMED:** Team roster is paged and never serializes Student email, Cognito subject or version. Its 401/403/404 contract is covered by integration tests.
- **Runtime fact (user-provided):** a Railway deployment failed because `student.version` was absent. V6/V7 must run before Hibernate validate; no production migration log is in this repository, therefore production migration state remains TBD.
- **Historical verification at this 2026-08-02 update:** full `./mvnw.cmd test` then passed 55 suites / 257 tests / 0 failures / 0 errors / 0 skipped. The current checkpoint is recorded separately as 60 / 278.
DO NOT ASSUME: FE implementation, infrastructure wiring, deployment variables, User Pool trigger setup, session scaling, or unimplemented assessment APIs.

## Update 2026-08-03 — Course roster, lecturer options và one-Team-per-Course guard

- **CONFIRMED:** `GET /api/v1/courses/{courseId}/students` was introduced at historical checkpoint `52a8c71` and remains in current HEAD `200d866`: ADMIN mọi Course và LECTURER là instructor; anonymous 401, STUDENT/lecturer ngoài scope 403, Course không có 404. GET không cần CSRF. `hasTeam` chỉ nhận `all|with|without`; roster whitelist `studentCode|fullName|email|teamName|projectName`, direction `asc|desc`; invalid query 400. Filter/sort chạy trước pagination, metadata tính trên toàn bộ tập sau filter và tie-break ổn định theo id.
- **PARTIAL:** roster materialize từ `TeamMember -> Team -> Course`; invitation outbox không phải enrollment source. Không có quan hệ Student–Course độc lập cho Student chưa có Team nên `studentsWithoutTeam`/`hasTeam=without` hiện rỗng, không phải feature đầy đủ. Legacy invalid data nhiều Team cùng Course chỉ được đọc không crash, không phải business behavior hợp lệ.
- **CONFIRMED:** lecturer options were introduced at historical checkpoint `52a8c71` and remain in current HEAD `200d866`: ADMIN-only (anonymous 401; LECTURER/STUDENT 403), keyword chỉ `fullName`/`email`, không tìm/trả `cognitoSub`; whitelist sort `fullName|email`, direction `asc|desc`, invalid query 400 và GET không cần CSRF.
- **ACCEPTED bởi Product Owner:** Student có thể thuộc nhiều Course nhưng tối đa một Team trong mỗi Course; `RoleInTeam` và Project độc lập theo Team/Course. Nhiều Team/Project trong một Course hợp lệ nếu mỗi Project thuộc Team khác; không hợp lệ khi cùng Student ở hai Team khác nhau trong cùng Course.
- **CONFIRMED:** the TeamMember write guard was introduced at historical checkpoint `52a8c71` and remains in current HEAD `200d866`: `ExcelImportService` là production write path duy nhất tạo TeamMember. Service lock Student bằng `PESSIMISTIC_WRITE`, rồi query Student+Course: chưa có membership thì tạo; cùng Team thì idempotent, không đổi role; Team khác cùng Course là 409, không move/delete/update membership cũ; Course khác hợp lệ. Local seed không tạo dữ liệu trái rule.
- **PARTIAL:** concurrency guard application đã được kiểm thử bằng hai thread và hai transaction độc lập; database chưa có invariant trực tiếp `UNIQUE(student_id, course_id)`, nên chỉ bảo vệ các write path tuân thủ guard. Email Student trong roster và email Lecturer trong options hiện được trả cho actor đã được authorize, nhưng business/UI justification cho hai field vẫn **TBD**; response không chứa `cognitoSub`, version, token hay credential.

## Update 2026-08-03 — Student self-scoped course team roster

- **CONFIRMED:** `GET /api/me/courses/{courseId}/team/members` was committed at `250f514` and remains in current HEAD `200d866`: STUDENT-only, browser session `JSESSIONID`/`SagaPrincipal`, không nhận `studentId` hay `teamId` và GET không cần CSRF. ADMIN/LECTURER 403, anonymous 401.
- **CONFIRMED:** backend kiểm tra Course tồn tại, lấy Student từ `SagaPrincipal.localProfileId`, query `TeamMember` theo Student+Course. Không có membership trả 404; đúng một membership thì trả Team, Project nullable và `Page<TeamMemberResponse>`; legacy nhiều membership trả 409 an toàn, không chọn Team đầu tiên hay sửa dữ liệu.
- **CONFIRMED:** endpoint tái sử dụng đọc page của `TeamRosterService`; endpoint cũ `/api/v1/courses/{courseId}/teams/{teamId}/members` giữ nguyên authorization và response contract. Response mới trả `courseId`, resolved `teamId`, `teamName`, role hiện tại, Project id/name nullable và members; không trả email, `cognitoSub`, version, session, CSRF, token hay credential.

## Cập nhật 2026-08-04 — độ tin cậy GitHub reconciliation

- **CONFIRMED:** initial backfill, scheduler reconciliation và webhook-triggered reconciliation cùng dùng claim theo `GitRepo`; không có lock toàn bảng hay in-memory lock làm bảo vệ chính.
- **CONFIRMED:** lỗi optimistic lock khi degrade được cô lập; finalization theo jobId vẫn được gọi độc lập. Không retry toàn bộ provider sync và không sửa thủ công `@Version`.
- **PARTIAL:** source chứng minh stale detached `GitRepo` có thể tạo lỗi đã thấy; external writer production cụ thể vẫn cần runtime observation sau deploy.
- **TBD:** row GitHub cũ trên production đã về terminal state hay chưa, cho tới khi quan sát DB/log sau deploy.

### Traceability index

| Chủ đề | File/class chính | Method/điểm đọc |
|---|---|---|
| URL authorization/CSRF/session | `SecurityConfig` | `securityFilterChain`, `csrfTokenRepository` |
| CORS | `CorsConfig` | `corsConfigurationSource` |
| OIDC success/profile | `CognitoAuthenticationSuccessHandler`, `AuthenticatedProfileService` | `onAuthenticationSuccess`, `synchronize` |
| Role priority | `CognitoRoleResolver` | `resolve` |
| Team authorization | `ProjectIntegrationAuthorizationService` | `requireTeamManager` |
| Identity review | `IdentityMappingReviewService` | `requireReviewer` |
| HTTP API | `src/main/java/com/saga/be/controller/*` | mappings listed in section 9 |
| Jira/GitHub | `integration/project`, `provider`, `webhook`, `sync` | methods cited sections 14–15 |
| UTC sync status | `SyncStatusResponse`, `JiraSyncJobService`, `GitHubSyncJobService`, `SyncJobFinalizationService` | `Job#from`, `claim`, `finalizeJob` |
| GitHub concurrency | `GitHubSyncJobService`, `GitRepoStateService`, `SyncJobStaleRecoveryScheduler` | `claim`, `complete/degrade`, `recoverStaleJobs` |
| Lambda link | `infra/lambda/cognito-account-linking/index.mjs` | `createHandler` |
| deployment/config | `application*.properties`, `railway.json`, `.env.example` | property tables |

Không có password, credential, token, private key, encryption key, webhook secret, session cookie hoặc CSRF token thực tế trong tài liệu này.
## Cập nhật 2026-08-05 — Lecturer Analytics

**CONFIRMED:** tám Lecturer Analytics GET APIs dùng `JSESSIONID`, không cần CSRF,
và áp ownership Course trong service trước khi resolve Team/Student. DTO riêng không
trả Cognito subject, provider credential, token hay JPA entity/version.

**PARTIAL:** data model không có Sprint commitment snapshot hoặc Jira transition history.
Velocity dùng `currentPlannedPoints`; activities chỉ Commit/Document; warning chỉ overdue Task;
heatmap chỉ Commit UTC; interaction chỉ Peer Review record thật.

Test checkpoint của milestone: 77 Surefire suites / 339 tests / 0 failures /
0 errors / 0 skipped; targeted analytics 21 tests, Team roster security 13 tests
và reliability regression 20 tests đều pass.

## Cập nhật 2026-08-09 — Rubric schema repair M4-R2

- **CONFIRMED (runtime fact trước V22 do người dùng cung cấp):** Flyway production
  đã ghi V10 và V13 thành công; `rubric_template.subject_id` là `CHAR(36) NOT NULL`,
  bảng có 0 row và V10 tạo hai FK cùng trỏ `subject(id)`.
- **CONFIRMED:** V22 chỉ dành cho **EXISTING_BASELINED_DB_UPGRADE**: đổi
  `rubric_template.subject_id` thành nullable, không seed/sửa/xóa rubric và không
  đổi Peer Review. `RubricTemplate.subject` đã nullable trong JPA.
- **PARTIAL:** **REPLAY_FROM_EXTERNAL_V1_BASELINE** cần baseline legacy thật ngoài
  repository. V13 vẫn cố seed `subject_id = NULL` khi bảng rỗng, nên replay path cần
  quyết định compatibility/baseline riêng; không thêm migration trước V13 trong M4-R2.
- **BLOCKED:** **TRUE_EMPTY_DATABASE_BOOTSTRAP** là
  `BLOCKED_EXISTING_BASELINE_GAP`, vì V1 là external/legacy và không được tạo lại
  trong repository. Đây không được gọi là lỗi riêng của API Peer Review.
- **CONFIRMED:** `RubricMigrationContractTest` khóa source V10/V13 và nội dung tối
  thiểu của V22. Full Maven pass 105 suites / 646 tests / 0 failures / 0 errors /
  0 skipped. Đây không phải MySQL execution evidence.

### Runtime verification production sau V22 — 2026-08-09

- **CONFIRMED (runtime fact do người dùng cung cấp):** V19, V20, V21 và V22 đều
  `SUCCESS`. `semester.deleted_at` và `course.deleted_at` là nullable `datetime(6)`;
  `lecturer.account_status` là `varchar(20) NOT NULL DEFAULT 'ACTIVE'`;
  `rubric_template.subject_id` là nullable `char(36)` và rubric row count vẫn 0.
- **CONFIRMED:** duplicate FK rubric subject vẫn tồn tại nhưng không chặn V22 alter.
  Không suy diễn rằng FK đã được cleanup; không có seed rubric, Admin CRUD hay thay
  đổi Peer Review.

## Cập nhật 2026-08-09 — Admin global rubric M4B

- **CONFIRMED:** `RubricTemplate` có `deletedAt`; V23 chỉ bổ sung
  `rubric_template.deleted_at DATETIME(6) NULL`. Không sửa V10/V13/V22, không
  seed, không drop duplicate FK. Runtime production V23 đã **CONFIRMED** thành công.
- **CONFIRMED:** ADMIN qua browser session + CSRF có `POST`, `PUT`, `DELETE`
  `/api/admin/peer-review-rubrics`; chỉ quản lý rubric global active
  (`subject_id = NULL`). Không có bearer, batch API hay CRUD subject-specific.
- **CONFIRMED:** criteriaName được trim và non-blank; weight bắt buộc;
  description nullable. Tối đa 4 global rubric active, cho phép 0; không
  enforce tổng weight = 100 hay uniqueness criteriaName.
- **CONFIRMED:** DELETE chỉ set tombstone, không hard-delete/cascade. Resolver
  cấu hình hiện tại chỉ lấy active global rồi fallback active Subject; reference history
  vẫn dereference được tombstone. `PeerReviewRequest` `@Size(max = 4)`, scoring
  và Contribution không đổi.

## Cập nhật 2026-08-09 — Admin Course progress overview M5

- **CONFIRMED:** `GET /api/admin/course-progress-overview` là ADMIN-only, GET session
  browser, không cần CSRF và chỉ đọc DB local. Response phân trang Course active theo
  `courseCode`, rồi `id`; filter tùy chọn `keyword`, `semesterId`, `lecturerId` chạy tại DB.
- **CONFIRMED:** mỗi Course chỉ trả count hiện tại: Team, Student distinct, Project,
  Sprint active/non-deleted, Sprint state `active`/`closed` và PeerReview. Đây không
  phải final grade, completion percentage, finalization hay contribution snapshot.
- **TBD:** Assessment không có HTTP/lifecycle ứng dụng chứng minh; PeerReview không có
  denominator obligation để suy diễn phần trăm completion. Contribution là aggregate
  hiện tại nhưng không được chạy theo toàn bộ Team trong endpoint này.

## Cập nhật 2026-08-09 — Admin Course report export M6

- **CONFIRMED:** `GET /api/admin/reports/courses/{courseId}/export` là ADMIN-only,
  session browser, GET không CSRF, local-only. Response là attachment XLSX no-store với
  filename deterministic đã sanitize từ Course code.
- **CONFIRMED:** XLSX có năm sheet: Course, Team Members, Sprints active/non-deleted,
  Tasks active/non-deleted và Peer Reviews raw. Không gọi Jira/GitHub, không dùng
  Lecturer Analytics hay `ContributionCalculationService` theo Team.
- **CONFIRMED:** không export email, Cognito subject, credential/provider ID, comment
  Peer Review, Assessment hay Contribution. File không là final grade, transcript,
  finalized score hoặc completed assessment.
