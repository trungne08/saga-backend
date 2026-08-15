## Jira task evidence from SAGA (DEC-093) — 2026-08-15

- Attach endpoint is **STUDENT team member only**. Files and/or an `http`/`https` link. DOCUMENT/RESEARCH count when ≥1 `TaskAttachment` or ≥1 `TaskWebLink`.
- Persist Jira file metadata (`V38`) and submitted links (`V39__add_task_web_link.sql`). Do not store links in `task_attachment`. No file download. GitHub attachments not ingested.
- OpenAPI **149**. Migration head **V39**.

## Absolute weighted slice × peer (DEC-092) — 2026-08-15

- Final contribution = `(Σ sprint slices × project P) / team adjust × 100`. Per-sprint display `% = (slice × P_s) / team adjust`. `slice = Σ SP_criterion × configured weight` (no share mix, no unused-weight redistribution). `P_s = 1` if that sprint has no peer. Tasks with no sprint do not score.
- OpenAPI **148**. No migration. Spec: `docs/CONTRIBUTION_CALCULATION_SPEC.md`.

## Sprint-first contribution % (DEC-091) — 2026-08-15

- **SUPERSEDED by DEC-092.**

## Labels-only Task scoring + Jira attachment metadata (DEC-090) — 2026-08-15

- Task criterion = reserved labels only. No keyword/title/`TaskType` fallback.
- DOCUMENT/RESEARCH: story points count only when the Task has ≥1 Jira `TaskAttachment` or ≥1 submitted `TaskWebLink`. Extra files/links do not add points. CODE/TEST ignore attachments.
- Persist Jira attachment metadata only (`V38__add_task_attachment.sql`). No file download. GitHub attachments not ingested.
- OpenAPI **148**. Migration head **V37 → V38**.

## Task-is-sole-numeric-authority + reserved Contribution markers constraints (DEC-089, foundation only) — 2026-08-15

- Foundation-only: Jira attachment ingestion and GitHub Issue/comment attachment extraction (the rest of the originally-requested milestone) are **not implemented**, blocked on unconfirmed provider runtime facts (Jira attachment endpoint, GitHub App permission grant, private-repo attachment CDN auth). Do not claim external-evidence ingestion exists.
- `ContributionCriterion` = CODE/TEST/DOCUMENT/RESEARCH, a dedicated enum — never overload `TaskType` (native MySQL ENUM, extending it needs a physical column migration) or `DocumentType`.
- Reserved markers `saga:code`/`saga:test`/`saga:document`/`saga:research` — exact case-sensitive match only, no substring/fuzzy/AI. >1 conflicting marker on a Task = AMBIGUOUS, excluded from all four criteria until fixed, never pick-first.
- Precedence: marker first, then unchanged legacy keyword classifier as fallback. Legacy classifier logic (title/description/labels/components keyword lists, CODE default for unlabeled Tasks, pick-first on keyword conflicts) is **untouched** in both `TeamContributionService` and `ContributionCalculationService`. The two services' legacy classifiers are pre-existing technical debt (structurally slightly different) — not unified.
- `TaskWeightConfig` audited: confirmed NOT a scoring authority, only a delete-dependency guard in `CourseService`.
- Task is the sole numeric Contribution authority when evidence is Task-linked — NUMERIC_TASK_FORMULA_CHANGED = NO (per-Task formula untouched) but COMMIT_NUMERIC_CONTRIBUTION_CHANGED = YES and OVERALL_CONTRIBUTION_SOURCE_SEMANTIC_CHANGED = YES: a commit linked to a DONE+assigned Task contributes exactly zero additional numeric score now (previously contributed its full weight) — the per-commit slice-scoring loop, which previously double-counted via the dead `commit.task` FK, was removed from both services. This is a deliberate double-count-prevention decision, not a no-op; do not describe it as "formula unchanged." Standalone (non-task-linked) commits and Documents are unaffected — no Task dependency existed for them before or after.
- TEST/RESEARCH have a real evidence source for the Task-marker path only (a DONE Task with `saga:test`/`saga:research`); provider-sourced TEST/RESEARCH evidence remains `TBD_PRODUCT_RULE`.
- `V37__fold_legacy_design_weight_into_document.sql` (new, does not touch V34/V35/V36 since their applied status could not be confirmed by runtime evidence): `document = document + design; design = 0`, for both `course` and `project_group_weight_config`. `code`/`test`/`research` are never written by this migration. Guard: only folds a row where `code+test+document+research+design` still equals exactly 100/1.0 (i.e. design is genuinely the missing piece of an untouched legacy total) — never a row already validly configured with the active four fields alone summing to 100/1.0, since that would corrupt an already-correct sum. Proven by an executed test against real H2, not string matching alone.
- OpenAPI operation count baseline **148** (3 legacy Course slice-weight request/list/decision routes removed). Migration head **V36 → V37**. Peer Review / Rubric / individual override / Student progress authorization / AI Agent / COURSE-TEAM mode (DEC-088) not changed.

## Contribution weight: Course-default + optional exclusive Team override constraints (DEC-088, supersedes DEC-087) — 2026-08-15

- Contribution weight authority has exactly two mutually exclusive modes per Course (`Course.contributionConfigMode`): **COURSE** (mọi Team resolve chung bộ trọng số Course) hoặc **TEAM** (**mọi** Team hiện tại của Course bắt buộc có `ProjectGroupWeightConfig` riêng hợp lệ; thiếu một Team = INCOMPLETE, không fallback Course). **Không** có mode hỗn hợp/từng-phần — bị cấm tường minh. `Class` không phải authority scope.
- Criteria universe = **CODE/TEST/DOCUMENT/RESEARCH** (DESIGN retired khỏi Contribution, vẫn là ProjectType catalog value độc lập). Evidence DESIGN cũ (Document `DESIGN` + task keyword) remap deterministic vào DOCUMENT — không invent taxonomy, không mapping DESIGN→RESEARCH.
- `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` route giữ nguyên; body/response 4 field `{codeWeight, testWeight, documentWeight, researchWeight}` scale 0..100, tổng 100 ± 0.01. PUT chỉ LECTURER exact instructor; ADMIN direct PUT giữ 403.
- `PUT /api/projects/{projectId}/group-weights` **hồi sinh** — 4 field `{codeWeight, testWeight, documentWeight, researchWeight}`, storage 0..1 scale (không đổi unit). Authorization hẹp nguyên bản: ADMIN hoặc đúng LECTURER phụ trách Course — **không** mở cho Student/Leader.
- `PUT /api/v1/courses/{courseId}/contribution-config-mode` (LECTURER instructor đúng Course) chuyển COURSE↔TEAM. Chuyển sang TEAM chỉ khi audit xác nhận toàn bộ Team hiện tại có override hợp lệ (atomic, không partial); thiếu → 409 `TEAM_MODE_CONFIGURATION_INCOMPLETE`. Chuyển về COURSE không xoá `ProjectGroupWeightConfig` cũ (giữ historical/inactive).
- `GET /api/v1/courses/{courseId}/contribution-team-weights` (ADMIN/LECTURER, mới) — team-menu read: mode hiện tại + effective weight + nguồn từng Team.
- `ContributionSliceWeightResolver.resolve(Team)` fail-closed trong TEAM mode: thiếu/sai Team override → `IntegrationException(TEAM_WEIGHT_CONFIG_INCOMPLETE)`, không bao giờ fallback Course. Team mới tạo sau khi TEAM mode active cũng phải có override riêng.
- TEST_SLICE_CLASSIFICATION / RESEARCH_SLICE_CLASSIFICATION = TBD_PRODUCT_RULE. Không có authoritative source nào định nghĩa testing/QA marker hay RESEARCH evidence ngoài phần remap từ DESIGN. Một keyword list TEST ban đầu bị audit là tự invent và đã bị gỡ bỏ. `testContributionScore`/`researchContributionScore` luôn `0.0`. Không gọi mọi BUG là TEST, không double-count, không AI/fuzzy classification, không tuyên bố 4-tiêu-chí scoring hoàn tất cho tới khi có product rule cụ thể.
- `V35__add_course_contribution_config_mode_and_weights.sql` + `V36__add_test_research_weight_to_project_group_weight_config.sql` chỉ `ADD COLUMN ... DEFAULT 0` — không UPDATE row nào đã tồn tại. `LEGACY_DESIGN_WEIGHT_MIGRATION = TBD` — không có formula an toàn để tự chuyển design→research; giá trị cũ giữ lại thuần lịch sử.
- OpenAPI operation count baseline **148 (DEC-087) → 151**. Migration head **V35 → V36**. Peer Review / individual override / Rubric / Student progress authorization / AI Agent không đổi.

## Course-wide 4-slice Contribution weight constraints (DEC-087, SUPERSEDED BY DEC-088 above) — 2026-08-15

- Contribution weight authority = **Course-only**. Mọi Team thuộc cùng Course resolve đúng cùng bộ trọng số của Course đó; không Team/Project-specific override. `Class` không phải authority scope; không thêm `classId` vào API.
- `GET/PUT /api/v1/courses/{courseId}/contribution-slice-weights` route giữ nguyên; body/response 4 field `{codeWeight, testWeight, documentWeight, designWeight}` scale 0..100, tổng 100 ± 0.01, mỗi field bắt buộc và `>= 0`. PUT chỉ LECTURER exact instructor; ADMIN direct PUT giữ 403 (unchanged). Legacy request/decision flow không mở rộng testWeight; approve ghi testWeight=0.
- `ProjectGroupWeightConfig` không còn là Contribution authority. `PUT /api/projects/{projectId}/group-weights` và Controller/Service/DTO liên quan bị xóa. Entity + repository giữ nguyên cho historical retention — không drop table, không hard-delete data.
- TEST_SLICE_CLASSIFICATION = TBD_PRODUCT_RULE. Không có `TaskType.TEST`/`DocumentType.TEST` hay authoritative source nào định nghĩa testing/QA marker. Một keyword list TEST ban đầu bị audit là tự invent và đã bị gỡ bỏ khỏi `classifyTaskSlice`; classifier chỉ còn CODE/DOCUMENT/DESIGN. `testWeight` vẫn backward-safe ở tầng Course API/schema nhưng luôn bị coi là slice không evidence khi tính Contribution (ngân sách phân bổ lại cho 3 slice còn lại). Không gọi mọi BUG là TEST, không double-count, không AI/fuzzy classification, không tuyên bố TEST scoring hoàn tất cho tới khi có product rule cụ thể (field/taxonomy nào là authority).
- `V35__add_course_test_contribution_weight.sql` chỉ `ADD COLUMN test_contribution_weight DOUBLE NOT NULL DEFAULT 0` — không UPDATE Course row đã tồn tại (giữ nguyên code/document/design cũ, cột mới backfill 0, tổng vẫn 100). `Course.applyDefaultContributionWeights()` (áp dụng cho Course MỚI qua `@PrePersist`) đổi default 1/3 → 1/4 mỗi phần; đây là default tầng application cho Course mới, không phải rewrite migration cho Course cũ.
- ProjectType (DESIGN/RESEARCH/TESTING/DOCUMENTATION) là catalog phân loại Project, **không** quyết định Course Contribution weights — hai concept độc lập.
- OpenAPI operation count baseline **149 → 148**. Migration head **V34 → V35**. Peer Review / individual override / Rubric / Student progress authorization / AI Agent không đổi.

## ProjectType fixed canonical catalog constraints (DEC-086) — 2026-08-15

- ProjectType là fixed migration-seeded canonical SAGA taxonomy: `DESIGN_ARCHITECTURE`/`RESEARCH`/`TESTER`/`DOCUMENT` (codes/names đã audit và chỉnh sửa in-place). Đây là taxonomy nội bộ SAGA, không ghi là chuẩn IEEE/ISO.
- Không còn ADMIN create/update/delete ProjectType. `POST /api/project-types` bị xóa; không endpoint thay thế. `GET /api/project-types` giữ nguyên (authenticated ADMIN/LECTURER/STUDENT, không CSRF, không Bearer) nhưng luôn trả đúng 4 canonical row.
- `V34__replace_project_type_with_canonical_catalog.sql`: null `project.project_type_id` trước, `DELETE FROM project_type`, insert đúng 4 row (codes/names ở trên) với UUID literal cố định trong migration (không phụ thuộc ID từ browser). Không drop table/FK/column. Existing Project đọc lại `projectType=null` — product-approved reset, legacy-compatible.
- Project create không đổi: `projectTypeId` bắt buộc; thiếu → `PROJECT_TYPE_REQUIRED`; unknown → `PROJECT_TYPE_NOT_FOUND`. FE GET catalog rồi gửi UUID đã chọn; không nhận code/name thay projectTypeId.
- `criteriaConfig` = NULL cho cả 4 canonical row; không biến thành rubric/peer review/contribution/grading authority. ProjectType **không** quyết định Contribution weight (xem DEC-088).
- LocalDemoDataSeeder resolve canonical `DESIGN_ARCHITECTURE` bằng `findByCode`; catalog thiếu → fail rõ, không tự tạo type thứ 5.
- OpenAPI operation count baseline **150 → 149**. Migration head **V33 → V34**. Contribution formula / Peer Review / Rubric / ProjectGroupWeightConfig / AI Agent / Lecturer & Admin Dashboard / Jira/GitHub / Student progress authorization không đổi.

## Student progress LEADER exact-Team constraints (DEC-085) — 2026-08-15

- STUDENT `/progress` authorize theo exact Team: actor LEADER Team X AND target `TeamMember` in Team X. Không dùng global Course uniqueness để chặn Leader read.
- Nếu actor lead nhiều Team hợp lệ: scope = union các Team đó. Target thuộc nhiều Team trong union → 409. MEMBER nhiều Team không LEADER (self) → 409.
- Không mở Leader xem Team khác trong Course. Không mở quyền theo toàn Class. Không thêm `classId` vào `/progress` — `courseId` đã đủ scope. LEADER Course A không mang sang Course B trừ independent exact-Team LEADER. Không mở activities / contribution-detail / early-warnings / dashboard. ADMIN/LECTURER uniqueness không đổi. Không migration / không xóa membership production.

## Lecturer teams-progress parallel Sprint constraints — 2026-08-15

- Không assume tối đa 1 Sprint `active` / Project trên `teams-progress`. Không pick primary. Không aggregate nhiều Sprint vào `currentSprint*`.
- `activeSprints[]` là authority khi `size != 1`. Filter: `deletedAt == null` và `state` equalsIgnoreCase `active`.
- Không đổi trends “Multiple Teams reference the same Project” 409, burndown `sprintId`, velocity, Jira lifecycle, auth.

## Avatar / progress / Course weight constraints — 2026-08-15

- OpenAPI generated operation count baseline = **150**. Migration head = **V33**. DEC-082 snapshot 149 / V32 không được rewrite.
- Browser FE auth: `JSESSIONID` + `credentials: include`; CSRF cho unsafe; GET không CSRF; **không Bearer**.
- Avatar chỉ từ OIDC `picture` lúc login; FE không POST avatar URL, không gọi Google image API, không gửi provider token. `avatarUrl` nullable.
- Cognito Google `picture` mapping: **CONFIRMED_CONSOLE_CONFIGURATION**. Runtime claim on login: **TBD_DEPLOYMENT_SMOKE**.
- Student progress STUDENT access chỉ `GET .../students/{studentId}/progress`. MEMBER self; LEADER exact Team (union nếu lead nhiều). MENTOR forbidden. DEC-085: target extra Course membership không 409 nếu exact Team deterministic. MEMBER self ambiguous / target in multiple led Teams = 409. Không mở activities / contribution-detail / early-warnings / dashboard.
- Course slice weights: PUT direct chỉ LECTURER owner; scale 0–100 sum 100 ± 0.01. Không nhầm ProjectGroupWeight 0–1. Precedence GroupWeight rồi Course fallback. Legacy request/decision giữ backward-compatible; new FE dùng PUT. ADMIN không có direct PUT.
- Contribution formula / Peer Review / Rubric / individual override / ProjectGroupWeightConfig **không đổi**.
- Full suite **1019 / 23 fail / 8 error** — không ghi FULL_SUITE=PASS.

## Merged main constraints — Project / Lecturer / Admin / AI / OpenAPI — 2026-08-15

- **HISTORICAL SNAPSHOT (DEC-082):** OpenAPI **149** / V32 / 994 tests. **Current baseline** = section Avatar/progress/Course weight ở trên (150 / V33 / 1019).
- OpenAPI generated operation count baseline = **149** (local contract). Deployed Swagger currency = **TBD**; production springdoc/Swagger mặc định off trừ khi bật explicit (`SWAGGER_ENABLED` / `SPRINGDOC_*` — không ghi secret/env value).
- Migration head = **V32**. Không seed canonical ProjectType production.
- Browser FE auth: `JSESSIONID` + `credentials: include`; CSRF cho unsafe mutations; GET không CSRF trừ khi exact source nói khác; **không Bearer**.
- Project create bắt buộc `projectTypeId`. Group weights lưu exact Project+Team; Contribution fallback Course; **không đổi** Contribution formula / Peer Review / Rubric.
- Admin unsupported anomaly counts phải JSON `null`, không `0`. Graph-processing không fabricate history.
- AI public chỉ `/api/v1/ai/**`. `/internal/ai/**` không phải FE contract. AI không là business authority.
- Full suite sau reconciliation: **994 / 23 fail / 0 error** — không ghi FULL_SUITE=PASS.

