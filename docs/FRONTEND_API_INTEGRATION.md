# SAGA Frontend API Integration Guide

## Contribution and Jira task-data status (2026-08-04)

- **CONFIRMED:** Jira sync keeps internal Task snapshots for labels, components
  (`id`/`name`) and a canonical plain-text description. Missing/null collection
  fields become empty and each sync replaces the prior snapshot.
- **PARTIAL:** There is no Task or Contribution HTTP endpoint. The frontend must
  not request or expect Jira descriptions/components/labels or contribution data
  yet, and no provider payload, token, or credential is exposed.
- **TBD:** Product Owner must define Contribution actors/authorization, persisted
  override policy, peer-config precedence, rounding residuals and zero-base final
  distribution before an API contract is published.
- **RECOMMENDED:** add a separate read-only Contribution contract only after those
  decisions; do not infer it from assessment endpoints.

Tài liệu này được đối chiếu với controller, DTO, Security, CORS, exception
handler và integration service hiện tại của backend. Không coi tài liệu này là
nơi lưu credentials: frontend **không** cần và không được nhận OAuth token,
client secret, private key, webhook secret, database credential hoặc token đã
mã hóa.

## Base URL và tài liệu API

| Môi trường | URL |
| --- | --- |
| Local backend | `http://localhost:8080` |
| Production backend | `https://saga-backend-production-3951.up.railway.app` |
| Production Swagger UI | `https://saga-backend-production-3951.up.railway.app/swagger-ui/index.html` |
| Production health | `https://saga-backend-production-3951.up.railway.app/actuator/health` |

Swagger chỉ có mặt khi backend bật cả `SPRINGDOC_API_DOCS_ENABLED` và
`SPRINGDOC_SWAGGER_UI_ENABLED`. Frontend không gửi hai biến này và không cần
biết bất kỳ biến môi trường bí mật nào của backend.

`GET /` là landing page công khai. `GET /actuator/health` là health check công
khai. Mọi API nghiệp vụ dưới đây đều cần session, trừ các endpoint được ghi rõ
là public.

`GET {PUBLIC_BASE_URL}/privacy` is a public HTML UTF-8 Privacy Policy page. It
does not require a browser session, Bearer token, or CSRF token, and it must not
be constructed from the Railway example URL above. Frontend may link to it
directly; it is not an OAuth callback or integration flow. The SAGA operator
must configure `PRIVACY_CONTACT_URL` to a real public contact URL before deploy;
the backend validates that it is an absolute `http` or `https` URL.

---

## 1. Authentication: browser session với Cognito

Flow hiện tại là:

```text
Frontend
  -> Spring Boot: GET /api/auth/login
  -> Cognito Hosted UI
  -> Google hoặc Cognito native login
  -> Spring Boot callback /login/oauth2/code/cognito
  -> backend tạo Spring Security session (JSESSIONID)
  -> redirect về AUTH_SUCCESS_REDIRECT_URI
  -> Frontend gọi API kèm session cookie
```

Backend là confidential OAuth/OIDC client và quản lý OAuth/OIDC token nội bộ.
Sau login thành công, backend thay authentication ban đầu bằng `SagaPrincipal`
trong session, không trả access token, ID token hoặc refresh token cho FE.

**Không lưu token vào `localStorage` hoặc `sessionStorage`.** Mọi gọi API từ
trình duyệt phải gửi cookie:

```ts
fetch(`${API_BASE_URL}/api/auth/me`, { credentials: "include" });
// Axios: axios.create({ baseURL: API_BASE_URL, withCredentials: true })
```

### Login, current user và logout

| Method | Endpoint | Auth | Kết quả từ code |
| --- | --- | --- | --- |
| GET | `/api/auth/login` | Public | `302 Found` đến `/oauth2/authorization/cognito`. |
| GET | `/api/auth/me` | Session | `200` với `AuthMeResponse`; endpoint này vẫn chạm CSRF token để browser nhận cookie CSRF. |
| GET | `/api/auth/csrf` | Session | `200` với token CSRF JSON cho frontend khác domain; không trả session id hay OAuth token. |
| POST | `/api/auth/logout` | Framework-managed + CSRF | CSRF hợp lệ trả `302` đến Cognito `/logout`; session hiện có bị hủy. Thiếu/sai CSRF trả `403`. |

Khởi tạo login bằng **browser navigation**, không dùng `fetch`:

```ts
window.location.assign(`${API_BASE_URL}/api/auth/login`);
```

`AUTH_SUCCESS_REDIRECT_URI` quyết định URL FE sau khi login xong;
`AUTH_LOGOUT_REDIRECT_URI` quyết định URL sau khi Cognito logout. Đây là cấu
hình backend, không phải response FE được tự đặt. Cả hai phải là URL HTTP(S)
tuyệt đối.

