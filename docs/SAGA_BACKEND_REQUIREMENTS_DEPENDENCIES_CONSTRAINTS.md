# SAGA Backend — Yêu cầu, Dependency, Phân quyền và Ràng buộc

> **Trạng thái audit:** CONFIRMED = được code hiện tại chứng minh; PARTIAL = mới có một phần code/mô hình; TBD = repository không đủ bằng chứng; RECOMMENDED = đề xuất, không phải hành vi hiện tại. Audit dựa trên branch `main`, HEAD `d855313` (`sửa lỗi phân quyền`) ngày 2026-08-02; application code working tree sạch và chỉ bốn tài liệu audit còn thay đổi chưa commit. Không dùng tài liệu cũ làm bằng chứng chính và không chép giá trị bí mật.

## 1. Mục đích tài liệu

Tài liệu này dành cho Backend, Frontend và QA để truy vết yêu cầu thực tế về code, dependency, API, phân quyền và vận hành. Phạm vi là toàn bộ source hiện có: Spring Boot, Lambda, cấu hình, migration, test và Railway. Mọi kết luận chỉ xuất phát từ executable code/config/test; khi code không chứng minh được, tài liệu ghi TBD thay vì suy đoán.

## 2. Tổng quan hệ thống

SAGA là backend Spring Boot quản lý dữ liệu học phần/lớp/học kỳ/course, định danh người dùng Cognito, team/project và tích hợp Jira Cloud/GitHub App. MySQL/JPA lưu dữ liệu nghiệp vụ; MongoDB lưu audit log.

| Trạng thái | Module thực tế |
| --- | --- |
| **ĐÃ TRIỂN KHAI** | Cognito OIDC session login/profile provisioning; Subject/Class/Semester/Course Create+Read; course student-import authorization; personal identity mapping; review mapping; tạo team project; Jira/GitHub project integration; webhook có xác thực; backfill, reconciliation, sync job và audit log. |
| **PARTIAL** | Entity cho assessment, rubric, CAM, AI log, document, meeting, notification, peer review, sprint/task đã có nhưng không tìm thấy controller/service HTTP hoàn chỉnh cho các module này. Master data chưa có Update/Delete. |
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
| CSRF | Cookie CSRF áp dụng mutation; chỉ `/api/webhooks/**` được miễn. |

Phân biệt lỗi: route protected chưa đăng nhập → 401; URL/method role denial → 403; có role nhưng không owner/member/leader → `IntegrationException` 403; resource không có thường 404 hoặc integration validation 400; conflict 409; OIDC identity invalid 422; provider/profile failure 502; CSRF thiếu/sai bị Spring Security từ chối trước controller (403).

Bằng chứng: `SecurityConfig.java:L74-L153`; `GlobalExceptionHandler.java`; `IdentityMappingService.java:requireStudent`; `ProjectIntegrationAuthorizationService.java:requireTeamManager`.

## 6. Ma trận phân quyền API đầy đủ

Quy ước: `AUTHENTICATED` = chỉ cần session; `SCOPED` = phụ thuộc ownership/membership; `PROVIDER` = external provider có xác thực; `—` = không áp dụng. Mutation trừ webhook yêu cầu CSRF theo `SecurityConfig.java:L74-L77`.