## J1K.1 TaskType.REQUEST physical schema constraints — 2026-08-13

- Java `TaskType` and the physical MySQL `task.type` enum are one persistence contract. V29 must contain exactly `BUG, EPIC, FEATURE, REQUEST, STORY, SUBTASK, TASK`; adding a Java value without matching Flyway support must fail the migration contract test.
- Preserve runtime metadata `IS_NULLABLE=YES` and `COLUMN_DEFAULT=NULL`. Do not rewrite/delete Task rows, change indexes/FKs, or modify unrelated columns/tables.
- MySQL 1265 after successful Jira search at `UPSERT_ISSUES` is classified as `TASK_TYPE_DATABASE_ENUM_MISSING_REQUEST`, not webhook/search/FE failure.
- Flyway is deployment authority. Do not manually ALTER production outside the normal migration path. Post-deploy Request/Story/existing-type and manual reconciliation smoke are required before runtime confirmation.
- This section supersedes only the J1K “no migration/schema unchanged” statement. Provider resolution, sparse update, canonical confirmation, authorization, session/CSRF and `CourseService` remain unchanged.

## Ràng buộc J1K Jira external Web Task sync — 2026-08-13

- Jira dynamic webhook phải giữ exact registered events: `jira:issue_created`, `jira:issue_updated`, `jira:issue_deleted`, `comment_created`, `comment_updated`, `comment_deleted`, `sprint_created`, `sprint_updated`, `sprint_deleted`, `sprint_started`, `sprint_closed`. Provider request chỉ được xử lý sau JWT/board-secret authentication, durable encrypted receipt và `(provider, delivery_id)` dedup.
- Issue create/update webhook không được dùng raw payload làm canonical Task; nó trigger shared reconciliation. Scheduler/manual sync phải reuse cùng canonical provider search/upsert path.
- Generic Jira search phải discovery estimation field theo external board ID, request đúng field cùng Sprint discovery và không hardcode `customfield_*`. Whole non-negative string/number normalize exact về integer. Explicit returned null có quyền clear local; omitted property không được clear. Fractional/negative/blank/non-numeric/object/array/overflow fail `JIRA_RESPONSE_INVALID`.
- Issue delete webhook chỉ dùng minimal stable issue ID, hoặc issue key khi ID thiếu, và phải scope lookup bằng Project của authenticated JiraBoard. Success đặt `Task.deletedAt` UTC; already tombstoned/unknown no-op, không hard-delete/cascade/cross-project. Generic upsert không clear tombstone nên stale canonical snapshot không resurrect Task.
- Diagnostics không chứa raw webhook payload, issue title/key, credential, token hoặc secret. Health/sync history tiếp tục local-only; health trả latest safe receipt summary và latest persisted Jira webhook-maintenance result. Maintenance attempt phải persist safe `SyncJobLog OTHER`/`WEBHOOK_MAINTENANCE` để operator phân biệt success/failure; không error message/raw exception/provider-live call. Không migration, Bearer, session/CSRF, OAuth scope hoặc `CourseService` change.

## Ràng buộc J1J Jira Task provider-ID ownership — 2026-08-10

- Normal Update Priority nhận duy nhất business `priority`; `priorityId` chỉ là backward-compatible advanced Jira provider override. Hai field cùng có mặt là `400 JIRA_PRIORITY_INVALID` trước write-operation claim và provider I/O.
- Update phải lấy `GET /rest/api/3/issue/{issueIdOrKey}/editmeta` cho từng issue/request. Nếu cần mutate: field `priority` phải editable; business resolution phải dedup provider ID, chọn unique exact canonical name trước semantic fallback, zero/multiple fail closed; explicit ID phải thuộc `allowedValues`. Không sort/pick-first, cache cross-project hoặc fallback stale ID.
- Create và Update phải gọi chung thuật toán priority resolution; chúng vẫn dùng authority metadata riêng (`createmeta` và `editmeta`). Provider payload chỉ được tạo sau resolution thành công. Diagnostic cấm request value, ID, token, Authorization, credential, Idempotency-Key, cookie/CSRF và raw response.
- Fingerprint Update chứa raw `priority` và `priorityId` riêng; không chứa resolved provider ID/metadata. Canonical reconciliation, remote-success recovery, sparse update khác, session/CSRF/authorization và schema giữ nguyên.
- Historical J1J boundary: `componentIds` remains a Jira provider-ID gap and Transition IDs must come from GET transitions for the exact issue. The former Issue Type `CONFIRMED_NOT_IMPLEMENTED` statement is superseded by J1K below.

## Ràng buộc J1I canonical Story Point parser — 2026-08-10

- PUT estimation 2xx xác nhận mutation remote và không cần parse response body để finalize; canonical Jira issue GET với estimation field đã discovery là nguồn xác nhận cuối cùng.
- Parser canonical chỉ normalise string/number decimal whole không âm bằng `BigDecimal` + exact integer conversion. Chấp nhận `0`, `0.0`, `5`, `5.0`; từ chối fractional, negative, blank, non-numeric, missing/null, object/array và overflow với `JIRA_RESPONSE_INVALID`.
- `JIRA_RESPONSE_INVALID` sau remote success không được chuyển operation sang `FAILED`; giữ `REMOTE_SUCCEEDED`, không provider retry mù, rồi same-key recovery canonical. Public request integer, discovery field, state-machine J1H, schema/migration, isolation và auth không đổi.

## Ràng buộc J1H TASK_ESTIMATION finalization — 2026-08-10

- `TASK_ESTIMATION` chỉ `COMPLETED` sau Jira Agile estimation mutation đã `REMOTE_SUCCEEDED`, canonical GET có field estimation discovery theo board, upsert và fresh `JiraCanonicalTaskReadService` xác nhận `storyPoint` bằng request integer không âm.
- Ngay sau `markRemoteSucceeded` transaction riêng phải đồng bộ remote id/key/status vào object orchestration trước reconcile. Failure canonical hay mismatch phải giữ `REMOTE_SUCCEEDED`, không `FAILED`, không success giả và không replay provider mutation với cùng key.
- `request_fingerprint` không lưu target intent đọc được; recovery nền không được tự complete `TASK_ESTIMATION`. Không đổi entity/schema/migration, global MySQL isolation, scope/auth/session/CSRF hoặc thêm hardcode Jira/customfield/Bearer.

## Ràng buộc J1G Jira Task update metadata — 2026-08-10

- Historical J1G baseline: `PUT /tasks/{taskId}` did not accept type. J1K adds business `type` only; assignee/Sprint/estimation/status retain their separate routes and recovery contracts.
- `GET /rest/api/3/issue/{issueIdOrKey}/editmeta` là authority writable. Field cần mutate không có metadata trả `400 JIRA_EDIT_FIELD_NOT_ALLOWED`, không provider update/retry mù.
- Diagnostic chỉ có operation, stage, field key/business field, upstream status, category, write status; cấm request value, token, Authorization, Idempotency-Key, cookie/CSRF, Cognito sub, raw provider response.
- Thiếu `Idempotency-Key` là binding `400 INVALID_REQUEST`; header vẫn required, không đổi session/CSRF/CORS/state machine.

# SAGA Backend — Yêu cầu, Dependency, Phân quyền và Ràng buộc

## A13 Admin advanced closure constraints — 2026-08-10

- Per-user audit chỉ có thể được mở khi quyết định rõ forward-only hay complete history, index, retention và privacy. Không parse old/new payload, Cognito subject hay backfill Mongo để suy đoán actor local.
- Role mutation cần transition matrix, cross-profile migration, Cognito group ownership và session refresh policy. Password reset cần authority cho native/federated user, AWS Cognito Admin dependency/IAM/email contract. Không được tự thêm chúng.
- Course membership vẫn là `Student -> TeamMember -> Team -> Course`; add cần Team selection, remove cần retention cho Project/Task/PeerReview/Contribution. Notification cần versioned schema, consumer, audience/read/retention/idempotency. Generic settings không được gom domain config khác scope.
- ADMIN access là per-endpoint explicit: shared endpoint đã support ADMIN thì reuse; endpoint không support ADMIN không được tự nới quyền. Không tạo `/api/admin/courses/**` duplicate.

## A12 Admin closure constraints — 2026-08-09

- `/api/admin/**` cần `ROLE_ADMIN`; unsafe request chịu CSRF global, chỉ webhook provider được
  exempt. Master-data mutation ngoài namespace này cũng có `hasRole('ADMIN')`.
- Admin read chỉ dùng local MySQL/Mongo snapshots. Không thêm provider call, Mongo backfill/index,
  MySQL migration, generic setting hay support capability khi không có governance contract.
- Browser E2E production không được suy diễn từ integration test; FE cần smoke session + CSRF riêng.

## Account lifecycle M3B constraints — 2026-08-09

V21 thêm `lecturer.account_status` non-null default ACTIVE; Admin không có cột này. `PATCH /api/admin/users/{id}/status` yêu cầu ADMIN session + CSRF, request chỉ có `status` ACTIVE/INACTIVE/SUSPENDED. Target là local profile ID Student/Lecturer; Admin target, PENDING và unknown ID fail controlled. Browser-session business API check current local DB status mỗi request; auth me/csrf/logout exempt và `/me` trả current status. Không provider lookup, role mutation, cascade Course/membership/Project/history.

## AccountStatus M3A audit constraints — 2026-08-09

`AccountStatus` chỉ thuộc Student. Local profile ID từ Admin user union có thể chỉ Admin/Lecturer, nhưng các target đó phải fail controlled nếu endpoint được phê duyệt. Hiện chưa có policy cho Admin set status hay enforce status API, nên không có PATCH/mutation. First-login giữ contract PENDING -> ACTIVE, ACTIVE giữ nguyên, INACTIVE/SUSPENDED không bind/activate. Session status là snapshot; không được suy diễn rằng DB update đã khóa session cũ.

## Course M2B retention constraints — 2026-08-09

`PUT` và `DELETE /api/v1/courses/{id}` yêu cầu ADMIN + browser session + CSRF. PUT reuse `CourseRequest`; không có PATCH. Create/update chỉ chấp nhận Subject, Class, Semester active. DELETE là soft-delete (`deleted_at` V20), chỉ khi không có Team, Project, StudentCourseInvitation hoặc TaskWeightConfig; dependency trả 409. Active detail/list/filter loại tombstone và code tombstone không reuse. Không cascade/hard-delete/detach; không thay đổi Team membership, invitation delivery hay Contribution.

## Semester retention constraints — 2026-08-09

`PUT` và `DELETE /api/v1/semesters/{id}` yêu cầu ADMIN + session + CSRF. PUT reuse `SemesterRequest`; không có PATCH. DELETE là soft-delete (`deleted_at` nullable từ V19), chỉ khi không có Course reference; dependency guard trả 409. GET detail/list/search chỉ thấy active Semester. Tombstoned code không reuse; Course history/business logic không bị sửa hoặc cascade.

## Admin read-only constraints — 2026-08-09

| Route | Access | Store | Safety |
| --- | --- | --- | --- |
| `GET /api/admin/users` | ADMIN | MySQL | local safe fields, DB-paged union |
| `GET /api/admin/audit-logs` | ADMIN | Mongo | no actor/IP/raw old-new payload |
| `GET /api/admin/system-stats` | ADMIN | MySQL | local counts and generatedAt only |
| `GET /api/admin/integrations/health` | ADMIN | MySQL | local integration snapshot; no provider call/secret |
| `GET /api/admin/teams` | ADMIN | MySQL | Team/Course/nullable Project summaries |
| `GET /api/admin/projects` | ADMIN | MySQL | Project/Course/Jira local/GitHub aggregate |

Các GET dùng browser session, không cần CSRF hay Bearer; không route nào gọi provider. `AccountStatus` chỉ có trên Student, nên filter này không suy diễn status Admin/Lecturer.

> **Trạng thái audit:** CONFIRMED = được code hiện tại chứng minh; PARTIAL = mới có một phần code/mô hình; TBD = repository không đủ bằng chứng; RECOMMENDED = đề xuất, không phải hành vi hiện tại. Audit dựa trên branch `main`, HEAD thực tế `0bc30be` ngày 2026-08-04. `200d866`, `a43f05d`, `07ffa38`, `90b1852` và `52a8c71` được giữ là checkpoint lịch sử. Không dùng tài liệu cũ làm bằng chứng chính và không chép giá trị bí mật.

## 1. Mục đích tài liệu

Tài liệu này dành cho Backend, Frontend và QA để truy vết yêu cầu thực tế về code, dependency, API, phân quyền và vận hành. Phạm vi là toàn bộ source hiện có: Spring Boot, Lambda, cấu hình, migration, test và Railway. Mọi kết luận chỉ xuất phát từ executable code/config/test; khi code không chứng minh được, tài liệu ghi TBD thay vì suy đoán.

## Contribution calculation constraints (2026-08-04)

- **CONFIRMED:** source-of-truth is mapped `CommitData` by Project/Student,
  SAGA `Document` split by `DocumentType.DESIGN`, DONE Jira-synced `Task` by
  Project/Sprint/assignee, and received `PeerReview` records. Aggregates are
  repository/database queries; unmapped external identities are not attributed.
- **CONFIRMED:** final arithmetic uses `BigDecimal`; raw weight is 40% code and
  60% adjusted task score. Document/design percentages are returned in the
  internal breakdown but do not alter that weight.
- **TBD:** default-versus-Subject `PeerReviewConfig` precedence; Contribution
  override storage/authorization; invalid override values, all-override remainder,
  positive remaining budget with zero base, and rounding residual policy.
- **RECOMMENDED:** configure/discover Jira story-point and sprint fields instead
  of relying on the current tenant-specific field ids. Do not add a normalized
  Jira Component or Label model without a query requirement.

## 2. Tổng quan hệ thống

SAGA là backend Spring Boot quản lý dữ liệu học phần/lớp/học kỳ/course, định danh người dùng Cognito, team/project và tích hợp Jira Cloud/GitHub App. MySQL/JPA lưu dữ liệu nghiệp vụ; MongoDB lưu audit log.

| Trạng thái | Module thực tế |
| --- | --- |
| **ĐÃ TRIỂN KHAI** | Cognito OIDC session login/profile provisioning; Subject/Class/Semester/Course Create+Read; course student-import authorization; personal identity mapping; review mapping; tạo team project; Jira/GitHub project integration; webhook có xác thực; backfill, reconciliation, sync job và audit log. |
| **PARTIAL** | Entity cho assessment, rubric, CAM, AI log, document, meeting, notification, peer review, sprint/task đã có nhưng không tìm thấy controller/service HTTP hoàn chỉnh cho các module này; riêng `Notification` còn không có repository. Master data chưa có Update/Delete. |
| **CHƯA TRIỂN KHAI / TBD** | Không có code Lambda gán role Cognito. README Lambda chỉ nhắc một Pre Token Generation Lambda khác; không được coi là đã triển khai trong repository. |

Bằng chứng: `pom.xml:L10-L177`; `src/main/java/com/saga/be/controller/*`; `src/main/java/com/saga/be/integration/*`; `infra/lambda/cognito-account-linking/README.md:L1-L5`.

## 3. Actor và Role

Application role khác team role. `STUDENT` có `RoleInTeam.LEADER` vẫn là STUDENT ở application level; LEADER chỉ mở rộng quyền ở đúng team/project khi authorization service cho phép.

| Role | Ý nghĩa và quyền code chứng minh | Phạm vi/ràng buộc | TBD |
| --- | --- | --- | --- |
| `ADMIN` | Tạo Subject/Class/Semester/Course; import sinh viên mọi Course; quản lý integration mọi team/project; review mọi identity mapping. | Override project/team được ghi audit. | Không có controller `/api/admin/**` hiện hữu. |
| `LECTURER` | Import sinh viên khi là instructor Course; quản lý project integration nếu là instructor của Course chứa Team; review mapping Student thuộc team/course do mình dạy. | So sánh `localProfileId` với `course.instructor.id`. | Không có quyền tạo master data. |
| `STUDENT` | Quản lý mapping Jira/GitHub của chính mình. | Bắt buộc role STUDENT và local profile id; không được import sinh viên. | Không có API profile update. |
| `LEADER` | Cho phép Student quản lý team project/integration. | Phải có TeamMember đúng team, đúng student và `LEADER`. | Không phải application role. |
| `MEMBER`, `MENTOR` | Giá trị enum tồn tại. | Không được cấp quyền manager trong code. | Các quyền khác không được chứng minh. |