### Logout: POST browser navigation qua Cognito

Không dùng `GET /api/auth/logout` và không dùng `fetch`/Axios để mong browser
tự theo redirect logout. Backend dùng Spring Security `LogoutFilter` cho
`POST /api/auth/logout`: request cần CSRF hợp lệ; nếu có session thì backend
invalidate session, clear authentication, xoá `JSESSIONID`/`XSRF-TOKEN`, rồi
redirect browser đến Cognito `/logout` với `client_id` và logout URI đã cấu hình.
Không có Cognito token, session id hay cookie nào được đưa vào redirect URL.
Swagger UI dùng fetch nên có thể báo `Failed to fetch` khi browser theo redirect
cross-origin đến Cognito; đó không phải bằng chứng logout thất bại. Dùng form
POST/top-level navigation như dưới đây cho browser client.

Từ FE, lấy CSRF token rồi submit form POST để đây là top-level navigation:

```ts
export async function logout(): Promise<void> {
  const csrfResponse = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" }
  });

  if (!csrfResponse.ok) {
    window.location.replace("/login");
    return;
  }

  const csrf = await csrfResponse.json() as {
    token: string;
    parameterName: string;
  };
  const form = document.createElement("form");
  form.method = "POST";
  form.action = `${API_BASE_URL}/api/auth/logout`;

  const input = document.createElement("input");
  input.type = "hidden";
  input.name = csrf.parameterName;
  input.value = csrf.token;
  form.appendChild(input);
  document.body.appendChild(form);
  form.submit();
}
```

Sau redirect `Backend → Cognito → Frontend`, route FE
`/logout/callback` phải xoá user và CSRF token trong AuthContext/Redux/Zustand
(memory), rồi điều hướng về `/login` hoặc trang chủ. Không đọc/xoá `JSESSIONID`
bằng JavaScript và không gọi Cognito logout bằng server-to-server request.

`GET /api/auth/me` trả:

```ts
type AuthMeResponse = {
  cognitoSub: string;
  email: string;
  fullName: string;
  applicationRole: "ADMIN" | "LECTURER" | "STUDENT";
  localProfileId: string; // UUID
  accountStatus: "ACTIVE" | "INACTIVE" | "SUSPENDED" | "PENDING" | null;
};
```

### 401 và 403

- Không có session hoặc session không hợp lệ trên protected route: `401`.
  Security trả `error: "Unauthorized"`, `message: "Authentication is required"`.
- Có session nhưng không có quyền: `403`. Security trả `error: "Forbidden"`,
  `message: "The authenticated user does not have permission for this operation"`.
- Service integration cũng có thể trả `403` với error code
  `INTEGRATION_FORBIDDEN` khi người dùng không là team manager/reviewer phù hợp.

---

## 2. CSRF

CSRF đang được bật toàn hệ thống bằng `CookieCsrfTokenRepository`:

| Thành phần | Giá trị |
| --- | --- |
| Cookie | `XSRF-TOKEN` |
| Header | `X-XSRF-TOKEN` |
| Cookie HttpOnly | `false`; browser có thể gửi cookie, nhưng JavaScript ở domain FE khác không thể đọc cookie domain backend |
| Cookie path | `/` |
| Endpoint lấy contract token cho FE | `GET /api/auth/csrf` |
| Endpoint được miễn CSRF | Chỉ `POST /api/webhooks/github` và `POST /api/webhooks/jira` |

Theo default CSRF matcher của Spring Security, các request unsafe cần header
CSRF: `POST`, `PUT`, `PATCH`, `DELETE`. Các webhook bị miễn là endpoint dành
cho provider (`POST /api/webhooks/github`, `POST /api/webhooks/jira`), không
phải API frontend gọi thay cho provider.

Flow FE chuẩn:

Khi frontend là `http://localhost:3000` và backend là Railway, JavaScript không
thể đọc `XSRF-TOKEN` thuộc domain backend bằng `document.cookie`. Sau callback,
frontend gọi `GET /api/auth/me`, sau đó gọi `GET /api/auth/csrf` với
`credentials: "include"` và giữ response trong memory/Auth store:

```ts
type CsrfTokenResponse = {
  token: string;
  headerName: "X-XSRF-TOKEN";
  parameterName: "_csrf";
};
```

Với `POST`, `PUT`, `PATCH`, `DELETE`, gửi `credentials: "include"` và header
động `[csrf.headerName]: csrf.token`. Không lưu CSRF token hay Cognito token vào
`localStorage` nếu không cần. Nếu response 403 do CSRF/session thay đổi, gọi lại
`GET /api/auth/csrf` đúng một lần rồi retry mutation tối đa một lần; không retry
vô hạn.