| HTTP Method | Path | Controller Method | Public/Auth Required | ADMIN | LECTURER | STUDENT | Ownership/Membership Rule | Team Role Rule | CSRF Required | Request DTO | Response DTO | Status Codes | Bằng chứng |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
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
| POST | `/api/v1/courses/{courseId}/import-students` | `importStudents` | AUTHENTICATED | YES | SCOPED | NO | ADMIN mọi Course; LECTURER phải có `localProfileId == course.instructor.id`; Course thiếu 404 | — | YES | multipart `file` | `String` | 200,401,403,404,500 | `CourseController#importStudents`; `CourseImportAuthorizationService#requireImportAccess` |
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
| GET | `/`, `/index.html`, `/favicon.ico`, `/assets/**`, `/css/**`, `/js/**`, `/images/**` | static resource | PUBLIC | YES | YES | YES | static matcher | — | NO | — | static content | 200,404 | `SecurityConfig.java:L93-L104` |
| POST | `/api/auth/logout` | Spring Security logout | AUTHENTICATED | YES | YES | YES | session hiện tại | — | YES | — | redirect | 302,403 | `SecurityConfig.java:L143-L153` |
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
| Authorization | Bốn POST có `@PreAuthorize(ADMIN)` |
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
| Main behavior | Tạo/reuse Student theo student code, Team `Group n` và TeamMember; import lặp không tạo duplicate membership theo team+student |
| Authorization | ADMIN mọi Course; LECTURER có `localProfileId == Course.instructor.id`; STUDENT bị 403 |
| Validation / failure | Anonymous 401; thiếu CSRF/không đủ scope 403; Course thiếu 404; exception trong transaction rollback. Header/schema/identity provisioning còn PARTIAL |
| Implementation status / evidence | PARTIAL — `CourseImportAuthorizationService`, `ExcelImportService`, `CourseImportSecurityIntegrationTest` (12 cases pass) |

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
- Jira sync dùng overlap/cursor và time zone config; GitHub/Jira external id hỗ trợ idempotent upsert. `JiraSyncWindow`; `GitHubDataUpsertService`; migration V2.
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
- CORS: explicit origins, `allowCredentials=true`, methods GET/POST/PUT/PATCH/DELETE/OPTIONS; FE cross-origin phải `credentials: "include"`.
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
| HIGH | Import tạo Student `PENDING` không có Cognito subject; validation spreadsheet còn dựa magic columns. | Có thể conflict identity hoặc nhận dữ liệu không đúng contract. | `ExcelImportService` | RECOMMENDED: chốt provisioning/validation contract trước production. |
| MEDIUM | Import duplicate guard theo application check, chưa thấy database unique constraint team+student. | Concurrent requests vẫn cần kiểm chứng. | `ExcelImportService`; `TeamMemberRepository` | RECOMMENDED: cân nhắc unique constraint và concurrency test. |
| MEDIUM | Master-data GET mở cho mọi authenticated Student. | Có thể lộ dữ liệu ngoài scope nếu policy muốn hạn chế. | `SecurityConfig:L119`; controller GET | RECOMMENDED: xác nhận scope nghiệp vụ. |
| MEDIUM | Project integration dựa service checks, không annotation. | Endpoint mới có thể bypass nếu gọi service sai. | `ProjectIntegrationController`; permission service | RECOMMENDED: bổ sung negative tests/guard pattern. |
| MEDIUM | Swagger session cookie không tự xử lý CSRF. | Mutation test khó và dễ nhầm 403. | `OpenApiConfig`; `SecurityConfig` | RECOMMENDED: hướng dẫn/CSRF support cho QA. |
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
| 8, 11 | `service/*`, `repository/*`, `entity/*`, migration V2–V5 | business/integrity/transaction |
| 9 | `pom.xml`, Lambda `package.json` | dependencies |
| 10, 15 | `application*.properties`, `railway.json` | config/deploy |
| 13–14 | `integration/provider`, `project`, `sync`, `webhook` | Jira/GitHub flows |
| 16–17 | `src/test/java/*` và implementation | behavior/risk evidence |

## Biên bản kiểm tra sau khi tạo file

- Quét lại: **12 controller có request mapping, 38 endpoint controller**; ma trận có thêm 8 route/matcher Security-managed (OAuth/login/error/static, logout, health, Springdoc, admin) để QA không bỏ sót. `GlobalExceptionHandler` là advice, không có endpoint.
- `@PreAuthorize`: **4**; `@Secured`: **0**. Permission check chính: `IdentityMappingService#requireStudent`, `IdentityMappingReviewService#requireReviewer`, `ProjectIntegrationAuthorizationService#requireTeamManager`, `ProjectIntegrationService#requireInstallationOwner`.
- Đối chiếu `pom.xml`, package Lambda và bảng dependency; có 16 dependency Maven application/test, 2 dependency Flyway plugin và 1 Node Lambda dependency.
- Đối chiếu properties/placeholders với bảng configuration; không copy password, token, private key hoặc client secret vào tài liệu.
- Test đã chạy: `./mvnw.cmd test` — **170 tests, 0 failures, 0 errors, 0 skipped**.
- Không sửa application code; chỉ cập nhật file Markdown này.