Bằng chứng: `ApplicationRole.java:L3-L7`; `RoleInTeam.java:L3-L7`; `ProjectIntegrationAuthorizationService.java:requireTeamManager`; `IdentityMappingReviewService.java:requireReviewer`.

## 4. Kiến trúc xác thực

1. `GET /api/auth/login` là PUBLIC, redirect browser đến `/oauth2/authorization/cognito`.
2. Spring OAuth2 Client thực hiện authorization-code OIDC với Cognito Hosted UI. Backend chỉ nhận OIDC user từ Cognito và không có form/API login mật khẩu native. Google chỉ được code chứng minh qua Lambda liên kết `Google_<subject>` trước khi Cognito tạo external profile; chi tiết cấu hình IdP/native login deployed là TBD.
3. Callback template là `/login/oauth2/code/cognito`, do Spring Security `OAuth2LoginAuthenticationFilter` xử lý; không có controller map callback này.
4. Success handler đòi `sub`, email đã verified, `name` và group Cognito hợp lệ; đồng bộ Admin/Lecturer/Student local profile, thay authentication provider bằng `SagaPrincipal` token-free, lưu vào `HttpSession`, rồi redirect `AUTH_SUCCESS_REDIRECT_URI`.
5. `GET /api/auth/me` trả profile session và materialize CSRF token. `POST /api/auth/logout` do Spring Security xử lý: invalidate session, xoá `JSESSIONID`/`XSRF-TOKEN`, redirect Cognito logout.
6. Lambda Pre Sign-up account-linking chỉ liên kết `Google_<subject>` với một native Cognito user hợp lệ theo email đã verified. Lambda role-assignment: **TBD, source không có**.

| Câu hỏi Frontend | Kết luận |
| --- | --- |
| FE có lấy Cognito token? | Không cần cho API application theo code hiện tại; session authentication được token-free. |
| FE có gửi `Authorization: Bearer`? | Không phải cơ chế xác thực application được cấu hình. Header được CORS cho phép nhưng session là cơ chế thực tế. |
| FE có dùng `credentials: "include"`? | Có, với các request cross-origin sử dụng session cookie. |
| `/api/auth/login` dùng gì? | Browser navigation/redirect, không phải fetch/Axios để hoàn tất đăng nhập. |
| Swagger dùng gì? | `JSESSIONID` cookie, không phải Bearer scheme. |

Bằng chứng: `AuthController.java:L17-L39`; `SecurityConfig.java:securityFilterChain`; `CognitoAuthenticationSuccessHandler.java:onAuthenticationSuccess`; `OidcIdentityService.java:extract`; `CognitoLogoutSuccessHandler.java:onLogoutSuccess`; `OpenApiConfig.java:customOpenAPI`; `infra/lambda/cognito-account-linking/index.mjs:createHandler`.

## 5. Mô hình phân quyền

| Tầng | Hành vi hiện tại |
| --- | --- |
| URL-level | PUBLIC: `/oauth2/**`, `/login/**`, `/error`, static GET, `/api/auth/login`, health GET, provider webhook, Springdoc khi flag bật. `/api/admin/**` yêu cầu ADMIN. Mọi route còn lại authenticated. |
| Method-level | `@EnableMethodSecurity` bật. Create master-data dùng `hasRole('ADMIN')`; import dùng `hasAnyRole('ADMIN','LECTURER')`; không có `@Secured`. |
| Service-level | Personal mapping bắt buộc Student; identity review là ADMIN hoặc Lecturer scoped; project manager là ADMIN, Lecturer owner hoặc Student LEADER; import dùng Course scope riêng. |
| Ownership/membership | Mapping dùng local profile id; Lecturer dùng Course instructor id; Student manager dùng truy vấn TeamMember exact. |
| Account status | `AccountStatus` được lưu/trả/audit nhưng không có check ACTIVE/INACTIVE/SUSPENDED/PENDING trước khi cấp API permission. |
| CSRF | Cookie CSRF áp dụng mutation; chỉ `POST /api/webhooks/github` và `POST /api/webhooks/jira` được miễn. |

Phân biệt lỗi: route protected chưa đăng nhập → 401; URL/method role denial → 403; có role nhưng không owner/member/leader → `IntegrationException` 403; resource không có thường 404 hoặc integration validation 400; conflict 409; OIDC identity invalid 422; provider/profile failure 502; CSRF thiếu/sai bị Spring Security từ chối trước controller (403).

Bằng chứng: `SecurityConfig.java:L74-L153`; `GlobalExceptionHandler.java`; `IdentityMappingService.java:requireStudent`; `ProjectIntegrationAuthorizationService.java:requireTeamManager`.

## 6. Ma trận phân quyền API đầy đủ

Quy ước: `AUTHENTICATED` = chỉ cần session; `SCOPED` = phụ thuộc ownership/membership; `PROVIDER` = external provider có xác thực; `—` = không áp dụng. Mutation trừ webhook yêu cầu CSRF theo `SecurityConfig.java:L74-L77`.

| HTTP Method | Path | Controller Method | Public/Auth Required | ADMIN | LECTURER | STUDENT | Ownership/Membership Rule | Team Role Rule | CSRF Required | Request DTO | Response DTO | Status Codes | Bằng chứng |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| GET | `/privacy` | `PrivacyPolicyController.getPrivacyPolicy` | PUBLIC | YES | YES | YES | exact public URL matcher; independent of integration flags | — | NO | — | HTML UTF-8 | 200; invalid/missing contact config controlled 503 | `PrivacyPolicyController`; `SecurityConfig`; `PrivacyPolicyIntegrationTest` |
| GET | `/api/auth/login` | `AuthController.login` | PUBLIC | YES | YES | YES | — | — | NO | — | redirect | 302 | `AuthController.java:L20-L25` |
| GET | `/api/auth/me` | `AuthController.me` | AUTHENTICATED | YES | YES | YES | principal session | — | NO | — | `AuthMeResponse` | 200,401 | `AuthController.java:L28-L39` |
| GET | `/api/v1/subjects/{id}` | `getSubjectById` | AUTHENTICATED | YES | YES | YES | — | — | NO | — | `Subject` | 200,404 | `SubjectController.java:L28-L30` |
| POST | `/api/v1/subjects` | `createSubject` | AUTHENTICATED | YES | NO | NO | `@PreAuthorize(ADMIN)` | — | YES | `SubjectRequest` | `Subject` | 201,400,403,409 | `SubjectController.java:L33-L36` |
| GET | `/api/v1/subjects` | `getSubjects` | AUTHENTICATED | YES | YES | YES | — | — | NO | query | `Page<Subject>` | 200 | `SubjectController.java:L39-L47` |
| GET | `/api/v1/classes/{id}` | `getClassById` | AUTHENTICATED | YES | YES | YES | — | — | NO | — | `Class` | 200,404 | `ClassController.java:L29-L31` |
| POST | `/api/v1/classes` | `createClass` | AUTHENTICATED | YES | NO | NO | `@PreAuthorize(ADMIN)` | — | YES | `ClassRequest` | `Class` | 201,400,403,409 | `ClassController.java:L34-L37` |
| GET | `/api/v1/classes` | `getClasses` | AUTHENTICATED | YES | YES | YES | — | — | NO | query | `Page<Class>` | 200 | `ClassController.java:L40-L48` |
| GET | `/api/v1/semesters/{id}` | `getSemesterById` | AUTHENTICATED | YES | YES | YES | — | — | NO | — | `Semester` | 200,404 | `SemesterController.java:L29-L31` |
| POST | `/api/v1/semesters` | `createSemester` | AUTHENTICATED | YES | NO | NO | `@PreAuthorize(ADMIN)` | — | YES | `SemesterRequest` | `Semester` | 201,400,403,409 | `SemesterController.java:L34-L37` |
| GET | `/api/v1/semesters` | `getSemesters` | AUTHENTICATED | YES | YES | YES | — | — | NO | query | `Page<Semester>` | 200 | `SemesterController.java:L40-L48` |
| GET | `/api/v1/courses/{id}` | `getCourseById` | AUTHENTICATED | YES | YES | YES | — | — | NO | — | `Course` | 200,404 | `CourseController.java:L28-L30` |
| POST | `/api/v1/courses` | `createCourse` | AUTHENTICATED | YES | NO | NO | `@PreAuthorize(ADMIN)` | — | YES | `CourseRequest` | `Course` | 201,400,403,404,409 | `CourseController.java:L33-L36` |
| GET | `/api/v1/courses` | `getCourses` | AUTHENTICATED | YES | YES | YES | filter không phải permission scope | — | NO | query | `Page<Course>` | 200 | `CourseController.java:L39-L51` |
| GET | `/api/v1/courses/instructors` | `getLecturersForCourseAssignment` | AUTHENTICATED | YES | NO | NO | ADMIN-only | — | NO | `keyword` fullName/email; sortBy fullName/email; sortDirection asc/desc; page/size | `Page<LecturerOptionResponse>` | 200,400,401,403 | `CourseController`; `CourseService#getLecturersForCourseAssignment` |
| GET | `/api/v1/courses/{courseId}/students` | `getCourseStudents` | AUTHENTICATED | YES | SCOPED | NO | ADMIN mọi Course; LECTURER là instructor; Course thiếu 404 | — | NO | keyword; hasTeam all/with/without; sortBy studentCode/fullName/email/teamName/projectName; sortDirection asc/desc; page/size | `CourseStudentRosterResponse` | 200,400,401,403,404 | `CourseController`; `CourseService#getCourseRoster` |
| GET | `/api/me/courses/{courseId}/team/members` | `MyCourseTeamController.getMyCourseTeamMembers` | AUTHENTICATED | NO | NO | YES | Student self-scoped: `SagaPrincipal.localProfileId` + Student/Course memberships; no membership/Course thiếu 404, legacy nhiều Team 409 | LEADER và MEMBER đều được; không thay đổi project-manager rule | NO | `page`, `size` (0/20, max 100) | `MyCourseTeamMembersResponse` gồm resolved team/project nullable và `Page<TeamMemberResponse>` | 200,400,401,403,404,409 | `MyCourseTeamController`; `TeamRosterService#getCurrentStudentTeamMembers` |
| POST | `/api/v1/courses/{courseId}/import-students` | `importStudents` | AUTHENTICATED | YES | SCOPED | NO | ADMIN mọi Course; LECTURER phải có `localProfileId == course.instructor.id`; Course thiếu 404; cùng Student vào Team khác của cùng Course là 409 | — | YES | multipart `file` | `String` | 200,401,403,404,409,500 | `CourseController#importStudents`; `CourseImportAuthorizationService#requireImportAccess`; `ExcelImportService#importStudentsToCourse` |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/members` | `TeamRosterController.getMembers` | AUTHENTICATED | YES | SCOPED | SCOPED | Team phải thuộc Course URL; Lecturer là instructor; Student có TeamMember đúng Team (LEADER/MEMBER đều được) | không dùng LEADER-only project rule | NO | `page`, `size` | `Page<TeamMemberResponse>` không email/cognitoSub/version | 200,401,403,404 | `TeamRosterController`; `TeamRosterService` |
| GET | `/api/integrations/identity-mappings` | `mappings` | AUTHENTICATED | YES | SCOPED | NO | Lecturer dạy Course có Student target | — | NO | `studentId` | `IdentityConnectionResponse[]` | 200,403 | `IdentityMappingReviewController.java:L32-L38`; `IdentityMappingReviewService#requireReviewer` |
| PATCH | `/api/integrations/identity-mappings/{mappingId}` | `review` | AUTHENTICATED | YES | SCOPED | NO | review/correction đều recheck reviewer | — | YES | `IdentityMappingReviewRequest` | `IdentityConnectionResponse` | 200,400,403,409 | `IdentityMappingReviewController.java:L40-L51`; `IdentityMappingReviewService#review` |
| GET | `/api/me/integrations` | `connections` | AUTHENTICATED | NO | NO | YES | chỉ mapping của Student hiện tại | — | NO | — | `PersonalIntegrationsResponse` | 200,403 | `PersonalIntegrationController.java:L36-L40`; `IdentityMappingService#getOwnConnections` |
| GET | `/api/me/integrations/jira/connect` | `connectJira` | AUTHENTICATED | NO | NO | YES | personal flow sẽ require Student | — | NO | — | redirect | 302,403,503 | `PersonalIntegrationController.java:L43-L50` |
| DELETE | `/api/me/integrations/jira` | `disconnectJira` | AUTHENTICATED | NO | NO | YES | own Student mapping | — | YES | — | — | 204,403 | `PersonalIntegrationController.java:L52-L62`; `IdentityMappingService#disconnectOwn` |
| GET | `/api/me/integrations/github/connect` | `connectGitHub` | AUTHENTICATED | NO | NO | YES | personal flow sẽ require Student | — | NO | — | redirect | 302,403,503 | `PersonalIntegrationController.java:L65-L72` |
| GET | `/api/me/integrations/github/callback` | `githubCallback` | AUTHENTICATED | NO | NO | YES | OAuth state bound session/user + own mapping | — | NO | query | `IdentityConnectionResponse` | 200,400,403,409 | `PersonalIntegrationController.java:L74-L90`; `OAuthStateService#consume` |
| DELETE | `/api/me/integrations/github` | `disconnectGitHub` | AUTHENTICATED | NO | NO | YES | own Student mapping | — | YES | — | — | 204,403 | `PersonalIntegrationController.java:L93-L103` |
| GET | `/api/integrations/jira/callback` | `callback` | AUTHENTICATED | SCOPED | SCOPED | SCOPED | state chọn personal/project; project recheck manager | project flow: LEADER | NO | query | polymorphic | 200,400,403,409 | `JiraIntegrationCallbackController.java:L27-L44`; `JiraOAuthCallbackService#complete` |
| POST | `/api/teams/{teamId}/projects` | `create` | AUTHENTICATED | YES | SCOPED | SCOPED | exact team, một project/team | Student phải LEADER | YES | `CreateTeamProjectRequest` | `ProjectResponse` | 201,400,403,409 | `TeamProjectController.java:L29-L42`; `TeamProjectService#create` |
| GET | `/api/projects/{projectId}/integrations` | `integrations` | AUTHENTICATED | YES | SCOPED | SCOPED | Project Manager | Student phải LEADER | NO | — | `ProjectIntegrationsResponse` | 200,400,403 | `ProjectIntegrationController.java:L45-L50`; `ProjectIntegrationService:L151-L155` |
| GET | `/api/projects/{projectId}/jira/connect` | `jiraConnect` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager + state session | Student phải LEADER | NO | — | redirect | 302,403,503 | `ProjectIntegrationController.java:L53-L64`; `ProjectIntegrationService:L177-L182` |
| POST | `/api/projects/{projectId}/jira/link` | `jiraLink` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager + OAuth site state | Student phải LEADER | YES | `JiraProjectLinkRequest` | `ProjectIntegrationsResponse` | 200,400,403,409 | `ProjectIntegrationController.java:L67-L81`; `ProjectIntegrationService:L236-L243` |
| DELETE | `/api/projects/{projectId}/jira` | `jiraDisconnect` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager | Student phải LEADER | YES | — | — | 204,403 | `ProjectIntegrationController.java:L84-L94`; `ProjectIntegrationService:L357-L362` |
| GET | `/api/projects/{projectId}/github/install` | `githubInstall` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager + state session | Student phải LEADER | NO | — | redirect | 302,403,503 | `ProjectIntegrationController.java:L98-L109`; `ProjectIntegrationService:L399-L404` |
| GET | `/api/projects/{projectId}/github/setup` | `githubSetup` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager + state session | Student phải LEADER | NO | query | redirect | 302,400,403 | `ProjectIntegrationController.java:L112-L132`; `ProjectIntegrationService:L415-L425` |
| GET | `/api/projects/{projectId}/github/callback` | `githubCallback` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager + state session | Student phải LEADER | NO | query | `GitHubInstallationResponse` | 200,400,403 | `ProjectIntegrationController.java:L135-L151`; `ProjectIntegrationService:L467-L477` |
| POST | `/api/projects/{projectId}/github/repositories` | `githubRepositories` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager; Student còn phải owner installation | Student phải LEADER | YES | `GitHubRepositoriesLinkRequest` | `ProjectIntegrationsResponse` | 200,400,403,409 | `ProjectIntegrationController.java:L154-L166`; `ProjectIntegrationService:L610-L625` |
| DELETE | `/api/projects/{projectId}/github/repositories/{repositoryId}` | `githubRepositoryDisconnect` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager | Student phải LEADER | YES | — | — | 204,403,404 | `ProjectIntegrationController.java:L169-L180`; `ProjectIntegrationService:L708-L714` |
| GET | `/api/projects/{projectId}/sync-status` | `syncStatus` | AUTHENTICATED | YES | SCOPED | SCOPED | Manager | Student phải LEADER | NO | — | `SyncStatusResponse` | 200,403 | `ProjectIntegrationController.java:L185-L190`; `ProjectIntegrationService:L733-L737` |
| GET | `/api/integrations/github/setup` | `githubSetup` | AUTHENTICATED | SCOPED | SCOPED | SCOPED | state resolve project rồi recheck manager | theo team target | NO | query | redirect | 302,400,403 | `ProjectIntegrationCallbackController.java:L30-L49`; `ProjectIntegrationService:L441-L456` |
| GET | `/api/integrations/github/project/callback` | `githubCallback` | AUTHENTICATED | SCOPED | SCOPED | SCOPED | state resolve project rồi recheck manager | theo team target | NO | query | `GitHubInstallationResponse` | 200,400,403 | `ProjectIntegrationCallbackController.java:L52-L68`; `ProjectIntegrationService:L493-L508` |
| POST | `/api/webhooks/github` | `github` | PUBLIC/PROVIDER | PROVIDER | PROVIDER | PROVIDER | HMAC signature + delivery/event | — | NO | bytes/headers | `WebhookAcceptedResponse` | 200,202,400,403,503 | `WebhookController.java:L24-L51`; `GitHubWebhookSignatureVerifier#verify` |
| POST | `/api/webhooks/jira` | `jira` | PUBLIC/PROVIDER | PROVIDER | PROVIDER | PROVIDER | JWT + board secret + payload | — | NO | bytes/header/query | `WebhookAcceptedResponse` | 202,400,403,503 | `WebhookController.java:L54-L73`; `JiraWebhookJwtVerifier#verify` |
| ALL | `/oauth2/**` | Spring Security | PUBLIC | YES | YES | YES | OAuth infrastructure route | — | theo method | — | redirect/TBD | TBD | `SecurityConfig.java:L88-L92` |
| ALL | `/login/**` | Spring Security | PUBLIC | YES | YES | YES | OAuth infrastructure route | — | theo method | — | redirect/TBD | TBD | `SecurityConfig.java:L88-L92` |
| ALL | `/error` | Spring Boot/Security | PUBLIC | YES | YES | YES | error dispatch | — | theo method | — | error | TBD | `SecurityConfig.java:L88-L92` |
| GET | `/`, `/index.html`, `/favicon.ico`, `/assets/**`, `/css/**`, `/js/**`, `/images/**` | static resource | PUBLIC | YES | YES | YES | static matcher | — | NO | — | static content | 200,404 | `SecurityConfig` |
| POST | `/api/auth/logout` | Spring Security logout | FRAMEWORK/CSRF-GATED | YES | YES | YES | valid CSRF redirects even without a current session; authenticated session is invalidated when present | — | YES | `X-XSRF-TOKEN` or `_csrf` | redirect | 302,403 | `SecurityConfig`; `CognitoLogoutSuccessHandler`; `SecurityIntegrationTest` |
| GET | `/actuator/health`, `/actuator/health/**` | Actuator | PUBLIC | YES | YES | YES | — | — | NO | — | health | 200 | `SecurityConfig.java:L93-L104`; `application.properties:L25-L27` |
| GET | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | Springdoc | PUBLIC khi enabled | YES | YES | YES | feature flag | — | NO | — | OpenAPI/UI | 200,404 | `SecurityConfig.java:L112-L117`; `OpenApiConfig.java` |
| ALL | `/api/admin/**` | Không có controller hiện tại | AUTHENTICATED | YES | NO | NO | URL matcher | — | theo method | TBD | TBD | 401,403,404 | `SecurityConfig.java:L118`; controller scan |