### Swagger UI CSRF

Swagger UI cùng origin backend được cấu hình `withCredentials` để giữ
`JSESSIONID`. Interceptor toàn cục đọc/bootstraps cookie `XSRF-TOKEN`, decode giá
trị và gắn `X-XSRF-TOKEN` chỉ cho `POST`, `PUT`, `PATCH`, `DELETE` cùng origin.
Nó không gắn header cho `GET`, `HEAD`, `OPTIONS`, không thay `Content-Type` nên
không làm hỏng multipart import, và không gửi token sang origin khác. Swagger
không dùng Bearer token và không cần khai báo CSRF header từng endpoint.

Nếu cookie chưa có, interceptor gọi cùng origin `GET /api/auth/csrf` trước
mutation. Frontend khác origin không đọc được cookie backend; tiếp tục dùng
response JSON từ endpoint CSRF theo flow ở trên.

### TypeScript fetch utility

```ts
export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ??
  "http://localhost:8080";

export type CsrfTokenResponse = {
  token: string;
  headerName: "X-XSRF-TOKEN";
  parameterName: "_csrf";
};

let csrf: CsrfTokenResponse | null = null;

export async function getCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) throw new Error(`Cannot obtain CSRF token: ${response.status}`);
  csrf = (await response.json()) as CsrfTokenResponse;
  return csrf;
}

type ApiRequestOptions = RequestInit & {
  requireCsrf?: boolean;
};

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {}
): Promise<T> {
  const {
    requireCsrf = false,
    headers: customHeaders,
    ...requestOptions
  } = options;

  const headers = new Headers(customHeaders);

  if (
    requestOptions.body !== undefined &&
    !(requestOptions.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }

  headers.set("Accept", "application/json");

  if (requireCsrf) {
    const csrfToken = csrf ?? await getCsrfToken();
    if (!csrfToken.token) {
      throw new Error(
        "Missing CSRF token. Call /api/auth/csrf before mutation requests."
      );
    }
    headers.set(csrfToken.headerName, csrfToken.token);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    credentials: "include",
    headers
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const message =
      typeof body === "object" &&
      body !== null &&
      "message" in body
        ? String(body.message)
        : `HTTP ${response.status}`;

    throw new ApiError(response.status, message, body);
  }

  return body as T;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly body: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}
```

Ví dụ mutation:

```ts
await apiRequest("/api/teams/<team-uuid>/projects", {
  method: "POST",
  requireCsrf: true,
  body: JSON.stringify({ name: "SAGA capstone" })
});
```

---

## 3. CORS, cookie và frontend environment

Backend chỉ chấp nhận các origin được khai báo bởi `FRONTEND_ORIGINS` (danh
sách cách nhau bằng dấu phẩy). Mỗi origin phải là HTTP(S) origin rõ ràng, không
chứa wildcard, path, query, fragment hoặc user info. CORS cho phép:

- methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`;
- request headers: `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`;
- exposed header: `Location`;
- credentials: `true`.

Để FE khác origin hoạt động, origin thực tế của FE phải được thêm đúng vào
`FRONTEND_ORIGINS` ở backend. Không dùng `*` khi gọi bằng credentials.

Session cookie do backend thiết lập. Với profile production, các property lấy
từ `SESSION_COOKIE_SECURE` (default `true`) và `SESSION_COOKIE_SAME_SITE`
(default `none`); profile local dùng `secure=false`, `same-site=lax`.
Frontend chỉ cần `VITE_API_BASE_URL`, ví dụ:

```env
# local
VITE_API_BASE_URL=http://localhost:8080

# production
VITE_API_BASE_URL=https://saga-backend-production-3951.up.railway.app
```

Không đưa biến secret backend nào vào bundle FE.

Railway phải cấu hình đúng tên biến mà `application-prod.properties` đang map,
không dùng tên relaxed-binding khác:

```env
FRONTEND_ORIGINS=http://localhost:3000
AUTH_SUCCESS_REDIRECT_URI=http://localhost:3000/auth/callback
AUTH_LOGOUT_REDIRECT_URI=http://localhost:3000/logout/callback
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
```

Trên Cognito Hosted UI, Allowed sign-out URL phải chứa chính xác
`http://localhost:3000/logout/callback`. `COGNITO_CLIENT_ID` là client id đã
có của backend; `COGNITO_DOMAIN` là tùy chọn nếu muốn chỉ định origin Cognito
HTTPS, nếu trống backend suy ra domain từ authorization URI. Không truyền
`logout_uri` từ query string người dùng: backend chỉ dùng URI cấu hình.

