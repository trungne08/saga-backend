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
- Hệ quả: Bind dùng transaction + pessimistic row lock, chỉ ghi subject và `PENDING → ACTIVE`; không đổi email/code, không đụng TeamMember/Team/Course/RoleInTeam. Không có partial match thì giữ behavior tạo Student mới của flow cũ.
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