### API chỉ dành cho ADMIN

`POST /api/v1/subjects`, `/classes`, `/semesters`, `/courses` là ADMIN-only.

### API chỉ dành cho LECTURER

Không có endpoint chỉ LECTURER trong code hiện tại.

### API dành cho ADMIN và LECTURER

Identity review dành cho ADMIN hoặc Lecturer scoped. Project integration dành cho ADMIN hoặc Lecturer là instructor của Course tương ứng; Student LEADER cũng có đường quyền riêng.

### API STUDENT được phép sử dụng

Student dùng personal integrations của mình; Student LEADER có thể tạo/quản lý project integration đúng team.

### API yêu cầu Team LEADER

Create team project và toàn bộ Project Integration Manager path yêu cầu Student `RoleInTeam.LEADER` nếu caller là Student.

### API phụ thuộc ownership/membership

Personal mapping phụ thuộc Student ownership; review phụ thuộc course/team của Lecturer; project phụ thuộc team manager; GitHub repo link còn kiểm tra installation owner của Student.

### API chỉ cần authenticated nhưng chưa có role restriction

`/api/auth/me` và toàn bộ master-data GET chỉ cần authenticated. Đây không chứng minh policy nghiệp vụ mong muốn.

### API có dấu hiệu thiếu bảo vệ hoặc không nhất quán

Master-data GET cho mọi authenticated user; `AccountStatus` không được enforce. Integration controller không có annotation nhưng service check đầy đủ; đây là phụ thuộc kiến trúc dễ bị bypass nếu thêm endpoint mới sai cách.

### Kết luận câu hỏi “CRUD có chỉ dành cho Lecturer hay không?”

**Không.** Subject/Class/Semester/Course chưa phải CRUD hoàn chỉnh: chỉ có Create và Read; Create là **ADMIN-only**, Read là mọi authenticated role. Personal integration là Student-only. Project/integration mutation là ADMIN, Lecturer scoped hoặc Student LEADER. Không có CRUD nào được code chứng minh là Lecturer-only.

## 7. Functional Requirements

| Thuộc tính | FR-AUTH-001 |
| --- | --- |
| Tên requirement | Đăng nhập Cognito và provisioning local profile |
| Actor | ADMIN, LECTURER, STUDENT có OIDC user từ Cognito |
| Preconditions | `sub`, email verified, name, group role hợp lệ |
| Main behavior | Đồng bộ profile đúng role, tạo session `SagaPrincipal`, redirect success URI |
| Authorization | Cognito group resolve theo priority ADMIN → LECTURER → STUDENT |
| Validation / failure | Student email cần student code; conflict 409, identity invalid 422, profile/provider lỗi 502 |
| Implementation status / evidence | CONFIRMED — `CognitoAuthenticationSuccessHandler#onAuthenticationSuccess`; `AuthenticatedProfileService#synchronize` |

| Thuộc tính | FR-USER-001 |
| --- | --- |
| Tên requirement | Quản lý provider identity cá nhân |
| Actor | STUDENT |
| Preconditions | Session Student, provider integration enabled |
| Main behavior | Xem, connect, disconnect Jira/GitHub identity của chính mình |
| Authorization | `IdentityMappingService#requireStudent` |
| Validation / failure | External identity unique; duplicate/conflict 409; OAuth state invalid 400 |
| Implementation status / evidence | CONFIRMED — `PersonalIntegrationService.java`; `IdentityMappingService.java` |

| Thuộc tính | FR-SEMESTER-001 / FR-SUBJECT-001 / FR-CLASS-001 |
| --- | --- |
| Tên requirement | Tạo và đọc Semester, Subject, Class |
| Actor | ADMIN tạo; mọi authenticated role đọc |
| Preconditions | DTO hợp lệ |
| Main behavior | Tạo code duy nhất, tìm kiếm/phân trang, đọc theo id |
| Authorization | Bốn POST master-data có `@PreAuthorize(ADMIN)`; `@PreAuthorize` thứ năm thuộc endpoint import với ADMIN/LECTURER scope |
| Validation / failure | `@NotBlank`, `@Size`; duplicate 409; Semester end không trước start |
| Implementation status / evidence | CONFIRMED/PARTIAL CRUD — `SemesterService#createSemester`, `SubjectService#createSubject`, `ClassService#createClass` |

| Thuộc tính | FR-COURSE-001 |
| --- | --- |
| Tên requirement | Tạo và đọc Course |
| Actor | ADMIN tạo; authenticated đọc |
| Preconditions | Subject/Class/Semester/Lecturer tồn tại |
| Main behavior | Tạo course code unique và filter theo subject/semester/instructor |
| Authorization | POST ADMIN-only |
| Validation / failure | DTO required; missing reference 404; duplicate 409 |
| Implementation status / evidence | CONFIRMED/PARTIAL CRUD — `CourseService#createCourse`; `CourseController.java` |

| Thuộc tính | FR-COURSE-IMPORT-001 |
| --- | --- |
| Tên requirement | Import danh sách sinh viên vào Course |
| Actor | ADMIN; LECTURER là instructor của Course |
| Preconditions | Authenticated `SagaPrincipal`, CSRF hợp lệ, Course tồn tại, file spreadsheet hợp lệ theo parser hiện tại |
| Main behavior | Tạo/reuse Student theo student code, Team `Group n` và TeamMember; Student có thể thuộc nhiều Course nhưng chỉ một Team/Course: cùng Team idempotent không đổi role, Team khác cùng Course conflict 409, khác Course hợp lệ |
| Authorization | ADMIN mọi Course; LECTURER có `localProfileId == Course.instructor.id`; STUDENT bị 403 |
| Validation / failure | Anonymous 401; thiếu CSRF/không đủ scope 403; Course thiếu 404; exception trong transaction rollback. Header/schema còn PARTIAL; identity binding đã có contract an toàn. |
| Implementation status / evidence | PARTIAL — `CourseImportAuthorizationService`, `ExcelImportService`, `CourseImportSecurityIntegrationTest` (13 cases pass) |

| Thuộc tính | FR-TEAM-001 / FR-PROJECT-001 |
| --- | --- |
| Tên requirement | Tạo Team Project và quản lý project integration |
| Actor | ADMIN, Lecturer owner, Student LEADER |
| Preconditions | Team/project tồn tại; team chưa có project khi tạo |
| Main behavior | Create project; xem/link/disconnect Jira/GitHub, xem sync state |
| Authorization | `requireTeamManager` / `requireProjectManager` |
| Validation / failure | Một project/team; forbidden 403; conflict 409 |
| Implementation status / evidence | CONFIRMED — `TeamProjectService#create`; `ProjectIntegrationService` |

| Thuộc tính | FR-JIRA-001 |
| --- | --- |
| Tên requirement | Jira 3LO và đồng bộ project |
| Actor | Student personal hoặc Project Manager |
| Preconditions | Jira enabled/config hợp lệ, OAuth state session |
| Main behavior | Authorize, link cloud/project, lưu credential encrypted, dynamic webhook, backfill/sync |
| Authorization | Personal Student-only; project Manager rule |
| Validation / failure | Provider/config/encryption errors mapped 400/409/502/503 |
| Implementation status / evidence | CONFIRMED — `JiraProviderClientImpl.java`; `ProjectIntegrationService.java` |

| Thuộc tính | FR-GITHUB-001 |
| --- | --- |
| Tên requirement | GitHub OAuth/App installation và repository integration |
| Actor | Student personal hoặc Project Manager |
| Preconditions | GitHub enabled/config hợp lệ; installation hợp lệ |
| Main behavior | Link identity, install/verify App, chọn repository, nhận signed webhook, backfill |
| Authorization | Manager; Student link repository phải owner installation |
| Validation / failure | State/permission invalid 400/403; duplicate 409 |
| Implementation status / evidence | CONFIRMED — `GitHubProviderClientImpl.java`; `WebhookIngestionService.java` |

| Thuộc tính | FR-SYNC-001 |
| --- | --- |
| Tên requirement | Sync/reconciliation job |
| Actor | Scheduler/event nội bộ |
| Preconditions | Integration linked và provider available |
| Main behavior | Claim job, overlap/cursor, upsert idempotent, recover stale job |
| Authorization | Không có HTTP endpoint sync trigger hiện tại |
| Validation / failure | Job status/failure diagnostics; provider failure |
| Implementation status / evidence | CONFIRMED — `JiraSyncJobService.java`; `IntegrationReconciliationScheduler.java`; `GitHubDataUpsertService.java` |

| Thuộc tính | FR-ASSESSMENT-001 |
| --- | --- |
| Tên requirement | Assessment/rubric/evidence |
| Actor | TBD |
| Preconditions | Entity tồn tại |
| Main behavior | Không có workflow HTTP/service hoàn chỉnh tìm thấy |
| Authorization / validation / failure | TBD |
| Implementation status / evidence | PARTIAL — `entity/Assessment.java`, `AssessmentEvidence.java`, `RubricTemplate.java` |

## 8. Business Rule và Domain Constraint

- Group Cognito được normalize; ưu tiên ADMIN, rồi LECTURER, rồi STUDENT. `CognitoRoleResolver#resolve`.
- OIDC bắt buộc `sub`, email verified, name và supported group. `OidcIdentityService#extract`.
- Student email phải trích xuất được student code. Successful accepted STUDENT authentication không có local match tạo Student `ACTIVE`; Student mới do import/pre-provision tạo trước identity binding là `PENDING`. `AuthenticatedProfileService#extractRequiredStudentCode`, `#create`.
- Một Cognito subject/email chỉ được khớp tối đa một profile local, và profile type phải khớp role. `AuthenticatedProfileService#synchronizeInternal`.
- Subject/Class/Semester/Course code được kiểm tra duplicate; Course cần bốn foreign reference; Semester end không trước start. `*Service#create*`.
- Một team chỉ được một project. `TeamProjectService#create`.
- Personal identity mapping: một mapping/provider/Student và một external provider id toàn cục; mapping active khác phải disconnect trước replace. `IdentityMappingService`; `V2__integration_identity_and_sync.sql`.
- Reviewer là ADMIN hoặc Lecturer dạy Course có Student target. `IdentityMappingReviewService#requireReviewer`.
- Project manager là ADMIN, Course instructor, hoặc Student LEADER exact team; ADMIN override được audit. `ProjectIntegrationAuthorizationService#requireTeamManager`.
- Import sinh viên: ADMIN mọi Course; LECTURER chỉ Course do mình dạy; STUDENT bị từ chối. `CourseImportAuthorizationService#requireImportAccess`.
- OAuth state random 32 bytes, lưu hash trong session, one-time, TTL mặc định PT10M và bind sub/profile/flow/target. `OAuthStateService`.
- Secret integration AES-256-GCM, nonce 12 bytes, AAD theo purpose, key version/rotation. `IntegrationSecretCipher`.
- Webhook receipt encrypted, deduplicate `(provider, delivery_id)`. `WebhookIngestionService#persist`; migration V2.
- Jira sync dùng overlap/cursor và time zone config; GitHub/Jira external id hỗ trợ idempotent upsert. Jira labels là `Task` snapshot `labels_json` JSON-in-TEXT, provider request/parse `List<String>` và upsert replace-all; V8 nullable bảo toàn Task cũ. Không có normalized Label entity hoặc Task HTTP API. `JiraSyncWindow`; `JiraIssueUpsertService`; V2/V8.
- Không tìm thấy soft delete; DELETE integration đổi trạng thái/disconnect thay vì xoá record. Hard delete behavior các entity khác: TBD.

## 9. Dependency

### Java/Spring Dependency

| Dependency | Version | Nguồn version | Mục đích | Được sử dụng bởi |
| --- | --- | --- | --- | --- |
| Spring Boot parent | 4.1.0 | `pom.xml` | dependency management | build/runtime |
| Java | 17 | `pom.xml` property | compiler/runtime target | application |
| data-jpa; mysql-connector-j | Boot managed | parent | MySQL/JPA | entity/repository/service |
| data-mongodb | Boot managed | parent | Mongo audit | `SystemAuditLogRepository` |
| starter-flyway; flyway-mysql | Boot managed | parent | migration | `db/migration` |
| webmvc | Boot managed | parent | REST MVC | controller |
| security; oauth2-client | Boot managed | parent | session/Cognito OIDC | security package |
| actuator | Boot managed | parent | health | Railway |
| lombok; validation | Boot managed | parent | boilerplate/DTO validation | entity/DTO |
| springdoc-openapi-starter-webmvc-ui | 3.0.3 | explicit | OpenAPI/Swagger | `OpenApiConfig` |
| JPA test; WebMVC test; H2; security-test | Boot managed | parent | test | `src/test` |

### Node.js/Lambda Dependency

| Lambda | Dependency | Version | Mục đích |
| --- | --- | --- | --- |
| Cognito account linking | Node.js | >=20 | runtime |
| Cognito account linking | `@aws-sdk/client-cognito-identity-provider` | 3.1096.0 | List/Get/Link Cognito user |

### External Service

| Service | Bằng chứng sử dụng |
| --- | --- |
| AWS Cognito | OIDC client/issuer, Hosted UI, group mapping |
| AWS Lambda | Pre Sign-up account linking độc lập |
| Google Identity Provider | `Google_<subject>` trong Lambda qua Cognito |
| MySQL/Aiven-compatible JDBC | JPA/Flyway config |
| MongoDB Atlas-compatible | Mongo URI/audit/health config |
| Jira Cloud | 3LO, API, dynamic webhook, sync |
| GitHub App | OAuth, installation, API, webhook |
| Gmail REST API | Student Course Invitation qua OAuth 2.0 token refresh và `users.messages.send` HTTPS |
| Railway | `railway.json` build/deploy |