`FRONTEND_ORIGINS` được tách bằng dấu phẩy, trim từng phần, bỏ phần rỗng,
deduplicate và chỉ chấp nhận HTTP(S) origin không wildcard/path/query/fragment.
CORS cho phép `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; các request
header `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `Accept`; và
`allowCredentials=true`. Preflight `OPTIONS` vì vậy không cần session.

---

## 4. Error response và validation

Các lỗi authentication/authorization và exception handler ứng dụng dùng:

```ts
type ApiErrorResponse = {
  timestamp: string; // Instant
  status: number;
  error: string;
  message: string;
  path: string;
};
```

Mapping được code định nghĩa:

| Nguồn lỗi | HTTP status | `error` |
| --- | --- | --- |
| Không xác thực | 401 | `Unauthorized` |
| Không có quyền | 403 | `Forbidden` |
| `UnauthenticatedRequestException` | 401 | `Unauthorized` |
| `IdentityConflictException` | 409 | `Conflict` |
| `InvalidIdentityException` | 422 | `Unprocessable Entity` |
| `IdentityServiceException` | 502 | `Bad Gateway` |
| `IntegrationException` | Theo exception | Mã integration an toàn, ví dụ `INTEGRATION_NOT_CONFIGURED` |

`IntegrationException` có thể mang status `400`, `403`, `409`, `502` hoặc
`503` tùy service. FE phải dùng `status`, `error` và `message`, không parse
raw provider response.

Request DTO có Bean Validation. Các lỗi validation framework (`@NotBlank`,
`@NotNull`, `@Size`, `@Positive`, `@Min`, `@Max`) hiện **không có** custom
`@ExceptionHandler` trong source; do đó FE không nên phụ thuộc vào body chi
tiết của validation error. Hiển thị `ApiError.message` khi có, đồng thời validate
form phía client theo contract dưới đây.

---

## 5. Quyền và enums FE cần biết

`SagaPrincipal` cung cấp các field của `GET /api/auth/me`: `cognitoSub`,
`email`, `fullName`, `applicationRole`, `localProfileId`, `accountStatus`.

| Enum | Giá trị |
| --- | --- |
| `ApplicationRole` | `ADMIN`, `LECTURER`, `STUDENT` |
| `AccountStatus` | `ACTIVE`, `INACTIVE`, `SUSPENDED`, `PENDING` |
| `IntegrationProvider` | `JIRA`, `GITHUB` |
| `IntegrationStatus` | `CONNECTING`, `BACKFILLING`, `ACTIVE`, `DEGRADED`, `DISCONNECTED` |
| `IdentityMappingStatus` | `ACTIVE`, `DISCONNECTED`, `PENDING_REVIEW`, `REJECTED` |
| Review action | `APPROVE`, `REJECT`, `CORRECT` |
| `SyncJobType` | `JIRA_SYNC`, `GIT_SYNC`, `INITIAL_BACKFILL`, `RECONCILIATION`, `WEBHOOK_PROCESSING`, `OTHER` |
| `SyncJobStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `PARTIAL_FAILURE`, `FAILED` |

Quyền API:

- Tất cả API master data đọc yêu cầu session. Tạo Subject/Course/Class/Semester
  yêu cầu `ADMIN`.
- Team Project và project integration yêu cầu **team manager**: `ADMIN`; hoặc
  `LECTURER` là instructor của course của team; hoặc `STUDENT` là `LEADER` của
  team đó.
- Review identity mapping yêu cầu `ADMIN`, hoặc `LECTURER` dạy course của một
  team có student cần review.
- Personal integration yêu cầu người dùng đã đăng nhập; provider phải được
  backend bật. Project integration cũng cần team-manager check.

Frontend có thể dùng role để quyết định hiển thị UI, nhưng luôn phải xử lý 403:
backend mới là nguồn quyết định quyền.

---

## 6. Master-data API

Tất cả endpoint trong phần này cần session. `page` là zero-based; mặc định
`page=0`, `size=10`; `size` phải từ `1` đến `100`.

| Method | Path | Query | Quyền | Success |
| --- | --- | --- | --- | --- |
| GET | `/api/v1/subjects` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Subject>` |
| GET | `/api/v1/subjects/{id}` | — | Session | 200 `Subject` |
| POST | `/api/v1/subjects` | — | ADMIN + CSRF | 201 `Subject` |
| GET | `/api/v1/classes` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Class>` |
| GET | `/api/v1/classes/{id}` | — | Session | 200 `Class` |
| POST | `/api/v1/classes` | — | ADMIN + CSRF | 201 `Class` |
| GET | `/api/v1/semesters` | `keyword?`, `page?`, `size?` | Session | 200 `Page<Semester>` |
| GET | `/api/v1/semesters/{id}` | — | Session | 200 `Semester` |
| POST | `/api/v1/semesters` | — | ADMIN + CSRF | 201 `Semester` |
| GET | `/api/v1/courses` | `subjectId?`, `semesterId?`, `instructorId?`, `page?`, `size?` | Session | 200 `Page<Course>` |
| GET | `/api/v1/courses/{id}` | — | Session | 200 `Course` |
| POST | `/api/v1/courses` | — | ADMIN + CSRF | 201 `Course` |

### Request body

```ts
type SubjectRequest = {
  subjectCode: string; // non-blank, max 255
  name: string;        // non-blank, max 255
};

