# SAGA — Trạng thái hiện tại

## 1. Thông tin checkpoint

| Mục | Giá trị |
|---|---|
| Branch được audit | `main` |
| Commit được audit | `200d866` (`cập nhật doc`); `07ffa38` là checkpoint lịch sử Privacy |
| Ngày cập nhật | 2026-08-04 (Asia/Saigon, UTC+07:00) |
| Working tree hiện tại | Sáu tài liệu đang được đồng bộ; Jira labels/components/description, V8/V9, Contribution engine và tests đã thuộc lịch sử Git (`b9968dc`/`200d866`) |
| Phạm vi thay đổi của task | Source/config/test ở HEAD và working tree là bằng chứng mạnh nhất |

## Update 2026-08-04 — Contribution engine and Jira task snapshots

- **CONFIRMED:** V9 stores a nullable canonical Jira description and JSON
  component snapshot (`id`/`name`) on `Task`. Search requests these fields and
  upsert replaces them; labels retain their existing V8 replace-all behavior.
- **CONFIRMED:** internal read-only Contribution calculation implements scores
  from mapped commits, Documents, DONE Tasks and PeerReview, with `BigDecimal`.
  Null Task story point contributes one. Results are calculated on demand and
  are not persisted.
- **PARTIAL:** existing Jira story-point and sprint source fields are hard-coded
  tenant ids. No configurable discovery was found, so portability is blocked.
- **TBD:** peer config precedence, persisted contribution overrides, specified
  final-distribution edge cases, classification rules and Contribution API actor
  policy.
- **Verification:** targeted Jira/persistence/repository/calculation tests pass:
  5 suites, 33 tests, 0 failures/errors/skips. Full Maven working tree passes
  60 suites, 278 tests, 0 failures, 0 errors and 0 skipped.

## 2. Đã hoàn thành

- **CONFIRMED:** Spring Security OAuth2/OIDC với Cognito, browser session `JSESSIONID`, profile local và role mapping đã có implementation. Evidence: `SecurityConfig#securityFilterChain`, `CognitoAuthenticationSuccessHandler#onAuthenticationSuccess`, `AuthenticatedProfileService#synchronize`.
- **CONFIRMED:** `/api/auth/login`, `/api/auth/me`, `/api/auth/csrf` và logout Spring Security tồn tại. Evidence: `AuthController`, `SecurityConfig#securityFilterChain`.
- **CONFIRMED:** application roles là `ADMIN`, `LECTURER`, `STUDENT`; team roles là `LEADER`, `MEMBER`, `MENTOR`. Evidence: `ApplicationRole`, `RoleInTeam`.
- **CONFIRMED:** master-data Class/Course/Subject/Semester có API read/create; create được bảo vệ bằng ADMIN. Evidence: bốn controller master-data và `@PreAuthorize`.
- **CONFIRMED:** Jira và GitHub có code OAuth/App, linking, webhook, sync/backfill, reconciliation và encrypted secret handling. Evidence: `integration/callback`, `integration/project`, `integration/provider`, `integration/webhook`, `integration/sync`, `IntegrationSecretCipher`.
- **CONFIRMED:** MySQL/JPA là store domain chính; MongoDB lưu `SystemAuditLog`. Evidence: `application.properties`, entities/repositories.
- **CONFIRMED:** `GET /privacy` public cho anonymous và mọi role, trả HTML UTF-8 từ `static/privacy.html`; exact matcher trong `SecurityConfig` không mở wildcard, không đổi OAuth/session/CSRF/CORS. Contact public được validate từ `app.privacy.contact-url` / `PRIVACY_CONTACT_URL`; deploy phải cấu hình URL contact thực. Evidence: `PrivacyPolicyController`, `SecurityConfig`, `PrivacyPolicyIntegrationTest`.
- **CONFIRMED:** Jira issue labels được fetch/parse thành immutable snapshot, persist `Task.labels_json` TEXT JSON và replace-all khi Jira issue cập nhật; missing legacy DB value đọc thành empty list. Webhook vẫn dùng shared reconciliation. Không có Label entity, Task HTTP API, frontend labels API hay SAGA→Jira task create/update. Evidence: `JiraProviderClientImpl`, `JiraIssueSnapshot`, `JiraIssueUpsertService`, `Task`, V8, labels tests.
- **PARTIAL:** import Excel sinh viên có authorization course scope, transaction rollback, identity bind an toàn, invitation outbox và application guard một Student/một Team/mỗi Course. Parser/header-preview/error DTO và database invariant trực tiếp Student+Course chưa hoàn chỉnh. Evidence: `CourseController#importStudents`, `CourseImportAuthorizationService`, `ExcelImportService#importStudentsToCourse`, `AuthenticatedProfileService`, `StudentInvitationOutboxService`.

## 3. Đã kiểm chứng

| Hạng mục | Cách kiểm chứng | Kết quả |
|---|---|---|
| Import authorization integration test | `-Dtest=CourseImportSecurityIntegrationTest test` | 13 tests, 0 failures/errors/skips; `BUILD SUCCESS` |
| Existing Security integration test | `-Dtest=SecurityIntegrationTest test` (three repeated runs) | 13 tests/run, all pass |
| Maven test suite (working tree hiện tại) | `./mvnw.cmd test` | 60 suites, 278 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS` |
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
ĐÃ KIỂM CHỨNG: full Maven tại checkpoint hiện tại 60 suites / 278 tests / 0 failures / 0 errors / 0 skipped; targeted self-team/roster/security regression là checkpoint lịch sử.
ĐANG LÀM: Cập nhật tài liệu checkpoint theo source/test tại HEAD.
CHƯA LÀM: Import production-ready validation/provider delivery; browser E2E localhost→Railway; session scaling/redeploy verification.
VẤN ĐỀ ĐANG MỞ: Import validation/identity; third-party cookie; error contract; session store; hạ tầng Cognito/Railway còn TBD.
BƯỚC TIẾP THEO: Chốt parser/error DTO, policy email exposure và provider email, chạy E2E cookie/CSRF.
BASE HEAD: `200d866`. Endpoint Student self-scoped đã được commit tại `250f514`; không phải dirty working-tree state.
```

## Update 2026-08-04 — OAuth completion callback redirect

- **CONFIRMED:** Jira common, personal GitHub, project GitHub and GitHub provider-alias completion callbacks return `302` to `app.integration.callback-redirect-uri` with only `resultId`.
- **CONFIRMED:** Safe callback summaries are in current HTTP session for `app.integration.callback-result-ttl` (default `PT5M`), bounded to ten and consumed once by authenticated, CSRF-protected POST. Invalid/missing/replayed state still fails closed.
- **TBD:** Browser E2E confirmation for cross-site cookie and multi-instance session behavior.

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
- **Verification hiện tại:** full Maven suite tại checkpoint hiện tại: **60 suites, 278 tests, 0 failures, 0 errors, 0 skipped**. Không thay đổi Jira, GitHub, OAuth callback, role priority, session hay import authorization.