Bằng chứng: `pom.xml`; `infra/lambda/cognito-account-linking/package.json`; `application.properties`; `railway.json`.

## 10. Environment Variable và Configuration

`Bắt buộc` phản ánh code/config khi feature tương ứng bật; không có giá trị secret nào được hiển thị.

| Variable/Property | Profile | Bắt buộc | Mục đích | Secret | Default | Validation/Ràng buộc | Bằng chứng |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `PORT` | all | NO | HTTP port | NO | 8080 | — | `application.properties:L3` |
| `PUBLIC_BASE_URL` | all | YES | public origin | NO | local localhost | absolute origin; HTTPS trừ local/test | `application.properties:L5`; `IntegrationPublicUrlValidator#validate` |
| `FRONTEND_ORIGINS` | all | YES | CORS origins | NO | none | HTTP(S) explicit, không wildcard | `CorsConfig#corsConfigurationSource` |
| `AUTH_SUCCESS_REDIRECT_URI`, `AUTH_LOGOUT_REDIRECT_URI` | all | success YES | auth redirects | NO | logout fallback success | absolute HTTP(S) | `application.properties:L7-L8`; handlers |
| `COGNITO_DOMAIN`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, `COGNITO_ISSUER_URI` | all | registration cần values | Cognito OIDC/logout | client secret YES | domain empty | domain logout HTTPS origin | `application.properties:L9-L21` |
| `DATABASE_JDBC_URL`/`AIVEN_JDBC_URL`, username aliases, password aliases | all | YES | MySQL | password YES | none | Hibernate validate | `application.properties:L35-L45` |
| `MONGO_URI`, `MONGO_DATABASE`, `MONGO_HEALTH_TIMEOUT` | all | URI/database YES | Mongo | URI có thể secret | PT5S | health Mongo default disabled | `application.properties:L47-L49` |
| `GMAIL_API_CLIENT_ID`, `GMAIL_API_CLIENT_SECRET`, `GMAIL_API_REFRESH_TOKEN`, `GMAIL_API_SENDER_EMAIL`, `GMAIL_API_SENDER_NAME` | invitation delivery | YES when Gmail enabled | Gmail REST API | client secret/refresh token YES | empty | all five required; missing config uses unavailable adapter; sender email must be authorized by the Gmail account | `GmailApiStudentInvitationProperties`; `StudentInvitationDeliveryConfiguration` |
| `STUDENT_INVITATION_LOGIN_URL` | invitation delivery | YES | email CTA entry URL | NO | public-base `/api/auth/login` | absolute HTTP(S), host, no userinfo | `StudentInvitationProperties#loginUri` |
| `SESSION_COOKIE_SECURE`, `SESSION_COOKIE_SAME_SITE` | prod | NO | cookie policy | NO | true/none prod | local false/lax | `application-prod.properties` |
| Springdoc flags (`SPRINGDOC_*`, `SWAGGER_ENABLED`) | all | NO | docs exposure | NO | false | public only when enabled | `application.properties:L29-L30` |
| `FLYWAY_ENABLED`, `FLYWAY_BASELINE_ON_MIGRATE` | all | NO | migration | NO | false | schema normally validate | `application.properties:L42-L44` |
| Jira variables `JIRA_*` (enabled, client id/secret, URLs, scopes, time zone) | integration | YES when enabled | Jira 3LO/webhook/sync | secret YES | enabled true, zone UTC | callback/webhook public URL validated | `application.properties:L52-L61`; validator |
| GitHub variables `GITHUB_*` (enabled, App/client ids, secret, private key, URLs, slug, webhook secret) | integration | YES when enabled | GitHub App/OAuth | secret/key YES | enabled true | callback/setup/webhook URL validated | `application.properties:L64-L79`; validator |
| `LOCAL_WEBHOOK_BASE_URL` | local | NO | tunnel webhook URL | NO | empty | URL resolver | `application.properties:L80` |
| `INTEGRATION_TOKEN_ENCRYPTION_*` | integration secret storage | YES when used | AES key/rotation | YES | key id `primary` | Base64 32-byte active key; previous `id:key` | `IntegrationSecretCipher` |
| integration TTL/timeout/reconcile/stale variables | integration | NO | job timing | NO | values tại properties | Duration/number binding | `application.properties:L85-L92` |
| `LOCAL_DEMO_SEED_ENABLED`, `LOCAL_DEMO_LEADER_COGNITO_SUB` | local | NO | demo data | identifier | false/empty | disabled default | `application-local.properties:L19-L21` |

`SPRING_PROFILES_ACTIVE` không được khai báo bằng placeholder trong source hiện tại; profile `local`, `prod`, `test` tồn tại qua các file properties. Test profile dùng URL/client test, tắt provider integration, reconciliation và Mongo health. `src/test/resources/application-test.properties`.

**Danh sách đầy đủ 78 tên biến placeholder đã quét trong các profile properties** (các nhóm trong bảng trên không dùng wildcard để tạo thêm tên): `AIVEN_DB_PASSWORD`, `AIVEN_DB_USERNAME`, `AIVEN_JDBC_URL`, `AUTH_LOGOUT_REDIRECT_URI`, `AUTH_SUCCESS_REDIRECT_URI`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, `COGNITO_DOMAIN`, `COGNITO_ISSUER_URI`, `DATABASE_JDBC_URL`, `DATABASE_PASSWORD`, `DATABASE_USERNAME`, `FLYWAY_BASELINE_ON_MIGRATE`, `FLYWAY_ENABLED`, `FRONTEND_ORIGINS`, `GITHUB_API_BASE_URL`, `GITHUB_APP_ID`, `GITHUB_APP_SLUG`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_INTEGRATION_ENABLED`, `GITHUB_PERSONAL_CALLBACK_URL`, `GITHUB_PRIVATE_KEY`, `GITHUB_PROJECT_CALLBACK_URL`, `GITHUB_SETUP_URL`, `GITHUB_WEB_BASE_URL`, `GITHUB_WEBHOOK_PUBLIC_URL`, `GITHUB_WEBHOOK_SECRET`, `GMAIL_API_CLIENT_ID`, `GMAIL_API_CLIENT_SECRET`, `GMAIL_API_REFRESH_TOKEN`, `GMAIL_API_SENDER_EMAIL`, `GMAIL_API_SENDER_NAME`, `INTEGRATION_CALLBACK_REDIRECT_URI`, `INTEGRATION_CALLBACK_RESULT_TTL`, `INTEGRATION_HTTP_CONNECT_TIMEOUT`, `INTEGRATION_HTTP_READ_TIMEOUT`, `INTEGRATION_OAUTH_STATE_TTL`, `INTEGRATION_OVERLAP_WINDOW`, `INTEGRATION_RECONCILIATION_DELAY_MS`, `INTEGRATION_RECONCILIATION_ENABLED`, `INTEGRATION_TOKEN_ENCRYPTION_KEY`, `INTEGRATION_TOKEN_ENCRYPTION_KEY_ID`, `INTEGRATION_TOKEN_ENCRYPTION_PREVIOUS_KEYS`, `JIRA_API_BASE_URL`, `JIRA_AUTHORIZATION_URL`, `JIRA_CALLBACK_URL`, `JIRA_CLIENT_ID`, `JIRA_CLIENT_SECRET`, `JIRA_INTEGRATION_ENABLED`, `JIRA_SCOPES`, `JIRA_TIME_ZONE`, `JIRA_TOKEN_URL`, `JIRA_WEBHOOK_PUBLIC_URL`, `LOCAL_DEMO_LEADER_COGNITO_SUB`, `LOCAL_DEMO_SEED_ENABLED`, `LOCAL_WEBHOOK_BASE_URL`, `MONGO_DATABASE`, `MONGO_HEALTH_TIMEOUT`, `MONGO_URI`, `NOTIFICATION_DEADLINE_PROCESSING_ENABLED`, `NOTIFICATION_DEADLINE_SCAN_DELAY_MS`, `NOTIFICATION_DELIVERY_PROCESSING_ENABLED`, `NOTIFICATION_DELIVERY_PROCESSING_TIMEOUT_MS`, `NOTIFICATION_DELIVERY_RETRY_DELAY_MS`, `PORT`, `PRIVACY_CONTACT_URL`, `PUBLIC_BASE_URL`, `SESSION_COOKIE_SAME_SITE`, `SESSION_COOKIE_SECURE`, `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`, `STALE_SYNC_JOB_RECOVERY_DELAY_MS`, `STUDENT_INVITATION_LOGIN_URL`, `STUDENT_INVITATION_PROCESSING_TIMEOUT_MS`, `STUDENT_INVITATION_RETRY_DELAY_MS`, `SWAGGER_ENABLED`, `SYNC_JOB_STALE_AFTER`.

| Property cố định/không lấy từ env | Profile | Giá trị/ràng buộc thực tế | Bằng chứng |
| --- | --- | --- | --- |
| `spring.config.import` | all | optional `.env` và `.env.local` properties | `application.properties:L1` |
| OAuth registration | all | provider Cognito, `authorization_code`, redirect template, scope `openid,email,profile`, user-name `sub` | `application.properties:L12-L21` |
| HTTP/error/session | all | cookie session HttpOnly; không include message/stacktrace/binding errors | `application.properties:L23-L24` |
| Actuator/Mongo health | all | chỉ expose health, no details, Mongo contributor disabled | `application.properties:L25-L28` |
| Gmail API safety | all | không provider call lúc startup; dùng shared integration connect/read timeout, mặc định 3/10 giây; không Gmail health probe | `StudentInvitationDeliveryConfiguration` |
| JPA/Flyway | all | `ddl-auto=validate`, `open-in-view=true`, driver MySQL | `application.properties:L35-L45` |
| local profile | local | Swagger enabled, integration/reconcile disabled, session secure false/same-site lax | `application-local.properties` |
| prod profile | prod | session secure default true, same-site none | `application-prod.properties` |
| test profile | test | H2 create-drop, Flyway false, test OAuth endpoints, Springdoc false | `application-test.properties` |

## 11. Database và Persistence Constraint

- **MySQL/JPA:** mọi `@Entity` trong `entity/` là JPA; `BaseEntity` có UUID/id/audit timestamp. Quan hệ chính: Course→Subject/Class/Semester/Lecturer; Team→Course/Project; TeamMember→Team/Student; Project→Course; Jira/GitHub integration entities. `spring.jpa.open-in-view=true` vì controller serialize JPA entity.
- **MongoDB:** `SystemAuditLog` là document và repository Mongo được chứng minh. `MongoCollectionInitializer`/health tồn tại.
- **Ràng buộc:** migration V2 có foreign key/index/unique identity mapping, Jira board, GitHub installation/repository, external task/sprint/issue/commit/PR/review/comment và webhook delivery. Pessimistic lock có ở mapping/board/repo/receipt repository.
- **Transaction:** master data create, profile, integration mutation, webhook persist, sync claim/upsert đều có `@Transactional`; một số sync dùng `REQUIRES_NEW`.
- **Cảnh báo:** Spring Data strict mode có warning không xác định store cho repository của datastore còn lại khi JPA/Mongo cùng scan; cần theo dõi nhưng không tự suy ra sai datastore.
- **Encryption-at-rest do application:** Jira token và webhook payload ciphertext bằng `IntegrationSecretCipher`.

Bằng chứng: `V2__integration_identity_and_sync.sql`; `BaseEntity.java`; `SystemAuditLogRepository.java`; `IdentityMapRepository.java`; `JiraBoardRepository.java`; `GitRepoRepository.java`; `WebhookReceiptRepository.java`; `JiraSyncJobService.java`.

## 12. Security Constraint

- OAuth2/OIDC session: `HttpSessionSecurityContextRepository`, `IF_REQUIRED`, session fixation migrate; logout xoá cookie. Không có Redis/Spring Session dependency/config.
- Cookie: `JSESSIONID`; CSRF `XSRF-TOKEN` cookie được tạo bằng `CookieCsrfTokenRepository.withHttpOnlyFalse`. Session cookie HttpOnly true; Secure/SameSite theo profile.
- CORS: explicit origins, `allowCredentials=true`, methods GET/POST/PUT/PATCH/DELETE/OPTIONS; allowed request headers `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`, `Idempotency-Key`; FE cross-origin phải `credentials: "include"`. `Idempotency-Key` bắt buộc cho Jira Task/Sprint mutation nên CORS preflight phải allow header này; không dùng wildcard.
- Mutation POST/PUT/PATCH/DELETE phải gửi `X-XSRF-TOKEN` tương ứng cookie; webhook miễn CSRF.
- `/api/auth/login` nên browser redirect, không fetch/Axios login flow. Swagger cookie `JSESSIONID`; cần login cùng browser và tự thêm CSRF cho mutation.
- Provider security: GitHub HMAC SHA-256 constant-time comparison; Jira HS256 JWT/time claims và board secret. Cognito provider token không lưu/đưa ra FE; Jira token server-side encrypted; GitHub installation token cache in-memory; refresh token không đưa FE.
- Jira provider error logging redact token/client secret/Bearer. Error API dùng JSON custom cho auth/integration; master-data còn có Spring `ResponseStatusException`.

Bằng chứng: `SecurityConfig.java`; `CorsConfig.java`; `OpenApiConfig.java`; `IntegrationSecretCipher.java`; `GitHubWebhookSignatureVerifier.java`; `JiraWebhookJwtVerifier.java`; `JiraProviderClientImpl#redactAndTruncate`.

## 13. Jira Integration

- Jira dùng OAuth 2.0 3LO; callback chung `/api/integrations/jira/callback` resolve personal/project bằng session OAuth state. Project flow kiểm tra Manager trước và sau callback.
- Project link nhận cloudId/jiraProjectId, kiểm tra resource/provider, lưu token encrypted, đăng ký/duy trì dynamic webhook và dispatch work sau commit.
- URL callback/webhook phải khớp public-base derived URL; production webhook bắt buộc HTTPS.
- Dynamic webhook có random secret mỗi board, chỉ hash secret lưu DB. Ingress Jira xác thực Bearer JWT, expiry/nbf/iat và board secret.
- Sync có JQL, pagination provider, configured Jira time zone (default UTC), overlap/cursor lower-upper bound, stale recovery và idempotent upsert.
- Disconnect chuyển integration state; reconnect/backfill/sync theo service hiện tại. Provider/config/encryption lỗi có thể 400/409/502/503.

Bằng chứng: `JiraProviderClientImpl.java`; `ProjectIntegrationService.java`; `JiraCredentialService.java`; `JiraWebhookMaintenanceService.java`; `JiraSyncWindow.java`; `JiraSyncJobService.java`.

## 14. GitHub Integration

- Personal flow dùng GitHub OAuth callback để link identity Student. Project flow dùng GitHub App installation; setup/project callback consume OAuth state rồi recheck Project Manager.
- Repository selection nhận installation id và repository ids; code không có branch-selection request endpoint. Student phải vừa là Team LEADER/manager vừa owner installation để link repository.
- Webhook xác thực `X-Hub-Signature-256`, allow-list event; `ping` không durable; receipt encrypted/deduplicated, sau đó async process/backfill.
- App private key chỉ lấy từ configuration để ký/mint installation token. Token cache nằm trong `ConcurrentHashMap`, có expiry skew, không persist DB.
- Upsert issue/commit/PR/review/comment dùng external provider ids để idempotent; reconciliation có scheduler.

Bằng chứng: `GitHubProviderClientImpl.java`; `ProjectIntegrationService.java:L399-L625`; `WebhookIngestionService.java`; `GitHubDataUpsertService.java`; `IntegrationReconciliationScheduler.java`.

## 15. Deployment Constraint

- Java target 17; Spring Boot parent 4.1.0. Railway Railpack build `mvn clean package -DskipTests`, start jar, healthcheck `/actuator/health`, retry on failure.
- `PUBLIC_BASE_URL`, Cognito callback/logout allowlist ngoài repository, `AUTH_SUCCESS_REDIRECT_URI`, `FRONTEND_ORIGINS`, cookie cross-origin và provider public webhook URLs phải tương thích.
- MySQL/Mongo URL cần được provision ngoài code; Hibernate validate schema, Flyway flag-controlled.
- Session và GitHub token cache in-memory mất khi redeploy. Horizontal scaling/persistent session/Redis: **TBD; RECOMMENDED nếu cần scale**, không có code hiện tại.
- Lambda deploy/IAM/trigger độc lập Railway. Encryption key phải ổn định và giữ previous keys khi rotation để decrypt record cũ.

Bằng chứng: `pom.xml:L10-L177`; `railway.json`; `application*.properties`; `IntegrationPublicUrlValidator.java`; Lambda README.

## 16. Hướng dẫn test API

### Browser login

Mở `/api/auth/login` bằng browser navigation, hoàn tất Cognito cùng browser. Không dùng fetch/Axios để kỳ vọng browser hoàn tất redirect login.

