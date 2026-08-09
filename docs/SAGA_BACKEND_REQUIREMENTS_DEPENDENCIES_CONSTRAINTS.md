# SAGA Backend — Yêu cầu, Dependency, Phân quyền và Ràng buộc

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
- Student email phải trích xuất được student code; Student mới có `AccountStatus.PENDING`. `AuthenticatedProfileService#extractRequiredStudentCode`, `#create`.
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

**Danh sách đầy đủ 62 tên biến placeholder đã quét** (các nhóm trong bảng trên không dùng wildcard để tạo thêm tên): `AIVEN_DB_PASSWORD`, `AIVEN_DB_USERNAME`, `AIVEN_JDBC_URL`, `AUTH_LOGOUT_REDIRECT_URI`, `AUTH_SUCCESS_REDIRECT_URI`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, `COGNITO_DOMAIN`, `COGNITO_ISSUER_URI`, `DATABASE_JDBC_URL`, `DATABASE_PASSWORD`, `DATABASE_USERNAME`, `FLYWAY_BASELINE_ON_MIGRATE`, `FLYWAY_ENABLED`, `FRONTEND_ORIGINS`, `GITHUB_API_BASE_URL`, `GITHUB_APP_ID`, `GITHUB_APP_SLUG`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_INTEGRATION_ENABLED`, `GITHUB_PERSONAL_CALLBACK_URL`, `GITHUB_PRIVATE_KEY`, `GITHUB_PROJECT_CALLBACK_URL`, `GITHUB_SETUP_URL`, `GITHUB_WEB_BASE_URL`, `GITHUB_WEBHOOK_PUBLIC_URL`, `GITHUB_WEBHOOK_SECRET`, `INTEGRATION_HTTP_CONNECT_TIMEOUT`, `INTEGRATION_HTTP_READ_TIMEOUT`, `INTEGRATION_OAUTH_STATE_TTL`, `INTEGRATION_OVERLAP_WINDOW`, `INTEGRATION_RECONCILIATION_DELAY_MS`, `INTEGRATION_RECONCILIATION_ENABLED`, `INTEGRATION_TOKEN_ENCRYPTION_KEY`, `INTEGRATION_TOKEN_ENCRYPTION_KEY_ID`, `INTEGRATION_TOKEN_ENCRYPTION_PREVIOUS_KEYS`, `JIRA_API_BASE_URL`, `JIRA_AUTHORIZATION_URL`, `JIRA_CALLBACK_URL`, `JIRA_CLIENT_ID`, `JIRA_CLIENT_SECRET`, `JIRA_INTEGRATION_ENABLED`, `JIRA_SCOPES`, `JIRA_TIME_ZONE`, `JIRA_TOKEN_URL`, `JIRA_WEBHOOK_PUBLIC_URL`, `LOCAL_DEMO_LEADER_COGNITO_SUB`, `LOCAL_DEMO_SEED_ENABLED`, `LOCAL_WEBHOOK_BASE_URL`, `MONGO_DATABASE`, `MONGO_HEALTH_TIMEOUT`, `MONGO_URI`, `PORT`, `PUBLIC_BASE_URL`, `SESSION_COOKIE_SAME_SITE`, `SESSION_COOKIE_SECURE`, `SPRINGDOC_API_DOCS_ENABLED`, `SPRINGDOC_SWAGGER_UI_ENABLED`, `STALE_SYNC_JOB_RECOVERY_DELAY_MS`, `SWAGGER_ENABLED`, `SYNC_JOB_STALE_AFTER`.

| Property cố định/không lấy từ env | Profile | Giá trị/ràng buộc thực tế | Bằng chứng |
| --- | --- | --- | --- |
| `spring.config.import` | all | optional `.env` và `.env.local` properties | `application.properties:L1` |
| OAuth registration | all | provider Cognito, `authorization_code`, redirect template, scope `openid,email,profile`, user-name `sub` | `application.properties:L12-L21` |
| HTTP/error/session | all | cookie session HttpOnly; không include message/stacktrace/binding errors | `application.properties:L23-L24` |
| Actuator/Mongo health | all | chỉ expose health, no details, Mongo contributor disabled | `application.properties:L25-L28` |
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
| HIGH | Validation spreadsheet còn dựa magic columns; production email provider chưa có. | Có thể nhận dữ liệu không đúng contract hoặc không gửi được invitation. | `ExcelImportService`; invitation adapter | RECOMMENDED: header/schema/error DTO và provider configuration trước production. |
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
| Email provider | TBD | Adapter abstraction/fake test có; không có provider/dependency production trong source. |
| Import parser/DB invariant | PARTIAL | Header/schema, preview, error DTO từng dòng và database invariant trực tiếp `UNIQUE(student_id, course_id)` chưa có. |
| Swagger CSRF | CONFIRMED | `withCredentials`; cookie `XSRF-TOKEN`; global same-origin interceptor chỉ POST/PUT/PATCH/DELETE gắn `X-XSRF-TOKEN`; không Bearer. |
| Logout | CONFIRMED | Framework-managed `POST /api/auth/logout`; valid CSRF 302 Cognito, missing/invalid 403; Swagger fetch có thể `Failed to fetch` khi redirect cross-origin. |
| Team roster | CONFIRMED | Paged TeamMemberResponse, ADMIN/Lecturer owner/Student exact-Team policy; 401/403/404 và không email/cognitoSub/version. |
| Course roster | PARTIAL | `TeamMember -> Team -> Course`; filter/sort trước pagination, stable id tie-break, query invalid 400. `studentsWithoutTeam` rỗng vì chưa có enrollment Student–Course độc lập; invitation outbox không phải enrollment. |
| Lecturer options | CONFIRMED | ADMIN-only; keyword fullName/email, sort fullName/email, không tìm/trả cognitoSub; invalid query 400. |
| One Team per Student/Course | CONFIRMED application guard | Excel import lock `PESSIMISTIC_WRITE` Student rồi query Student+Course; same Team idempotent, Team khác 409, khác Course hợp lệ. Test hai thread/hai transaction xác nhận đúng một membership. DB invariant trực tiếp vẫn PARTIAL. |

Configuration mới: `app.student-invitation.login-url` lấy từ `STUDENT_INVITATION_LOGIN_URL` (phải là absolute HTTP(S)); `app.student-invitation.retry-delay-ms` từ `STUDENT_INVITATION_RETRY_DELAY_MS`; `app.student-invitation.processing-timeout-ms` từ `STUDENT_INVITATION_PROCESSING_TIMEOUT_MS`. Không hard-code localhost/Railway, không dùng callback URL làm điểm bắt đầu login và không lưu secret.

Runtime fact do người dùng cung cấp: Railway từng fail vì DB thiếu `student.version`. V6/V7 phải chạy trước Hibernate `validate`; repository không có production log/dashboard nên migration production vẫn **TBD**, không CONFIRMED.

Full `./mvnw.cmd test` tại checkpoint hiện tại: **70 suites, 299 tests, 0 failures, 0 errors, 0 skipped**. Jira/GitHub/webhook, sync UTC serialization, GitHub claim/concurrency/stale recovery, session/CSRF/OIDC callback, master-data authorization và import authorization đều pass.
## Lecturer Analytics constraints — 2026-08-05

- Read-only GET; ADMIN mọi Course, LECTURER instructor-only, STUDENT forbidden.
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

## Cập nhật 2026-08-09 — ràng buộc Admin global rubric M4B

- `rubric_template.deleted_at` do V23 bổ sung nullable; row cũ mặc định active.
  Migration additive, không seed, cleanup FK hay thay migration cũ; production V23 **CONFIRMED**.
- Mutation admin chỉ cho global active rubric. Missing/tombstone trả 404;
  subject-specific bị từ chối có kiểm soát và không mutate; fifth active global
  bị conflict. Security là `ROLE_ADMIN` + session/CSRF, không bearer.
- Current-form resolver loại tombstone cho cả global và Subject; global active
  non-empty có precedence. Entity không có global filter nên historical
  `PeerReviewDetail`/`Assessment` vẫn giữ reference rubric tombstone.
- Không thay đổi `PeerReviewRequest @Size(max=4)`, scoring, Contribution, AccountStatus,
  Course, Import, Jira/GitHub hay authorization Peer Review.

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