type ClassRequest = {
  classCode: string; // non-blank, max 255
  name: string;      // non-blank, max 255
};

type SemesterRequest = {
  code: string;      // non-blank, max 255
  name: string;      // non-blank, max 255
  startDate: string; // LocalDateTime JSON string
  endDate: string;   // LocalDateTime JSON string; must not be before startDate
};

type CourseRequest = {
  courseCode: string; // non-blank, max 255
  name: string;       // non-blank, max 255
  subjectId: string;  // UUID
  classId: string;    // UUID
  semesterId: string; // UUID
  instructorId: string; // UUID
};
```

`Subject`, `Class` và `Semester` là JPA entity response trực tiếp, có các field
base `id`, `createdAt`, `updatedAt` và các property domain tương ứng. `Course`
cũng được trả trực tiếp từ entity với `id`, `createdAt`, `updatedAt`,
`courseCode`, `name`, `subject`, `clazz`, `semester`, `instructor`; không có
DTO response ổn định riêng. Kiểm tra schema đang chạy trên Swagger trước khi
bind toàn bộ nested entity vào UI.

Các service xác định rõ `404` khi không tìm thấy entity liên quan, `409` khi
mã Subject/Class/Course/Semester trùng, và `400` khi `endDate` trước
`startDate`. Đây là `ResponseStatusException` từ service; chỉ dựa vào status
và message hiển thị được, không giả định error-body validation chi tiết.

`Page<T>` là response `org.springframework.data.domain.Page` trả trực tiếp,
không phải DTO tự định nghĩa. FE cần dùng `content` và pagination metadata mà
Swagger của môi trường đang chạy hiển thị, thay vì tự tạo một envelope khác.

---

## 7. Team Project và identity mapping review

### Danh sách thành viên Team

| Method | Path | Auth/authorization | Response |
| --- | --- | --- | --- |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/members?page=0&size=20` | ADMIN mọi Team; LECTURER chỉ Course mình dạy; STUDENT chỉ đúng Team mình là member, cả LEADER và MEMBER | `200 Page<TeamMemberResponse>`; `401` anonymous; `403` không đủ scope; `404` Team không có hoặc không thuộc Course URL |

`TeamMemberResponse` chỉ có `studentId`, `fullName`, `studentCode`, `roleInTeam`.
Không hiển thị email, `cognitoSub` hay version. UI phải gửi `courseId` và `teamId`
đúng quan hệ; endpoint là read-only nên không cần CSRF.

### Tạo Project cho team

| Method | Path | Quyền | Success |
| --- | --- | --- | --- |
| POST | `/api/teams/{teamId}/projects` | Team manager + CSRF | 201 `ProjectResponse` |

```ts
type CreateTeamProjectRequest = { name: string }; // non-blank, max 255
type ProjectResponse = { id: string; teamId: string; name: string };
```

Nếu team đã có project, backend trả `409` với code
`TEAM_PROJECT_ALREADY_EXISTS`.

### Identity mapping review

| Method | Path | Quyền | Success |
| --- | --- | --- | --- |
| GET | `/api/integrations/identity-mappings?studentId={uuid}` | Authorized reviewer | 200 `IdentityConnectionResponse[]` |
| PATCH | `/api/integrations/identity-mappings/{mappingId}` | Authorized reviewer + CSRF | 200 `IdentityConnectionResponse` |

```ts
type IdentityMappingReviewRequest = {
  action: "APPROVE" | "REJECT" | "CORRECT";
  correctedStudentId?: string; // required only for CORRECT
};

type IdentityConnectionResponse = {
  provider: "JIRA" | "GITHUB";
  status: "ACTIVE" | "DISCONNECTED" | "PENDING_REVIEW" | "REJECTED";
  displayName: string;
  email: string;
  verifiedAt: string | null;     // LocalDateTime
  disconnectedAt: string | null; // LocalDateTime
};
```