### Current user và Swagger

Gọi `/api/auth/me`, kiểm tra `applicationRole`, `localProfileId` và cookie `XSRF-TOKEN`. Nếu Springdoc bật, mở Swagger cùng browser: Swagger dùng `JSESSIONID`, không cần Bearer token. Mutation vẫn cần CSRF header.

### Mutation API

Gửi `JSESSIONID` qua `credentials: "include"`; lấy cookie `XSRF-TOKEN` và gửi `X-XSRF-TOKEN`. 403 do thiếu CSRF khác 403 authorization: thử lại cùng CSRF token và role phù hợp để phân biệt.

### Role testing

1. ADMIN POST từng master resource phải pass; Lecturer/Student phải 403; tất cả authenticated GET được.
2. Student personal integration pass; ADMIN/Lecturer phải 403.
3. Test project với ADMIN, Lecturer owner/non-owner, Student LEADER, MEMBER/non-member.
4. Test GitHub repository link bằng Student owner/non-owner của installation.
5. Webhook unsigned/malformed phải 400/403; gửi lại delivery hợp lệ phải `DUPLICATE`.
6. Xác nhận 401 khi không session, 404 resource thiếu, 409 duplicate, 422 OIDC identity invalid, 502 provider/profile failure.

## 17. Known Issue và Inconsistency

| Severity | Issue | Ảnh hưởng | Bằng chứng | Khuyến nghị |
| --- | --- | --- | --- | --- |
| HIGH | AccountStatus không enforce permission. | PENDING/INACTIVE/SUSPENDED vẫn không bị chặn bởi inspected authorization code. | `AuthenticatedProfileService:L209-L211,L261`; account-status search | RECOMMENDED: xác định và enforce policy status. |
| HIGH | Master data CRUD không hoàn chỉnh, Create ADMIN-only. | Sai nếu kỳ vọng CRUD Lecturer-only. | bốn controller master data | RECOMMENDED: chốt policy rồi bổ sung/update quyền. |
| HIGH | Validation spreadsheet còn dựa magic columns. | Có thể nhận dữ liệu không đúng contract. | `ExcelImportService` | RECOMMENDED: header/schema/error DTO trước production. Gmail adapter đã có source/test; runtime delivery smoke vẫn TBD. |
| MEDIUM | Guard application ngăn một Student vào hai Team trong cùng Course, nhưng database chưa có invariant trực tiếp `UNIQUE(student_id, course_id)`. | Chỉ write path tuân thủ guard được bảo vệ; dữ liệu legacy invalid không tự được sửa. | `ExcelImportService`; `StudentRepository`; `TeamMemberRepository` | RECOMMENDED: migration/invariant DB sau khi có kế hoạch xử lý legacy data. |
| MEDIUM | Master-data GET mở cho mọi authenticated Student. | Có thể lộ dữ liệu ngoài scope nếu policy muốn hạn chế. | `SecurityConfig:L119`; controller GET | RECOMMENDED: xác nhận scope nghiệp vụ. |
| MEDIUM | Project integration dựa service checks, không annotation. | Endpoint mới có thể bypass nếu gọi service sai. | `ProjectIntegrationController`; permission service | RECOMMENDED: bổ sung negative tests/guard pattern. |
| LOW | Swagger UI CSRF interceptor chỉ hoạt động cùng API origin; browser FE cross-site vẫn phụ thuộc cookie policy. | Swagger mutation không còn cần nhập header thủ công; frontend cross-site vẫn cần `/api/auth/csrf` và E2E. | `SwaggerUiCsrfConfiguration`; `AuthController#csrf` | browser E2E production-like. |
| MEDIUM | Session/token cache in-memory. | Mất session/cache sau redeploy, không chứng minh horizontal scale safe. | `SecurityConfig`; `GitHubProviderClientImpl#tokenCache` | RECOMMENDED: persistent session nếu scale. |
| LOW | `/api/admin/**` matcher không có controller. | Rule không bảo vệ API hiện có. | `SecurityConfig:L118`; scan controller | RECOMMENDED: giữ đồng bộ hoặc xóa rule thừa. |
| LOW | Error/response shape không đồng nhất. | FE phải xử lý nhiều error format. | `GlobalExceptionHandler`; `ResponseStatusException` services | RECOMMENDED: chuẩn hoá contract. |
| LOW | JPA/Mongo strict scanning warning. | Vận hành có warning store ambiguity. | Spring Data config/test logs; repositories | RECOMMENDED: cấu hình repository scan tách rõ. |

Không có bằng chứng ADMIN thiếu override: `requireTeamManager` chứng minh ADMIN override. Không có bằng chứng `accountStatus` serialize string `"null"`; chỉ ghi đây là TBD, không khẳng định lỗi.

## 18. Open Question / TBD

- Source/deployment của Cognito Pre Token Generation role-assignment Lambda là gì?
- User Pool deployed có email là username/alias không? Lambda README nói repository không chứng minh được.
- Master-data GET cho mọi Student có phải policy chủ đích không?
- Status nào phải được phép dùng API sau provisioning?
- Baseline migration/schema trước V2 tạo các bảng legacy bằng cách nào?
- Railway có phải production duy nhất, và có session persistence ngoài repository không?
- Delete của entity ngoài integration là hard hay soft delete? Không có endpoint/code đủ chứng minh.
- Assessment hiển thị/sửa ở mức nào cho Student/Lecturer? Chưa có API workflow.

## 19. Traceability Index

| Phần tài liệu | Source file chính | Class/Method liên quan |
| --- | --- | --- |
| 2, 7 | `controller/*`, `integration/*`, `entity/*` | controller/service workflows |
| 3–5, 12 | `SecurityConfig.java`, `Cognito*.java`, `CorsConfig.java` | security chain, auth handlers |
| 6 | toàn bộ `controller/*.java` | mapping methods; permission services |
| 8, 11 | `service/*`, `repository/*`, `entity/*`, migration V2–V7 | business/integrity/transaction |
| 9 | `pom.xml`, Lambda `package.json` | dependencies |
| 10, 15 | `application*.properties`, `railway.json` | config/deploy |
| 13–14 | `integration/provider`, `project`, `sync`, `webhook` | Jira/GitHub flows |
| 16–17 | `src/test/java/*` và implementation | behavior/risk evidence |

## Cập nhật 2026-08-04 — ràng buộc sync vận hành

- **FR-SYNC-001 / CONFIRMED:** `SyncJobLog` lưu UTC semantics trong `LocalDateTime`/`DATETIME(6)`; write path dùng UTC Clock. API `/api/projects/{projectId}/sync-status` trả `Instant` ISO-8601 có `Z`; frontend chịu trách nhiệm format theo timezone UI.
- **CONFIRMED:** mỗi GitRepo chỉ có một GitHub sync job active non-stale. Claim và state update khóa `PESSIMISTIC_WRITE` đúng row repository; không khóa table, Course hay repository khác.
- **CONFIRMED:** complete/degrade reload managed `GitRepo` theo id trong `REQUIRES_NEW`; finalization reload/lock `SyncJobLog` theo jobId trong `REQUIRES_NEW`, idempotent và không bị lỗi degrade chặn.
- **CONFIRMED:** stale recovery chỉ finalize GitHub `IN_PROGRESS` quá threshold; job fresh không bị động tới. Không retry toàn bộ provider sync, không dùng `synchronized`/`ConcurrentHashMap` làm guard chính.
- **PARTIAL/TBD:** application guard chỉ bảo vệ production flow tuân thủ claim; external writer của incident cũ và kết quả recovery row production cũ chờ kiểm chứng sau deploy.
- **Không thay đổi:** không migration, manual-sync endpoint, OAuth/callback/resultId, HttpSession/JSESSIONID, CSRF, CORS, scope, webhook verification hay encryption. Maven: **70 suites / 299 tests / 0 failures / 0 errors / 0 skipped**.

## Callback result contract update (2026-08-04)

| Route | Authorization / CSRF | Result |
|---|---|---|
| Four Jira/GitHub OAuth completion callbacks | Existing authenticated browser session/state validation; GET | `302` configured frontend URI, query only `resultId` |
| `POST /api/integrations/callback-results/{resultId}/consume` | Authenticated + CSRF; personal Student-only or current Project Manager | Safe read-once callback result; missing/expired/replayed/wrong-session uses controlled non-oracle error |

`INTEGRATION_CALLBACK_REDIRECT_URI` is absolute HTTP(S), host-required and rejects userinfo/query/fragment. `INTEGRATION_CALLBACK_RESULT_TTL` is positive and defaults `PT5M`. Both are separate from `AUTH_SUCCESS_REDIRECT_URI`; provider URLs, scopes, exchange, session and webhook exemptions remain unchanged.

## Biên bản kiểm tra sau khi tạo file

- Quét lại HEAD `0bc30be`: **16 REST controller có HTTP mapping, 46 controller HTTP methods**; thêm 1 `@RestControllerAdvice` (`GlobalExceptionHandler`) không có endpoint. `POST /api/auth/logout` là framework-managed ngoài controller scan.
- `@PreAuthorize`: **6**; `@Secured`: **0**. Endpoint mới dùng `hasRole('STUDENT')`; permission check chính còn lại không đổi.
- Đối chiếu `pom.xml`, package Lambda và bảng dependency; có 16 dependency Maven application/test, 2 dependency Flyway plugin và 1 Node Lambda dependency.
- Đối chiếu properties/placeholders với bảng configuration; không copy password, token, private key hoặc client secret vào tài liệu.
- Test source Java files/classes: **72**; full Maven checkpoint hiện tại: 70 Surefire suites, **299 tests, 0 failures, 0 errors, 0 skipped**.
- Task documentation hiện tại chỉ sửa sáu file Markdown; không commit/push.

## Update 2026-08-02 — Student provisioning và invitation outbox (working tree)

| Hạng mục | Trạng thái | Evidence/ràng buộc |
|---|---|---|
| Identity normalization | CONFIRMED | Import và OIDC dùng email trim/lowercase; student code trim/uppercase / extractor hiện có. |
| Bind imported Student | CONFIRMED | Cần email + student code cùng chỉ một Student, subject null, role STUDENT; row lock + transaction; conflict 409 an toàn. |
| Status | CONFIRMED | Chỉ `PENDING → ACTIVE` khi bind; ACTIVE giữ nguyên; INACTIVE/SUSPENDED không tự kích hoạt. |
| Course/Team access | CONFIRMED | Student global; access giữ bởi TeamMember hiện hữu. Bind không tạo/xoá/sửa TeamMember hay RoleInTeam. |
| Invitation | CONFIRMED | V6 outbox unique Student/Course/type sau import commit; claim/FAILED/SENT/retry tối đa 5; stale `PROCESSING` chỉ reclaim sau timeout cấu hình; không rollback import khi delivery lỗi; at-least-once. |
| Email provider | CONFIRMED_SOURCE_TEST / TBD_DEPLOYMENT_SMOKE | Gmail REST API adapter qua OAuth refresh + HTTPS `users.messages.send` đã có; production secrets/delivery/inbox chưa smoke. |
| Import parser/DB invariant | PARTIAL | Header/schema, preview, error DTO từng dòng và database invariant trực tiếp `UNIQUE(student_id, course_id)` chưa có. |
| Swagger CSRF | CONFIRMED | `withCredentials`; cookie `XSRF-TOKEN`; global same-origin interceptor chỉ POST/PUT/PATCH/DELETE gắn `X-XSRF-TOKEN`; không Bearer. |
| Logout | CONFIRMED | Framework-managed `POST /api/auth/logout`; valid CSRF 302 Cognito, missing/invalid 403; Swagger fetch có thể `Failed to fetch` khi redirect cross-origin. |
| Team roster | CONFIRMED | Paged TeamMemberResponse, ADMIN/Lecturer owner/Student exact-Team policy; 401/403/404 và không email/cognitoSub/version. |
| Course roster | PARTIAL / SOURCE DRIFT | Contract dùng `TeamMember -> Team -> Course`; filter/sort trước pagination, stable id tie-break, query invalid 400. `studentsWithoutTeam` phải rỗng vì chưa có enrollment Student–Course độc lập; invitation outbox không phải enrollment. Current baseline `CourseService#getCourseRoster` còn đọc outbox và fail contract test DEC-023. |
| Lecturer options | CONFIRMED | ADMIN-only; keyword fullName/email, sort fullName/email, không tìm/trả cognitoSub; invalid query 400. |
| One Team per Student/Course | CONFIRMED application guard | Excel import lock `PESSIMISTIC_WRITE` Student rồi query Student+Course; same Team idempotent, Team khác 409, khác Course hợp lệ. Test hai thread/hai transaction xác nhận đúng một membership. DB invariant trực tiếp vẫn PARTIAL. |

Configuration mới: `app.student-invitation.login-url` lấy từ `STUDENT_INVITATION_LOGIN_URL` (phải là absolute HTTP(S)); `app.student-invitation.retry-delay-ms` từ `STUDENT_INVITATION_RETRY_DELAY_MS`; `app.student-invitation.processing-timeout-ms` từ `STUDENT_INVITATION_PROCESSING_TIMEOUT_MS`. Không hard-code localhost/Railway, không dùng callback URL làm điểm bắt đầu login và không lưu secret.

Runtime fact do người dùng cung cấp: Railway từng fail vì DB thiếu `student.version`. V6/V7 phải chạy trước Hibernate `validate`; repository không có production log/dashboard nên migration production vẫn **TBD**, không CONFIRMED.

Full `./mvnw.cmd test` tại checkpoint hiện tại: **70 suites, 299 tests, 0 failures, 0 errors, 0 skipped**. Jira/GitHub/webhook, sync UTC serialization, GitHub claim/concurrency/stale recovery, session/CSRF/OIDC callback, master-data authorization và import authorization đều pass.
## Lecturer Analytics constraints — 2026-08-05

- Historical: Read-only GET; ADMIN mọi Course, LECTURER instructor-only, STUDENT forbidden.
- **SUPERSESSION (DEC-083):** STUDENT `/progress` MEMBER self / LEADER exact Team; MENTOR forbidden. Graph routes DEC-080 unchanged. Dashboard/activities/contribution-detail/early-warnings remain STUDENT forbidden.
- Team/Student phải thuộc đúng Course trong URL; Student+Team filter phải khớp membership.
- Không có committed story-point snapshot, Jira transition history, AI/NLP signal hay heatmap level rule.
- Contribution Detail chỉ adapter aggregate hiện hữu; không sao chép công thức và không sửa nhóm 2.
- Không thêm migration, environment variable hoặc dependency.
- Verification: full Maven 77 suites / 339 tests pass; targeted analytics 21 tests,
  Team roster security 13 tests và GitHub/Jira/Contribution regression 20 tests pass.

## Cập nhật 2026-08-09 — Flyway rubric upgrade M4-R2

- Flyway dependency/plugin dùng phiên bản `12.4.0`. Source đặt
  `spring.flyway.enabled=${FLYWAY_ENABLED:false}`,
  `baseline-on-migrate=${FLYWAY_BASELINE_ON_MIGRATE:false}` và `baseline-version=1`.
  Không có source setting cho `out-of-order`, `validate-on-migrate`,
  `ignore-migration-patterns` hoặc locations; locations mặc định là
  `classpath:db/migration`.
- V22 chỉ là **EXISTING_BASELINED_DB_UPGRADE**: `ALTER TABLE rubric_template MODIFY
  COLUMN subject_id CHAR(36) NULL`. Không có DML, không sửa V10/V13, không thêm
  dependency/config Flyway và không thay JPA `ddl-auto=validate`.
- `RubricMigrationContractTest` pass; full Maven pass 105 suites / 646 tests /
  0 failures / 0 errors / 0 skipped. Test không thay thế MySQL execution; runtime
  fact 2026-08-09 xác nhận V22 `SUCCESS`, `subject_id` nullable `char(36)`, row count
  rubric giữ 0 và duplicate FK không chặn alteration.
- **REPLAY_FROM_EXTERNAL_V1_BASELINE:** không implement. Với default Flyway
  `outOfOrder=false`, một migration mới version `12.1` xuất hiện khi DB đã ở V18/V21
  sẽ ở trạng thái ignored; validation mặc định không ignore `versioned:ignored`, nên
  compatibility migration này cần task/config decision riêng. **TRUE_EMPTY_DATABASE_BOOTSTRAP**
  là `BLOCKED_EXISTING_BASELINE_GAP` vì V1 legacy không có trong repository.

### Runbook MySQL không chứa secret

```sql
SELECT version, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('10', '13');

SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'rubric_template'
  AND COLUMN_NAME = 'subject_id';

SELECT COUNT(*) FROM rubric_template;
```

Sau khi V22 thực sự được apply, dùng postflight sau và xác nhận `IS_NULLABLE = YES`,
row count không đổi, V10/V13 checksum không đổi:

