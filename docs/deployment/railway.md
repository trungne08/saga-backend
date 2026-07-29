# Railway production runbook

Tài liệu này chỉ chuẩn bị cấu hình. Không có deployment hay migration database
thật nào được thực hiện bởi thay đổi này.

## 1. Service và runtime

Tạo một Railway service chạy liên tục từ repository root. Không cấu hình service
này thành Railway Cron Job: webhook worker và reconciliation scheduler là tác vụ
tự động nằm trong chính web application.

Railway đọc `railway.json` với cấu hình:

```text
Build:  mvn clean package -DskipTests
Start:  java -jar target/be-0.0.1-SNAPSHOT.jar
Health: GET /actuator/health (phải trả HTTP 200)
Port:   server.port=${PORT:8080}
Proxy:  server.forward-headers-strategy=framework
```

`PORT` do Railway inject. `overlapSeconds=0` tránh giữ hai deployment cùng chạy
trong giai đoạn chuyển phiên bản.

## 2. Giới hạn một replica

Deployment đầu tiên và trạng thái vận hành hiện tại phải giữ đúng **một replica**,
không bật multi-region hoặc horizontal autoscaling.

Lý do:

- đăng nhập và OAuth state dùng server-side `HttpSession`;
- installation token cache hiện nằm trong bộ nhớ tiến trình;
- reconciliation scheduler chưa có distributed database lock.

Webhook receipt đã durable trong MySQL và worker tự retry sau restart. Scheduler
và worker vẫn phải được bật tự động. Chỉ tăng replica sau khi đã có shared Spring
Session store (Redis/JDBC), đã đánh giá lại token cache, và đã thêm database-backed
distributed lock cho scheduled reconciliation.

## 3. Public routes

Chỉ đăng ký URL HTTPS tạo từ cùng một `PUBLIC_BASE_URL`:

```text
JIRA_CALLBACK_URL=${PUBLIC_BASE_URL}/api/integrations/jira/callback
JIRA_WEBHOOK_PUBLIC_URL=${PUBLIC_BASE_URL}/api/webhooks/jira
GITHUB_PERSONAL_CALLBACK_URL=${PUBLIC_BASE_URL}/api/me/integrations/github/callback
GITHUB_PROJECT_CALLBACK_URL=${PUBLIC_BASE_URL}/api/integrations/github/project/callback
GITHUB_SETUP_URL=${PUBLIC_BASE_URL}/api/integrations/github/setup
GITHUB_WEBHOOK_URL=${PUBLIC_BASE_URL}/api/webhooks/github
COGNITO_CALLBACK_URL=${PUBLIC_BASE_URL}/login/oauth2/code/cognito
```

`GITHUB_WEBHOOK_URL` và `COGNITO_CALLBACK_URL` là giá trị phải nhập ở provider
console; ứng dụng không cần hai biến runtime này. Startup validator từ chối
`PUBLIC_BASE_URL` có path, URL không phải HTTPS trong production, hoặc callback /
webhook runtime không khớp chính xác origin và route trên.

Jira chỉ có **một** callback. Flow `PERSONAL_JIRA` hay `PROJECT_JIRA`, cùng project
target nếu có, chỉ được lấy từ OAuth state ngẫu nhiên, bound với session/principal,
có TTL và consume một lần ở server. Callback không tin ID hay flow trong query.

## 4. Railway variables

Các biến bắt buộc cho production:

```text
SPRING_PROFILES_ACTIVE=prod
PUBLIC_BASE_URL
FRONTEND_ORIGINS
AUTH_SUCCESS_REDIRECT_URI
AUTH_LOGOUT_REDIRECT_URI
DATABASE_JDBC_URL
DATABASE_USERNAME
DATABASE_PASSWORD
MONGO_URI
MONGO_DATABASE
COGNITO_ISSUER_URI
COGNITO_CLIENT_ID
COGNITO_CLIENT_SECRET
COGNITO_DOMAIN
INTEGRATION_TOKEN_ENCRYPTION_KEY
INTEGRATION_TOKEN_ENCRYPTION_KEY_ID
JIRA_CLIENT_ID
JIRA_CLIENT_SECRET
JIRA_CALLBACK_URL
JIRA_WEBHOOK_PUBLIC_URL
GITHUB_APP_ID
GITHUB_CLIENT_ID
GITHUB_CLIENT_SECRET
GITHUB_PRIVATE_KEY
GITHUB_WEBHOOK_SECRET
GITHUB_APP_SLUG
GITHUB_PERSONAL_CALLBACK_URL
GITHUB_PROJECT_CALLBACK_URL
GITHUB_SETUP_URL
```