`CORRECT` thiếu `correctedStudentId` trả `400` code
`CORRECTED_STUDENT_REQUIRED`. Mapping không tồn tại trả `400`
`IDENTITY_MAPPING_NOT_FOUND`; mapping trùng khi sửa trả `409`
`IDENTITY_MAPPING_CONFLICT`.

---

## 8. Personal Jira/GitHub integration

Các route phần này đều cần session. Chỉ route `GET .../connect` dùng browser
navigation vì trả `302` đến OAuth provider.

| Method | Path | Success | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/me/integrations` | 200 `PersonalIntegrationsResponse` | Danh sách kết nối của chính user. |
| GET | `/api/me/integrations/jira/connect` | 302 | Dùng `window.location.assign`; bắt đầu Jira OAuth. |
| GET | `/api/integrations/jira/callback` | 200 JSON polymorphic | Provider callback; hiện trả JSON trực tiếp sau khi consume session state. Không gọi thủ công. |
| DELETE | `/api/me/integrations/jira` | 204 | CSRF required. |
| GET | `/api/me/integrations/github/connect` | 302 | Dùng browser navigation; bắt đầu GitHub OAuth. |
| GET | `/api/me/integrations/github/callback` | 200 `IdentityConnectionResponse` JSON | Provider callback, hiện trả JSON trực tiếp; không gọi thủ công. |
| DELETE | `/api/me/integrations/github` | 204 | CSRF required. |

```ts
type PersonalIntegrationsResponse = {
  connections: IdentityConnectionResponse[];
};
```

Jira callback có response phụ thuộc OAuth flow: personal flow trả
`IdentityConnectionResponse`; project flow trả `JiraAuthorizationResponse`.
FE không tự truyền `state`, `code` hoặc `error`: chúng là query parameters do
provider redirect trả về và được backend kiểm tra bằng `HttpSession` state.

**CONFIRMED:** Jira/GitHub completion callback hiện trả JSON trực tiếp sau khi
backend consume state session; không redirect về frontend sau completion.
**RECOMMENDED, chưa implemented:** redirect callback về FE và expose một
read-once result đã được consume từ session qua API riêng. FE không được giả định
flow này đã tồn tại.

Nếu Jira hoặc GitHub bị tắt ở backend, endpoint connect trả `503` với code
`INTEGRATION_NOT_CONFIGURED`. Consent bị từ chối/cancel trả `400`
`OAUTH_CONSENT_DENIED`.

---

## 9. Project Jira/GitHub integration và sync status

Mọi route trong phần này cần team-manager authorization. Các `connect`,
`install`, `setup` trả redirect nên dùng browser navigation. Với mutation dùng
CSRF utility.

### Project integration routes

| Method | Path | Success | Ghi chú |
| --- | --- | --- | --- |
| GET | `/api/projects/{projectId}/integrations` | 200 `ProjectIntegrationsResponse` | Trạng thái Jira/repository hiện tại. |
| GET | `/api/projects/{projectId}/jira/connect` | 302 | Bắt đầu Jira OAuth. |
| POST | `/api/projects/{projectId}/jira/link` | 200 `ProjectIntegrationsResponse` | Chỉ gọi sau project Jira OAuth callback thành công trong cùng browser session. |
| DELETE | `/api/projects/{projectId}/jira` | 204 | CSRF required. |
| GET | `/api/projects/{projectId}/github/install` | 302 | Bắt đầu GitHub App install. |
| GET | `/api/projects/{projectId}/github/setup` | 302 | GitHub setup callback route; provider/browser flow. |
| GET | `/api/projects/{projectId}/github/callback` | 200 `GitHubInstallationResponse` | GitHub OAuth callback; hiện trả JSON trực tiếp. |
| POST | `/api/projects/{projectId}/github/repositories` | 200 `ProjectIntegrationsResponse` | Liên kết repository và yêu cầu initial backfill. |
| DELETE | `/api/projects/{projectId}/github/repositories/{repositoryId}` | 204 | CSRF required. |
| GET | `/api/projects/{projectId}/sync-status` | 200 `SyncStatusResponse` | Tối đa 20 job mới nhất của Jira/repository thuộc project. |

Provider callback aliases có cùng session flow:

| Method | Path | Success |
| --- | --- | --- |
| GET | `/api/integrations/github/setup` | 302 |
| GET | `/api/integrations/github/project/callback` | 200 `GitHubInstallationResponse` | Hiện trả JSON trực tiếp. |

### Request/response bodies

```ts
type JiraProjectLinkRequest = {
  cloudId: string;       // non-blank, max 255
  jiraProjectId: string; // non-blank, max 255
};

type GitHubRepositoriesLinkRequest = {
  installationId: number; // positive integer
  repositoryIds: number[]; // non-empty; every entry positive; no duplicates
};