```sql
SELECT version, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('10', '13', '22');

SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'rubric_template'
  AND COLUMN_NAME = 'subject_id';

SELECT COUNT(*) FROM rubric_template;
```

**Runtime fact 2026-08-09:** postflight production đã xác nhận V19/V20/V21/V22
`SUCCESS`; `semester.deleted_at`/`course.deleted_at` nullable `datetime(6)`;
`lecturer.account_status` `NOT NULL varchar(20) DEFAULT 'ACTIVE'`; rubric subject
nullable `char(36)`, 0 row. Duplicate FK vẫn tồn tại, không được cleanup và không
chặn nullable repair.

## Scope rollback M4B — 2026-08-10

- V23 đã chạy production nên giữ nguyên, bất biến và không tái sử dụng version. Schema
  additive `rubric_template.deleted_at` được phép tồn tại nhưng không còn được code ứng
  dụng sử dụng.
- M4B Admin rubric CRUD, resolver active-only và thay đổi Peer Review/Rubric liên quan
  đã bị gỡ theo ownership. Không thêm reverse migration, không thay scoring, Contribution,
  authorization hay historical data.

## Cập nhật 2026-08-09 — giới hạn Admin Course progress overview M5

- Contract chỉ đọc Course active paged/filter DB và aggregate local current counts. Query dùng
  `COUNT(DISTINCT ...)` một page query, không materialize tất cả Course hoặc gọi provider.
- Sprint tombstone bị loại; rubric tombstone không được join nên không làm thay đổi raw
  PeerReview count. Course tombstone không xuất hiện. Không có field score/grade/completion.
- Assessment có `student`, `lecturer`, `rubric`, `score`, `note` nhưng không status,
  finalized/submitted field hay service/controller application flow; không dùng overview.

## Cập nhật 2026-08-09 — Admin Course report export M6

- Report là XLSX read-only nhiều sheet; Course tombstone bị 404, Sprint/Task tombstone
  bị loại. Repository bulk-load Course details, memberships, Sprints, Tasks và PeerReview
  với entity graph cần thiết; không có N+1 theo Team/member/Sprint/review.
- Không có Assessment/grade, contribution aggregate, review comment hoặc email trong
  workbook. Dữ liệu chỉ là local operational snapshot hiện hữu, không chứng minh lifecycle
  hoàn tất nào.

## Ràng buộc Admin global user import M7 — 2026-08-09

- Endpoint chỉ nhận ADMIN session + CSRF. `role` enum request `STUDENT`/`LECTURER`, không
  lấy role từ spreadsheet; `ADMIN` bị từ chối. Không thêm bearer, Cognito Admin API/group.
- Student schema `studentCode,email,fullName`; Lecturer `email,fullName`. Một XLSX đúng một
  sheet/header exact, required non-blank, không formula/duplicate normalized identity.
  Không tái sử dụng schema/parser Course import.
- Student chỉ reuse khi email/code cùng một Student; Lecturer chỉ reuse khi email là
  Lecturer. Partial/cross-role conflict; reuse không update. New Student PENDING, Lecturer
  ACTIVE. Không tạo Course/Team/TeamMember/invitation/outbox; không entity/migration.

## Ràng buộc Admin active Semester M8A — 2026-08-09

- Không có Semester status/active field, current-date inference hoặc generic settings model. V24 thêm đúng một bảng typed `active_semester_setting`; singleton id `1`, reference Semester nullable, PK/check/FK bảo vệ cardinality và integrity.
- `GET`/`PUT /api/admin/settings/active-semester` là ADMIN browser session. PUT yêu cầu CSRF, request tối thiểu `semesterId`; Semester missing/tombstone là 404. Same Semester được phép lặp deterministic. Không public setting/non-admin và không Bearer.
- Setting không đổi Semester, Course hoặc Course filter. Delete Semester có thêm dependency guard setting và fail closed 409; không cascade/clear. Không provider call, AccountStatus, Rubric, Import, Contribution hay Analytics change.

## Ràng buộc M9 notification broadcast — 2026-08-09

- Không có contract notification hoàn chỉnh: `Notification` mapping JPA không có repository,
  service/controller, HTTP API, producer/consumer, read API, realtime hoặc email delivery.
  `recipientRole` là String; `recipientId` không có association/FK JPA. Không dùng invitation
  outbox cho broadcast.
- Source không chứng minh policy audience `ALL`/role, target account status, lifecycle read/unread,
  retention, rich content hay content-length. Không được tự lọc account, gửi provider/Cognito,
  hay thêm HTML/WebSocket/SSE/email semantics.
- Flyway V1 legacy không nằm trong repository và V2–V24 không quản lý table notification;
  `ddl-auto=validate` không thay schema. Mọi physical-schema/FK production là **TBD**.
- Do đó `POST /api/admin/notifications/broadcast` là **BLOCKED**; migration và entity diff
  bằng 0 trong audit này. Chỉ sau business contract + schema versioned được phê duyệt mới đánh giá
  fanout; nếu cần read state theo user, khuyến nghị master broadcast + receipt per recipient.
- `GENERIC_SYSTEM_SETTINGS = TBD_NOT_IMPLEMENTED_NO_CONFIRMED_GLOBAL_SETTINGS`; không dùng
  key-value/JSON setting cho notification.

## Ràng buộc M10 Support & Diagnostics — 2026-08-09

### A11A durable identity additive

- `SystemAuditLog` mới có `actorLocalProfileId` UUID canonical text nullable và `actorRole`
  nullable. Chỉ `SagaPrincipal`/`AuthenticatedProfile` exact mới set hai field; webhook, system
  và identity-conflict không có local profile/role ghi `null`.
- Không có Mongo backfill, MySQL migration, managed index hoặc API per-user trong thay đổi này.
  `actorId` giữ nguyên Cognito subject; không nhận subject từ FE hay invent mapping/heuristic.

- Integration health chỉ đọc local MySQL qua repository: enabled flag, count/link/state
  Jira/GitHub, Jira stored webhook-id presence, GitHub installation state, receipt state và
  latest persisted sync timestamp. Không dùng token/credential/property secret để xác nhận
  “configured”, không provider network probe và không trả synthetic health score.
- User-scoped audit bị chặn: `SystemAuditLog` lưu `actorId` string là Cognito subject;
  `localProfileId` không phải field durable và chỉ xuất hiện trong payload của một số audit.
  Không nhận subject từ FE, không invent mapping/heuristic hay rewrite Mongo history.
- Không có switch-user/delegated-session/restore/audit contract. Authentication giữ Cognito
  OIDC + browser JSESSIONID + server-side `SagaPrincipal`; không thêm JWT/Bearer/token issue.
- Course không có enrollment entity; relation Student–Course thực tế là
  `Student -> TeamMember -> Team -> Course`. Không thêm/remove Student khi Team, role,
  Project và historical retention chưa được quyết định.

## Ràng buộc I1 Course student import — 2026-08-09

- Contract route không đổi: `POST /api/v1/courses/{courseId}/import-students`, multipart field `file`, success `200` plain text hiện hữu. Session browser + CSRF bắt buộc; ADMIN mọi Course, LECTURER ownership, STUDENT 403, anonymous 401.
- File phải `.xlsx`, không rỗng, không quá 1.048.576 bytes; chỉ sheet đầu tiên được xử lý. Tối đa 1.000 data rows. Header sau trim phải exact-case và đúng thứ tự `Class,RollNumber,Email,MemberCode,FullName,Group,Leader`, không extra/missing; formula ở header, data hoặc ô extra bị reject.
- `RollNumber`, `Email`, `FullName` non-blank; email normalized trim/lower, code trim/upper. File không partial success: duplicate normalized identity, malformed row/file, partial/split identity hoặc Team khác cùng Course chặn toàn transaction trước side effect.
- `Group` trống giữ behavior cũ: chỉ Student provision/reuse, không tạo TeamMember/invitation. Group có giá trị dùng Team `Group {Group}`; `Leader = x` (case-insensitive) là LEADER, còn lại MEMBER. Same Team không rewrite role; Course khác được membership độc lập.
- Preflight bulk Student/Team/membership rồi lock Student khi write; invitation outbox vẫn là luồng existing dedup sau TeamMember và không có provider/Cognito call. Không thay đổi M7 parser/business, entity, schema hay migration. DB invariant trực tiếp Student+Course vẫn chưa có.

## Ràng buộc D1 browser session và deployment — 2026-08-09

- Không được chuyển sang Bearer/JWT/localStorage token hoặc disable CSRF. `GET /api/auth/csrf` cần authenticated session; mutation gửi cookie `XSRF-TOKEN` cùng header `X-XSRF-TOKEN` và `credentials: include`. Webhook exemption vẫn chỉ là hai POST provider route.
- Prod source đặt `server.servlet.session.cookie.secure=${SESSION_COOKIE_SECURE:true}`, `same-site=${SESSION_COOKIE_SAME_SITE:none}` và HttpOnly true. CSRF cookie explicit path `/`, HttpOnly false, Secure/SameSite theo cùng source property. Domain, JSESSIONID path/lifetime/timeout không có setting source; không được coi là confirmed runtime.
- `FRONTEND_ORIGINS` là required public config: phải là origin HTTP(S) explicit, không wildcard/path/query/fragment; CORS cho GET/POST/PUT/PATCH/DELETE/OPTIONS, `Content-Type`, `X-XSRF-TOKEN`, `Idempotency-Key`, credentials=true, exposed `Location`, max-age 3600. `PUBLIC_BASE_URL`, auth redirect và Cognito/OAuth/database/integration config là required theo property/validator tương ứng; secret chỉ được nêu tên, không log/expose giá trị.
- Không có dependency/config Spring Session, Redis, JDBC session hay Hazelcast. `HttpSessionSecurityContextRepository` là in-memory process state; shared session/multi-replica là architectural decision riêng. Railway build hiện skip tests; CI gate không được suy diễn từ build command.

## Ràng buộc J1B Task Create canonical completion — 2026-08-09

## Ràng buộc J1C Jira metadata exact-name-first — 2026-08-09

- Metadata vẫn project-scoped. Dedup provider ID trước resolution; exact canonical provider name
  normalize bằng business enum được ưu tiên chỉ khi đúng một ID. Không có exact thì `exactlyOne`
  semantic fallback; zero/multiple distinct ID vẫn fail-closed với code resolution hiện hữu.
- Metadata failure xảy ra trước Jira create POST. Không đổi explicit `issueTypeId`/`priorityId`
  validation, priority omitted, idempotency/canonical recovery, scope, session/CSRF, entity/schema
  hay migration; không cache cross-project, sort/pick-first hoặc hardcode provider ID.

- `TASK_CREATE` chỉ ghi `COMPLETED` sau Jira POST đã được đánh dấu `REMOTE_SUCCEEDED`, canonical Jira GET/upsert hoàn tất và `TaskRepository.findByProjectIdAndExternalId(projectId, remoteResourceId)` xác nhận Task local để trả response. Upsert dùng transaction mới và `saveAndFlush`; lookup dùng `projectId + externalId`, không có điều kiện soft-delete.
- Canonical fetch, upsert hoặc local confirmation fail sau remote success phải giữ `REMOTE_SUCCEEDED`, giữ remote id/key, trả `JIRA_WRITE_RECOVERY_REQUIRED` khi cần và không chuyển `FAILED`. Retry cùng `Idempotency-Key` chỉ canonical recovery; `COMPLETED` thiếu Task local fail-safe, không POST Jira.
- Không thêm finalize từ reconciliation, hardcode Jira ID, Bearer, metadata policy, entity/schema/migration hay thay đổi authorization/session/CSRF. Runtime DEMO-8/DEMO-9 xác nhận final DB `COMPLETED`, Task local tồn tại, `safe_error_code=NULL`; WARN cũ xuất hiện sau `completed_at` do thứ tự confirmation cũ.

## Ràng buộc Admin users/audit timestamp — 2026-08-09

- `GET /api/admin/users` phải query và đếm chỉ trên Lecturer/Student; không được lấy page gồm Admin rồi lọc trong Java. Keyword, role `STUDENT|LECTURER` và accountStatus chạy trên cùng union. `role=ADMIN` hiện parse được và trả rỗng.
- `GET /api/admin/system-stats` không bị đổi theo user-list contract; vẫn là tổng profile toàn cục.
- `SystemAuditLog.timestamp` là absolute `Instant`; Mongo BSON Date là epoch-millis và Admin audit JSON phải là ISO UTC có `Z`. Không đổi JVM timezone, không cộng +7, không migration/backfill hay reinterpret historical document.

## Ràng buộc J1F TASK_SPRINT finalization — 2026-08-10

- `TASK_SPRINT` chỉ complete sau provider move đã `REMOTE_SUCCEEDED`, canonical Jira issue GET/upsert, apply target Sprint/backlog ở transaction mới và fresh local confirmation đúng target. Không dùng outer `REPEATABLE_READ` snapshot để ghi/đọc confirmation.
- Failure ở canonical GET/upsert/finalization/confirmation sau remote success phải giữ `REMOTE_SUCCEEDED`, remote id/key và không `FAILED`, không success giả, không POST move Jira lần hai. Same key/request chỉ canonical recovery; `COMPLETED` trả Task local deterministic.
- `request_fingerprint` không phải target intent có thể giải mã. Không migration/schema mới trong J1F; recovery nền không complete `TASK_SPRINT` thiếu target, không dùng reconciliation/scheduler làm normal completion.

## Ràng buộc J1D fresh canonical read — 2026-08-10

- Với MySQL production `REPEATABLE_READ`, không được dùng outer orchestration transaction để confirm Task vừa được child canonical upsert `REQUIRES_NEW` commit. Confirmation phải qua bean/proxy transaction mới `REQUIRES_NEW`, `readOnly`.
- `REMOTE_SUCCEEDED` chỉ complete sau fresh local confirmation. Missing Task giữ status đó và trả `JIRA_WRITE_RECOVERY_REQUIRED` ở create; recovery không complete. Cấm global isolation change, EntityManager clear, sleep/polling, blind Jira create retry, new idempotency key hoặc chờ scheduler.
- J1C metadata resolution, scope, auth/session/CSRF, Jira write operation state machine và schema/migration không đổi.
## Student Course Invitation Gmail REST API constraints — 2026-08-11

- Delivery giữ `StudentInvitationDeliveryAdapter` và HTTP stack Spring `RestClient` + JDK client hiện hữu; không còn dependency Spring Mail/JavaMail, SMTP properties hay Mail health contributor.
- Production configuration dùng đúng `GMAIL_API_CLIENT_ID`, `GMAIL_API_CLIENT_SECRET`, `GMAIL_API_REFRESH_TOKEN`, `GMAIL_API_SENDER_EMAIL`, `GMAIL_API_SENDER_NAME` và `STUDENT_INVITATION_LOGIN_URL`. Cả năm biến Gmail là bắt buộc để adapter khả dụng; thiếu một biến phải fail-safe thành unavailable mà backend/import vẫn hoạt động.
- OAuth refresh chỉ POST HTTPS tới `https://oauth2.googleapis.com/token` với `grant_type=refresh_token`. Gmail send chỉ POST HTTPS tới metadata endpoint `https://gmail.googleapis.com/gmail/v1/users/me/messages/send`; quyền tối thiểu cần cấp là `https://www.googleapis.com/auth/gmail.send`.
- Access token chỉ sống trong process, cache thread-safe và refresh trước expiry 60 giây. Không persist/log client secret, refresh/access token, Authorization header, form body, raw MIME hay raw provider response. Request body nhạy cảm được gửi dưới dạng byte array để Spring debug log không serialize nội dung.
- MIME UTF-8 phải có `multipart/alternative`, text + HTML, Base64URL toàn message trong JSON `raw`, FROM gồm configured sender name/email và TO từ message recipient. Header CR/LF injection bị từ chối trước provider call.
- Shared integration connect/read timeout mặc định 3/10 giây, không provider call lúc startup và không live Gmail health probe.
- 429/5xx/network và 403 reason rate/quota được phân loại retryable; invalid grant/client, malformed token response, 400/401 và 403 permission/sender là non-retryable. Outbox schema hiện không lưu retryability: processor vẫn mark mọi delivery failure `FAILED` và scheduler hiện hữu có thể claim lại tới max attempts. Đây là giới hạn có chủ ý của milestone, không phải exactly-once.
- Provider success mới `SENT`; failure không rollback import/membership. At-least-once, stale recovery, max attempts và SENT no-resend giữ nguyên. Production delivery/inbox/spam vẫn **TBD_DEPLOYMENT_SMOKE**.
- Verification: targeted context/Gmail/invitation/import regression **70/70 PASS**. Full suite chạy **822 tests** không có Gmail failure; DEC-023 baseline vẫn fail. Một Jira diagnostic full-order error ngoài scope pass khi rerun riêng và không có source diff liên quan.
## Notification Bell / Firebase Admin runtime constraints — 2026-08-11

