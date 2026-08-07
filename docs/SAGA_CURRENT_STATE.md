# SAGA — Trạng thái hiện tại

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