type JiraSiteResponse = {
  cloudId: string;
  name: string;
  siteUrl: string;
};

type JiraAuthorizationResponse = {
  projectId: string;
  sites: JiraSiteResponse[];
};

type GitHubRepositoryResponse = {
  repositoryId: number;
  fullName: string;
  defaultBranch: string;
  status: "CONNECTING" | "BACKFILLING" | "ACTIVE" | "DEGRADED" | "DISCONNECTED";
  lastSyncedAt: string | null; // LocalDateTime
};

type GitHubInstallationResponse = {
  projectId: string;
  installationId: number;
  accountLogin: string;
  accountType: string;
  repositories: GitHubRepositoryResponse[];
};

type ProjectIntegrationsResponse = {
  projectId: string;
  jira: {
    siteUrl: string;
    projectKey: string;
    status: "CONNECTING" | "BACKFILLING" | "ACTIVE" | "DEGRADED" | "DISCONNECTED";
    webhookExpiresAt: string | null; // LocalDateTime
    lastSyncedAt: string | null;     // LocalDateTime
  } | null;
  githubRepositories: GitHubRepositoryResponse[];
};

type SyncStatusResponse = {
  projectId: string;
  recentJobs: Array<{
    id: string;
    targetSystem: string;
    type: "JIRA_SYNC" | "GIT_SYNC" | "INITIAL_BACKFILL" | "RECONCILIATION" | "WEBHOOK_PROCESSING" | "OTHER";
    status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "PARTIAL_FAILURE" | "FAILED";
    startedAt: string | null;   // LocalDateTime
    completedAt: string | null; // LocalDateTime
    itemsProcessed: number | null;
    itemsFailed: number | null;
    errorCategory: string | null;
    failureStage: string | null;
  }>;
};
```

### UI flow được backend hỗ trợ

1. Jira: navigate đến `.../jira/connect` -> provider callback trả
   `JiraAuthorizationResponse` gồm `sites` -> FE để user chọn site/project ->
   POST `.../jira/link` với `cloudId`, `jiraProjectId` và CSRF -> poll
   `.../sync-status` nếu cần tiến độ backfill.
2. GitHub: navigate đến `.../github/install` -> GitHub setup/callback ->
   nhận `GitHubInstallationResponse.repositories` -> POST
   `.../github/repositories` với installation/repository IDs và CSRF -> poll
   `.../sync-status`.

Không đặt OAuth `state`, `code`, installation verification data hoặc webhook
data trong client state như một credential. Backend giữ OAuth state và grant
ngắn hạn trong `HttpSession`.

Một số status/code integration FE nên xử lý trực tiếp:

- `400`: lựa chọn/request OAuth không hợp lệ (ví dụ
  `GITHUB_INSTALLATION_INCOMPLETE`, `OAUTH_CONSENT_DENIED`).
- `403`: không phải team manager hoặc user GitHub không có quyền installation.
- `409`: project/site/repository/installation không ở trạng thái liên kết hợp
  lệ (ví dụ `JIRA_PROJECT_ALREADY_LINKED`, `GITHUB_REPOSITORY_ALREADY_LINKED`).
- `502`: provider tạm thời không khả dụng hoặc response provider không hợp lệ.
- `503 INTEGRATION_NOT_CONFIGURED`: integration đã bị tắt trên môi trường đó.

`sync-status` chỉ báo trạng thái sync của backend; `COMPLETED` với `0/0` nghĩa
job hoàn tất nhưng không xử lý item nào. Dùng `errorCategory` và `failureStage`
để hiển thị diagnostic an toàn, không hiển thị như provider payload.

---

## 10. Webhook API: provider-only, không gọi từ frontend

| Method | Path | CSRF | Response |
| --- | --- | --- | --- |
| POST | `/api/webhooks/github` | Exempt | `200 {"status":"PING"}` cho ping, ngược lại `202 WebhookAcceptedResponse` |
| POST | `/api/webhooks/jira` | Exempt | `202 WebhookAcceptedResponse` |

GitHub gửi payload raw cùng `X-Hub-Signature-256`, `X-GitHub-Delivery`,
`X-GitHub-Event`. Jira gửi payload raw với query `token` hoặc header
`Authorization`, và có thể `X-Atlassian-Webhook-Identifier`. Các endpoint này
được Spring Security public để provider giao hàng; signature/secret validation
do backend thực hiện. FE không gọi, không replay payload và không cần biết các
header/secret đó.

---

## 11. Checklist triển khai FE

1. Đặt `VITE_API_BASE_URL` theo local/production.
2. Bảo đảm origin FE có trong `FRONTEND_ORIGINS` backend.
3. Login bằng `window.location.assign`, không bằng `fetch`.
4. Sau callback/login, gọi `/api/auth/me` để lấy profile, rồi gọi
   `/api/auth/csrf` với cookies để lấy CSRF token JSON vào memory.
5. Dùng `apiRequest` cho mọi API; đặt `requireCsrf: true` với mutation.
6. Khi nhận 401, đưa user về flow login; khi 403, hiển thị no-permission UI
   hoặc refresh CSRF đúng một lần trước khi retry mutation.
7. Với OAuth provider connect/install, luôn browser-navigate và để callback
   quay lại cùng browser session.
8. Lấy contract response thực tế từ Swagger khi Springdoc bật, đặc biệt với
   các master-data endpoint trả JPA entity trực tiếp và `Page<T>`.

## 12. Course roster và lecturer options

| Method | Path | Quyền | Query/response |
| --- | --- | --- | --- |
| GET | `/api/v1/courses/{courseId}/students` | ADMIN mọi Course; LECTURER là instructor; anonymous 401; STUDENT/lecturer ngoài scope 403; Course thiếu 404 | `keyword`, `hasTeam=all|with|without`, `sortBy=studentCode|fullName|email|teamName|projectName`, `sortDirection=asc|desc`, `page`, `size` → `studentsWithTeam`/`studentsWithoutTeam` pages |
| GET | `/api/v1/courses/instructors` | ADMIN; anonymous 401; LECTURER/STUDENT 403 | `keyword` chỉ trên fullName/email, `sortBy=fullName|email`, `sortDirection=asc|desc`, `page`, `size` → `Page<LecturerOptionResponse>` |
| GET | `/api/me/courses/{courseId}/team/members` | STUDENT-only; anonymous 401; ADMIN/LECTURER 403 | backend tự resolve team; `page`, `size` → `MyCourseTeamMembersResponse`; 404 Course/no Team, 409 legacy nhiều Team |

Cả hai là GET, cần browser session nhưng không cần CSRF. Giá trị filter/sort không
hợp lệ trả 400. Roster filter/sort trước pagination; metadata được tính trên toàn bộ
tập sau filter và tie-break ổn định theo id. Roster chỉ dùng `TeamMember -> Team ->
Course` làm bằng chứng Student thuộc Course; invitation outbox không phải enrollment
source. `studentsWithoutTeam` và `hasTeam=without` hiện rỗng vì chưa có quan hệ
enrollment Student–Course độc lập, nên FE không được quảng bá nhánh `without` như
feature đầy đủ.

Business rule đã được Product Owner chốt: Student có thể thuộc nhiều Course nhưng
tối đa một Team trong mỗi Course; role và Project độc lập theo Team/Course. Legacy
invalid data nhiều Team cùng Course có thể được đọc mà roster không crash, nhưng đó
không phải behavior hợp lệ. Roster trả email Student cho ADMIN/Lecturer owner;
lecturer options trả email Lecturer cho ADMIN. Actor ngoài scope bị authorization
chặn. Business/UI justification cho hai email field vẫn TBD; không response nào trả
`cognitoSub`, version, session, token hoặc credential.

### Student self-scoped team trong Course

FE dùng endpoint này khi Student cần xem Team/Course hiện tại mà chưa biết `teamId`:

```ts
type MyCourseTeamMembersResponse = {
  courseId: string;
  teamId: string;       // backend tự resolve; FE không gửi teamId
  teamName: string;
  roleInTeam: "LEADER" | "MEMBER" | "MENTOR";
  project: { id: string; name: string } | null;
  members: Page<TeamMemberResponse>;
};
```

`GET /api/me/courses/{courseId}/team/members?page=0&size=20` dùng browser
session, không cần CSRF và không nhận `studentId` hay `teamId`. Backend lấy Student
từ `SagaPrincipal.localProfileId`, query membership theo Student+Course rồi trả
resolved `teamId`; FE có thể dùng id đó cho flow Project/integration hiện có. MEMBER
và LEADER đều xem được team của mình; quyền tạo Project vẫn là rule riêng.

`404` nghĩa Course không tồn tại hoặc Student chưa có Team trong Course. `409` nghĩa
dữ liệu legacy không hợp lệ có nhiều Team cho cùng Student/Course; FE không tự chọn
một Team để retry. Response và từng member không có email, `cognitoSub`, version,
session, CSRF, token hay credential.

### Jira labels status

Jira labels are fetched and stored only as an internal Task snapshot. There is
currently no Task read endpoint, no labels field exposed to frontend, and no
SAGA API that creates or updates Jira tasks. Therefore FE label display and
filtering remain **PARTIAL** until a separately authorized Task read contract is
implemented. Labels are classification data, not the Jira issue id/key used to
identify a Task.