- Firebase Admin Java `9.10.0` is required because delivery targets Firebase Installation IDs through `Message.Builder#setFid`; legacy registration-token storage is not introduced.
- Railway/production must supply the service-account fields as separate runtime secrets: `FIREBASE_TYPE`, `FIREBASE_PROJECT_ID`, `FIREBASE_PRIVATE_KEY_ID`, `FIREBASE_PRIVATE_KEY`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_CLIENT_ID`, `FIREBASE_AUTH_URI`, `FIREBASE_TOKEN_URI`, `FIREBASE_AUTH_PROVIDER_X509_CERT_URL`, `FIREBASE_CLIENT_X509_CERT_URL`, and `FIREBASE_UNIVERSE_DOMAIN`.
- Credentials are assembled and decoded by the Google credentials library entirely in memory. `FIREBASE_PRIVATE_KEY` may be a Railway multiline variable; literal `\\n` is normalized to newline. Do not configure Base64 JSON, commit a service-account file, create a temporary credential file, log values, or hardcode a project ID.
- Local development may use `GOOGLE_APPLICATION_CREDENTIALS` pointing outside the repository. ADC fallback is attempted only when that variable is configured. Missing/invalid credentials select an unavailable delivery adapter; application startup and notification DB/API remain functional.
- Notification delivery uses durable `PENDING/PROCESSING/SENT/FAILED` rows, a maximum of five claims, scheduled retry, stale `PROCESSING` recovery, bounded provider timeouts, and safe category-only logs. Notification/read state never depends on FCM success.
- `NOTIFICATION_DELIVERY_PROCESSING_ENABLED` defaults true; `NOTIFICATION_DELIVERY_RETRY_DELAY_MS` defaults 60000; `NOTIFICATION_DELIVERY_PROCESSING_TIMEOUT_MS` defaults 300000. Tests disable the processor and use mocks only.
- Admin broadcast remains outside this contract and BLOCKED. The only producer is a new grouped-import `TeamMember`; it cannot redefine roster/enrollment/grouping/DEC-023/Cognito/session semantics.
- Source/test evidence: targeted Notification/Firebase **16/16 PASS**; full **122 suites / 769 tests / 1 failure / 0 errors / 0 skipped**. The only failure is **PREEXISTING_BASELINE_SOURCE_CONFLICT_WITH_DEC_023**, not a notification/Firebase regression.
## Notification broadcast and producer constraints — 2026-08-11

- V26/V27 are additive after V25: `notification_broadcast` provides sender/audience/content/idempotency/status counters; `user_notification.broadcast_id` provides broadcast recipient dedup and nullable `event_key` provides event replay dedup. Do not edit V25 or legacy `notification`.
- Broadcast requires the existing `Idempotency-Key` header convention. The sender-role-key fingerprint is immutable: same intent replays safely, changed intent conflicts. Fanout fetches IDs in bounded 200-row pages and never makes provider calls in the HTTP transaction.
- Admin audiences: STUDENTS, LECTURERS, ALL_USERS. ALL_USERS is Student + Lecturer, with ADMIN inclusion explicitly TBD. No AccountStatus filtering is allowed until Product decides it.
- Lecturer Course audience is distinct TeamMember Students of all requested Courses after every Course ownership check succeeds. StudentCourseInvitation is prohibited as audience/enrollment evidence.
- Verified personal Jira/GitHub identity mappings and verified project Jira-board/GitHub-installation links have one confirmed automatic recipient: the initiating actor. The historical Task/Sprint TBD in this paragraph is superseded by DEC-071 and the constraints below.
- Broadcast title/message are trimmed, nonblank, max 160/1000, text-only; FID/provider/recipient identity and raw user content must not appear in response or logs.

## Jira mutation notification constraints — 2026-08-11

- Producer runs only after persisted COMPLETED Jira write operation, uses existing DB notification/FID delivery, and never calls FCM directly.
- Date-only deadline scan uses `JIRA_TIME_ZONE`, excludes deleted/null-due/DONE/CANCELLED, and reuses V27 semantic event dedup with task due-date revision.
- Task mutation recipient is the canonical assignee, or owning TeamMember Students only when canonical assignee is null. Sprint mutation recipient is owning TeamMember Students. Exclude the actor only when the actor is a Student recipient; do not infer Lecturer/Admin Team membership and do not add AccountStatus filtering.

## Student account lifecycle V2 constraints — 2026-08-14

- Account status and Course membership must remain independent. `PENDING` is limited to an imported/pre-provisioned placeholder that has not completed an accepted authenticated identity bind. Successful accepted STUDENT authentication must create or return `ACTIVE` regardless of TeamMember presence.
- Import-first must create an unlinked `PENDING` Student, create/reuse TeamMember and enqueue the invitation. Exact verified email + extracted student-code first login must bind and activate that same row under the existing pessimistic identity lock while retaining all TeamMember roles and Course memberships.
- Login-first with no local identity match must create an `ACTIVE` Student with the authenticated subject and no TeamMember. Later exact Course provisioning must reuse the same Student, preserve ACTIVE, create/reuse TeamMember and enqueue the invitation. Authentication must never create Course membership.
- Existing-subject recovery must row-lock the same Student and change only historical `PENDING + cognitoSub` to ACTIVE; no TeamMember is required. ACTIVE remains unchanged. INACTIVE and SUSPENDED must not auto-reactivate during authentication or provisioning. Partial/split, ambiguous and cross-profile identity remains a conflict.
- Course membership authority remains `Student -> TeamMember -> Team -> Course`; do not add enrollment status/entity/state. Invitation is informational only and never identity, account activation or enrollment truth; no click or Admin PATCH is required for the normal flow.
- No Cognito Lambda, group/role classification, browser session/CSRF, account-status enforcement, TeamMember model or `CourseService` behavior change is permitted.
- `JiraWriteOperation.id` plus `NotificationType` is the durable mutation event identity. The V27 unique key combines recipient profile/role with `event_key`, so producer replay/concurrency cannot create a second Bell row. Raw `Idempotency-Key` is never placed in the event key or logs.
- `REMOTE_SUCCEEDED`, FAILED, UNKNOWN, reconciliation, and webhook snapshots do not create mutation success notifications. Start/Close require canonical Sprint state `active`/`closed` before completion and notification.
- Jira due-date semantic is date-only: public request DTOs use `LocalDate`; canonical parser and legacy `Task.dueDate`/DB column store start-of-day `LocalDateTime`. Do not infer a due instant or 3-hour/24-hour alert. Only `TASK_DUE_TOMORROW`, `TASK_DUE_TODAY`, and `TASK_OVERDUE` are allowed.
- Deadline semantic identity is recipient ownership columns plus `task:{taskId}:due:{yyyy-MM-dd}:type:{NotificationType}`. A due-date revision may create a new reminder; restart, rerun, or concurrent scans cannot duplicate the same revision/type/recipient.
- Deadline scan is bounded to 100 rows per page and configured by `NOTIFICATION_DEADLINE_PROCESSING_ENABLED` (default true) and `NOTIFICATION_DEADLINE_SCAN_DELAY_MS` (default 3600000). One Task exception must not stop the batch. The scheduler uses injected `Clock` and `JIRA_TIME_ZONE`, never JVM default date semantics.
- Bell notification and durable delivery rows are committed before the existing after-commit Firebase processor runs. Zero FIDs still yields a Bell notification; multiple active FIDs yield multiple deliveries, not multiple Bell rows. Producer/FCM failure cannot change Jira `COMPLETED` or roll back canonical Task/Sprint state.
- No automatic-notification public API exists, no request accepts actor/recipient, and `actionUrl` is null until a canonical internal FE route is confirmed. DEC-023 and Course roster/enrollment/invitation semantics remain unchanged.
## GitHub Issue traceability constraints — 2026-08-11

- V28 tạo `task_git_issue_link`, `git_issue_pull_request_link`, `git_issue_commit_link`; mỗi relation
  unique theo pair, FK dùng `CHAR(36)` convention. Không sửa migration deployed và không drop legacy
  `pull_request.git_issue_id`/`commit_data.git_issue_id`. Trước deploy chạy read-only
  `docs/integrations/mysql-traceability-v28-preflight.sql` để xác nhận bốn legacy table có PK
  `CHAR(36)`, UUID charset/collation đồng nhất, table engine tương thích FK, database defaults mà V28
  sẽ kế thừa khớp UUID columns, và tên table/constraint V28 chưa va chạm. Source/test status là
  **CONFIRMED_SOURCE_TEST + REQUIRED_RUNTIME_MYSQL_PREFLIGHT**; chỉ deploy V28 nguyên trạng khi
  `V28_PREFLIGHT_READY=PASS`, mismatch thì dừng trước Flyway V28 và dùng exact runtime metadata để quyết định.
- Task–Issue link/unlink phải gọi manager authorization trước mutation, lock Task theo project và fail
  `TRACEABILITY_PROJECT_MISMATCH` nếu Task/Issue không cùng URL Project. Duplicate POST replay safe;
  DELETE missing pair vẫn 204 sau khi resource/scope được validate. Không provider write.
- Issue list tối đa 100/page, deterministic `externalUpdatedAt, issueNumber, id` descending; filter
  `state`, exposed repository ID, title/number keyword và Student `assignedToMe`. Counters dùng DB
  count trong project/repository scope, không full-table materialization.
- Issue/detail/traceability response chỉ trả local UUID và UI-safe snapshot. Cấm raw
  `githubIssueId`, `nodeId`, author/assignee external ID, installation, token/credential.
- Task/Issue link collections và timeline tối đa 100. Project timeline fetch bounded per source rồi
  merge bounded; timestamp null bị loại. Cấm dùng local `createdAt` làm GitHub created time.
- Current provider không chứng minh PR–Issue/Commit–Issue relation. Không infer từ title/message;
  normalized tables là seam, auto-sync relation **PARTIAL**. GitHub Issue remote CRUD, notification và
  Contribution vẫn **NOT_IMPLEMENTED**. DEC-023/CourseService không đổi.
## Contribution read authorization constraints — 2026-08-12

- Exact route là `GET /api/v1/teams/{teamId}/contribution-evaluation`; không tạo route mới.
- Principal chỉ lấy từ authenticated `SagaPrincipal`; identity authority là `localProfileId`.
  ADMIN đọc mọi Team. LECTURER chỉ khi `team.course.instructor.id` bằng local profile ID.
  STUDENT chỉ khi repository xác nhận exact `teamId + studentId + RoleInTeam.LEADER`.
- MEMBER, MENTOR, Student không membership và Leader Team khác phải 403. Team thiếu giữ 404;
  anonymous 401. Không pick membership đầu tiên hoặc cấp quyền chỉ vì cùng Course/Project.
- Response được phép giữ nguyên sau privacy audit; cấm bổ sung email, Cognito subject,
  reviewer/comment, token, credential, secret hoặc raw Jira/GitHub payload cho Leader.
- Đây là read-only grant. Không mở override/slice-weight/Peer Review mutation và không sửa bất kỳ
  arithmetic, normalization, evidence, warning hay current-aggregate semantic nào.
## J1K Jira Task Issue Type Update constraints — 2026-08-13

- `PUT /api/v1/projects/{projectId}/tasks/{taskId}` may accept optional business `type` only as SAGA `TaskType`; `REQUEST` is an exact business value. Public/normal clients must not send `issueTypeId` or any Jira provider ID.
- Edit authority is only `GET /rest/api/3/issue/{issueIdOrKey}/editmeta` for the exact current issue and field `issuetype`. Missing/non-editable field fails `400 JIRA_EDIT_FIELD_NOT_ALLOWED` before provider PUT. Create metadata, hardcoded/project IDs, cross-project cache, sort/pick-first and guessing are forbidden.
- Resolution must normalize candidates, deduplicate provider IDs, prefer exactly one canonical exact-name match, then allow semantic fallback only for exactly one distinct provider ID. Zero and multiple distinct IDs fail closed with the existing issue-type resolution taxonomy. Build the entire sparse field map before a single provider PUT so mixed validation failure cannot partially mutate Jira.
- Same canonical business type must be suppressed; an otherwise empty update keeps `JIRA_TASK_UPDATE_EMPTY`. Provider payload contains only the resolved ID under `fields.issuetype`; request/provider values, IDs, raw metadata/payload/response, credentials and Idempotency-Key must not be logged or exposed.
- Fingerprint must contain raw business `type`, never resolved Jira ID. After remote success, canonical GET/upsert/fresh local read must confirm `Task.type == requested type` before `COMPLETED`. Failure/mismatch stays `REMOTE_SUCCEEDED`; same-key replay only recovers canonical state. Background recovery must not complete `TASK_UPDATE` without readable target intent and must never replay provider mutation.
- EPIC/SUBTASK hierarchy crossing is fail-closed; do not call Move Issue or rewrite parent/hierarchy. Assignee, Sprint, Estimation, Transition, Delete, authorization/scopes, browser session, CSRF, CORS and required `Idempotency-Key` remain unchanged. No migration, Bearer, or `CourseService` change.
# M5 internal AI context constraints (2026-08-14)

- Backend is the sole authoritative source of Project/GitRepo/CommitData/Task/GitIssue context. AI must not query business tables or receive GitHub/Jira credentials.
- Internal commit-review input is exact Project UUID + GitHub provider repository ID + full 40-hex SHA. Cross-project/repository/commit requests fail closed.
- Service authentication is separate from browser security: env `SAGA_AI_SERVICE_TOKEN`, property `app.internal-ai.service-token`, header `X-SAGA-AI-Service-Token`, scoped only to `/internal/ai/**`. Browser `JSESSIONID`, CSRF, CORS, and no-Bearer contracts remain unchanged.
- Traceability authority is normalized `GitIssueCommitLink` and `TaskGitIssueLink` only. Missing links are `NOT_PROVEN`; no text/key/number/AI inference is permitted.
- Backend may fetch exact commit detail only with the existing Backend-owned GitHub client. Response is a dedicated versioned DTO, excludes credentials/personal session data, and is bounded to 50 files, 20,000 patch characters per file, and 100,000 total context characters with explicit truncation metadata.
- M5 adds no business migration, public AI review API, n8n flow, webhook trigger, or automatic push review.
- M5 targeted verification passes 47/47. Full clean runs 909 tests with only the four previously documented baseline failures (OpenAPI 131/133, DEC-023 Course roster, two Lecturer Analytics assertions); the internal endpoint is hidden from browser OpenAPI.

## Student Team graph read constraints — 2026-08-14

- Scope is exactly four GET routes: Team `overview`, Team `heatmap`, per-Team Student `interactions`, and Team Sprint `burndown`. Do not grant STUDENT to other Lecturer Analytics operations except the later DEC-083 `/progress` contract (MEMBER self / LEADER exact Team).
- A reusable graph-read guard must validate Course→Team, then allow ADMIN; allow LECTURER only when `Course.instructor.id == SagaPrincipal.localProfileId`; allow STUDENT only when an exact `TeamMember(teamId, localProfileId)` has `roleInTeam` equal to `LEADER` or `MEMBER`. `MENTOR` is not included.
- Do not accept caller identity from request parameters, body, headers, or target `studentId`. The target Student/heatmap filter must be in the exact Team; Sprint must be in the exact Team Project. Cross-scope identifiers fail closed with existing 403/404 semantics.
- Keep `AccountStatusEnforcementFilter`, `SecurityConfig`, browser session/no-Bearer, GET/no-CSRF, response DTOs, graph calculations, and `CourseService` unchanged.

## SAGA AI Agent V1 constraints — 2026-08-14

- Public Agent routes require an authenticated `SagaPrincipal`; unsafe routes require the existing CSRF token. Never accept actor/profile/role authority from browser JSON and never expose either directional service token to FE.
- `SAGA_BACKEND_TO_AI_SERVICE_TOKEN` must be a distinct strong secret for Backend→AI. `SAGA_AI_SERVICE_TOKEN` remains AI→Backend. Backend Agent timeouts are bounded (`PT3S` connect and `PT130S` read by default) for possible Hugging Face cold start; no automatic Jira mutation retry is added.
- Actor delegation is random opaque material with only SHA-256 persisted. It must be bound to conversation, exact current profile/role audit identity, capability, and TTL no longer than 15 minutes. Service auth alone is insufficient for actor-scoped tools.
- Internal Agent controllers expose exact typed POST routes only. Each route resolves delegation and reuses domain services; no generic path/method/body proxy, JPA entity dump, or authorization copy in Python is allowed.
- Task proposals are bounded and immutable. Confirmation owns no mutable request body, claims once, reauthorizes through existing task write service, and preserves its stable idempotency key. Update excludes assignee/Sprint/estimation/status dedicated operations.
- SRS projection is bounded to 100 tasks and 50 traceability events with explicit truncation metadata. Artifact download must verify current Project access, safe `.docx` filename, media type/size, and proxy content; possession of an artifact UUID is not authority.
- V30 belongs to Flyway because delegation is Backend-owned authorization state. All conversation/tool/action/artifact rows belong to AI Alembic; no cross-schema business FK is permitted.
