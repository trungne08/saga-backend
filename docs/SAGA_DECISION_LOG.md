# SAGA — Nhật ký quyết định kỹ thuật

Tài liệu ghi lại quyết định đã được code/runtime fact chứng minh và các đề xuất còn mở. `ACCEPTED` không có nghĩa production đã được kiểm chứng; evidence của từng quyết định xác định phạm vi xác nhận.

> Metadata audit: branch `main`, HEAD thực tế `d400162` (`sửa lại các số liệu`); authorization import application code ở ancestor `d855313`. Working tree hiện có thay đổi chưa commit cho provisioning/invitation/migration/test/docs. Full Maven suite sau logout-contract audit: 49 Surefire suites, 214 tests pass, 0 failures/errors/skips.

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
- Quyết định: `/api/webhooks/**` public ở URL security và được miễn CSRF; service xác thực provider token/JWT/signature.
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
- Quyết định: Explicit allowed origins; credentials true; GET/POST/PUT/PATCH/DELETE/OPTIONS; cho `X-XSRF-TOKEN`, Content-Type, Authorization, Accept.
- Lý do: Hỗ trợ API session + CSRF.
- Hệ quả: `FRONTEND_ORIGINS` là config bắt buộc, không wildcard.
- Rủi ro: Origin thiếu/sai scheme hoặc port sẽ bị từ chối.
- Evidence: `CorsConfig#corsConfigurationSource`, `SecurityIntegrationTest#corsAllowsTheConfiguredFrontendToSendTheCsrfHeaderWithCredentials`.
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
- Trạng thái: ACCEPTED (đã commit trong `d855313`; HEAD audit hiện là `d400162`)
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
- Quyết định: Import tạo outbox `student_course_invitation`, dedup theo Student/Course/type, phát event AFTER_COMMIT. Processor claim/lock record, delivery qua adapter, ghi `SENT`/`FAILED` và retry tối đa năm lần; email failure không rollback membership.
- Hệ quả: Linked Student nhận wording sign-in; Student chưa bind nhận wording sign-in/register bằng đúng email và Google nếu deployment Cognito hỗ trợ. Login URL lấy từ `STUDENT_INVITATION_LOGIN_URL`.
- TBD: Chưa chọn/configure provider production; default adapter báo unavailable an toàn, không claim mail production hoạt động.
- Evidence: `StudentInvitationOutboxService`, `StudentInvitationProcessor`, V6 migration, invitation tests.