Railway cung cấp `PORT`; không cần tạo giá trị cố định. Các biến có default an toàn
nhưng nên khai báo rõ:

```text
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
SWAGGER_ENABLED=false
FLYWAY_ENABLED=false
FLYWAY_BASELINE_ON_MIGRATE=false
INTEGRATION_TOKEN_ENCRYPTION_PREVIOUS_KEYS=
INTEGRATION_RECONCILIATION_ENABLED=true
INTEGRATION_RECONCILIATION_DELAY_MS=900000
INTEGRATION_OVERLAP_WINDOW=PT5M
INTEGRATION_OAUTH_STATE_TTL=PT10M
INTEGRATION_HTTP_CONNECT_TIMEOUT=PT3S
INTEGRATION_HTTP_READ_TIMEOUT=PT10S
```

`AUTH_SUCCESS_REDIRECT_URI`, `AUTH_LOGOUT_REDIRECT_URI` và mọi
`FRONTEND_ORIGINS` phải là URL frontend production phù hợp. Không dùng wildcard
origin khi gửi cookie. `INTEGRATION_TOKEN_ENCRYPTION_KEY` là đúng 32 byte ngẫu
nhiên đã Base64; private key GitHub phải giữ nguyên newline PEM khi nhập vào
Railway.

`JIRA_AUTHORIZATION_URL`, `JIRA_TOKEN_URL`, `JIRA_API_BASE_URL`,
`GITHUB_API_BASE_URL`, `GITHUB_WEB_BASE_URL`, và Jira scopes đã có provider
defaults trong application config; chỉ override nếu chủ động dùng endpoint khác.
Xem `.env.example` để có template đầy đủ.

## 5. Migration lần đầu cho schema legacy

Migration integration là:

```text
src/main/resources/db/migration/V2__integration_identity_and_sync.sql
```

Không tồn tại migration integration `V1`. Version `1` được dành làm baseline cho
schema MySQL legacy đang non-empty.

Quy trình được duyệt:

1. Backup database và kiểm thử restore.
2. Dừng writer/traffic cũ, giữ Railway service ở đúng một replica.
3. Chạy `docs/integrations/mysql-integration-preflight.sql` bằng DB principal
   read-only. Mọi duplicate/orphan check phải sạch và không được có DDL V2 một phần.
4. Chỉ trong lần startup migration có kiểm soát, đặt:

   ```text
   FLYWAY_ENABLED=true
   FLYWAY_BASELINE_ON_MIGRATE=true
   ```

   `spring.flyway.baseline-version=1` được cố định trong
   `application.properties`.
5. Flyway tạo history/baseline version 1 rồi áp dụng V2. Không chạy thao tác này
   đồng thời trên nhiều instance.
6. Xác nhận schema và `flyway_schema_history` đúng như bảng dưới.
7. Ngay sau lần thành công đầu tiên, đặt lại:

   ```text
   FLYWAY_ENABLED=false
   FLYWAY_BASELINE_ON_MIGRATE=false
   ```

8. Khởi động/rollout application với Hibernate
   `spring.jpa.hibernate.ddl-auto=validate`, sau đó mới mở traffic.

Nếu database đã có `flyway_schema_history`, **không bật baseline-on-migrate**.
Phải kiểm tra history/checksum và tình trạng DDL trước khi quyết định chạy V2.
`railway.json` không có pre-deploy migration command; vì vậy repo không tự chạy
migration live ngoài startup được operator bật rõ ràng.

Sau migration đầu tiên thành công, `flyway_schema_history` phải có chính xác hai
successful row liên quan:

| rank | version | description | type | script | checksum | success |
|---:|---:|---|---|---|---|---:|
| 1 | 1 | `<< Flyway Baseline >>` | `BASELINE` | `<< Flyway Baseline >>` | `NULL` | 1 |
| 2 | 2 | `integration identity and sync` | `SQL` | `V2__integration_identity_and_sync.sql` | non-null | 1 |

V2 tạo `identity_mapping_history`, `github_installation`, `webhook_receipt`, đồng
thời alter các bảng legacy được liệt kê chi tiết trong integration runbook.
Không được có failed Flyway row.

## 6. Go-live checklist

- Railway service là long-running web service, không phải Cron Job.
- Replica count bằng 1; không có multi-region/autoscaling.
- Provider consoles dùng đúng các URL ở mục 3.
- `GET /actuator/health` trả 200 qua public Railway domain.
- Session cookie là `Secure`, SameSite phù hợp với topology frontend.
- Flyway flags đã trở về `false` sau migration đầu tiên.
- Reconciliation và webhook processing vẫn bật.
- Không có secret/token trong log, API response hoặc Railway build variables.
