# Student import, invitation, and first-login provisioning

## Account lifecycle M3B — 2026-08-09

PENDING vẫn chỉ thuộc Student provisioning/import. Admin không thể set Student PENDING qua status API. First-login giữ nguyên PENDING -> ACTIVE và từ chối INACTIVE/SUSPENDED; request-time business API còn chặn PENDING/INACTIVE/SUSPENDED theo current DB status. Invitation, parser, identity matching và TeamMember không đổi.

## AccountStatus audit — 2026-08-09

Imported Student mới bắt đầu `PENDING`. First-login identity binding giữ nguyên: chỉ `PENDING -> ACTIVE`; target `ACTIVE` giữ ACTIVE; `INACTIVE`/`SUSPENDED` conflict trước bind/activate. M3A không thay đổi parser, invitation, provisioning, identity matching hay TeamMember.

## Course retention lookup — 2026-08-09

Import authorization chỉ resolve Course active. Course tombstone trả 404 trước khi import mutation chạy; parser CSV/XLSX, invitation outbox, delivery, provisioning, TeamMember và business rule import không thay đổi.

## Contribution isolation update (2026-08-04)

- **CONFIRMED:** Contribution reads `CommitData`, SAGA `Document`, Jira-synced
  `Task` and `PeerReview` data only; it does not write Student, TeamMember,
  invitation outbox or identity mappings.
- **CONFIRMED:** Invitation outbox remains delivery-only and is not a source of
  Project membership or Contribution enrollment.
- **TBD:** A persisted Contribution override model is not present. Existing
  `PolicyOverrideRequest` is not reused because it is not a per-Student/per-Project
  Contribution override.

Trạng thái: **PARTIAL** — HEAD hiện tại là `0bc30be`; `200d866`, `a43f05d` và `07ffa38` là checkpoint lịch sử. Sáu Markdown đang được đồng bộ; source/config/test là evidence. Không ghi secret tại đây.

`GET /privacy` is a separate public HTML policy route. It does not change imported Student provisioning, invitation outbox, membership rules, OAuth, browser session, or CSRF behavior. Its public contact link is deployment configuration (`PRIVACY_CONTACT_URL`), not invitation data. **Runtime fact do người dùng cung cấp:** route đã public thành công và Privacy Policy URL đã được cấu hình; giá trị URL không được ghi ở đây.

## Import and access flow

`POST /api/v1/courses/{courseId}/import-students` still uses browser `JSESSIONID` plus CSRF.

1. ADMIN may import any Course; LECTURER must be that Course's instructor; STUDENT is forbidden.
2. Excel values are normalized: email is trimmed/lowercased and student code is trimmed/uppercased.
3. Import reuses a Student only when normalized email and code resolve to the same record. A partial or split match fails the transaction safely.
4. Import creates/reuses Team and TeamMember. The Student starts `PENDING` when newly created. Student có thể thuộc nhiều Course nhưng tối đa một Team trong mỗi Course: cùng Team idempotent và không tự đổi role; Team khác trong cùng Course conflict 409; Course khác hợp lệ với role độc lập.
5. Trước khi quyết định TeamMember, import lock Student bằng `PESSIMISTIC_WRITE`, rồi query membership theo Student+Course. Chỉ production write path này tạo TeamMember; local seed không tạo dữ liệu trái rule.
6. Once a TeamMember exists/reuses, import enqueues a course invitation in the same transaction. Rollback creates neither membership nor invitation.

Student is a global profile. Course, Team and LEADER/MEMBER/MENTOR are represented only by `Student -> TeamMember -> Team -> Course`. Provisioning never creates, deletes or changes those memberships; therefore a bound Student immediately sees the existing Course/Team/Project relations through the same Student id.

## Identity matching contract

For application role `STUDENT`:

1. Find by `cognitoSub`. One Student is reused idempotently; multiple profile matches conflict; no Student is created in this path.
2. If no subject match exists, OIDC already requires a verified email. Normalize that email and extract the code with the existing `StudentCodeExtractor` rule.
3. Find by normalized email and by normalized student code. Bind only when both refer to one Student, that Student has no subject, and the new subject is not owned by another profile.
4. Lock the Student row in the binding transaction. Write `cognitoSub`; change only `PENDING` to `ACTIVE`; retain email, code, TeamMember and every role.
5. If neither email nor code matches, retain the historical behaviour of creating a new `PENDING` Student. Any partial match remains a conflict; no ambiguous profile is created.

| Situation | Result |
|---|---|
| Email and code identify one unlinked Student | bind subject; `PENDING -> ACTIVE` |
| Existing matching subject | reuse same local profile id |
| Email match only / code match only | 409 conflict |
| Email and code identify different Students | 409 conflict |
| Target Student has another subject | 409 conflict |
| New subject belongs to Admin/Lecturer/other Student | 409 conflict |
| ACTIVE target | bind and remain ACTIVE |
| INACTIVE or SUSPENDED target | no bind/activation; 409 conflict |

The pessimistic lock plus existing identity uniqueness constraints prevents two competing requests from silently overwriting a Student. A database `DataIntegrityViolationException` is translated to a safe identity conflict.

## Invitation outbox and email text

V6 creates `student_course_invitation` with the database unique key `student_id + course_id + invitation_type`, state `PENDING/PROCESSING/SENT/FAILED`, attempt count, claim timestamp (`processing_started_at`), timestamps, failure code and optimistic version. V7 adds `Student.version` with an existing-row-safe default/backfill for the Student optimistic-lock mapping.

After commit, the processor claims and locks a record, builds a message and invokes `StudentInvitationDeliveryAdapter`.

- success: `SENT`;
- temporary/provider failure: `FAILED`, while Student/Team/TeamMember remain committed;
- retry job processes `PENDING` and `FAILED` records, with a five-attempt cap;
- a `PROCESSING` record is recovered only after `processing_started_at` is older than the configured timeout; a newer claim is never reclaimed;
- `SENT` records are not sent again by this policy.

The claim transaction commits before the delivery adapter is called, so it does not hold a database lock during provider I/O. Delivery is **at-least-once**, not exactly-once: if a process stops after Gmail SMTP accepts a message but before `SENT` is persisted, stale recovery can make a later attempt. Gmail SMTP does not add a provider idempotency key, so duplicate delivery remains possible in this crash window.

For a linked Student, the UTF-8 text/HTML message names the Course, includes Team when available, explains that the profile is linked and uses CTA `Đăng nhập SAGA`. For an unlinked Student, it explains that a local profile exists but the account is not activated/linked, asks the recipient to register or sign in with exactly the email that received the message, describes existing first-login binding and uses CTA `Đăng ký / Kích hoạt tài khoản SAGA`. Both use the configured login URL and contain no password, provider token, Cognito subject, cookie, CSRF value, database id/UUID, membership token or invite token. They do not promise a specific identity provider.

## Configuration and provider boundary

| Property | Environment variable | Meaning |
|---|---|---|
| `app.student-invitation.login-url` | `STUDENT_INVITATION_LOGIN_URL` | absolute HTTP(S) front-end login URL or backend `/api/auth/login` |
| `app.student-invitation.retry-delay-ms` | `STUDENT_INVITATION_RETRY_DELAY_MS` | retry scheduler interval |
| `app.student-invitation.processing-timeout-ms` | `STUDENT_INVITATION_PROCESSING_TIMEOUT_MS` | age after which a `PROCESSING` claim may be safely recovered; default 300000 ms |
| `spring.mail.host` | `SPRING_MAIL_HOST` | Gmail SMTP host; required for SMTP adapter |
| `spring.mail.port` | `SPRING_MAIL_PORT` | SMTP port; default 587 |
| `spring.mail.username` | `SPRING_MAIL_USERNAME` | authenticated Gmail account and message FROM address |
| `spring.mail.password` | `SPRING_MAIL_PASSWORD` | deployment secret; never source/log/API data |
| `spring.mail.properties.mail.smtp.auth` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | must be true for Gmail adapter availability |
| `spring.mail.properties.mail.smtp.starttls.enable` | `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | must be true for Gmail adapter availability |

No URL is hard-coded; `/auth/callback` is not used to begin login. The repository contains Boot-managed Spring Mail and `GmailSmtpStudentInvitationDeliveryAdapter`. When any required Gmail setting or `JavaMailSender` is missing, configuration selects the unavailable adapter without failing application startup or inventing credentials. SMTP connect/read/write timeouts are bounded at 5000/10000/10000 ms and startup connection testing is disabled. Source/test behavior is **CONFIRMED_SOURCE_TEST**; real Gmail/Railway delivery remains **TBD_DEPLOYMENT_SMOKE**.

## Update 2026-08-04 — callback redirect isolation

- **CONFIRMED:** OAuth completion hands a safe short-lived session result to frontend via opaque `resultId`; it does not create or change Student, Course, TeamMember, invitation outbox, provisioning, migration or email data.
- **CONFIRMED:** Personal result consumption is Student-only and the POST consume endpoint remains authenticated and CSRF-protected. No provider credential or OAuth state enters invitation/provisioning data.

## Ghi chú isolation cho sync (2026-08-04)

Chuẩn hóa UTC cho operational timestamp và GitHub claim/finalization/stale
recovery không thay đổi Student, TeamMember, Course invitation, identity
provisioning, invitation outbox, browser session hay CSRF. Không có migration.
GitHub state được update từ row managed có lock thay vì detached entity; job stale
được finalize an toàn và idempotent. Maven: **70 suites / 299 tests / 0 failures /
0 errors / 0 skipped**. Row production cũ vẫn cần quan sát sau deploy; không ghi
production id hay secret trong tài liệu.

## Migration and deployment status

**CONFIRMED from source:** Flyway V6 and V7 must run before production Hibernate
`ddl-auto=validate`; V7 is required by the mapped non-null `Student.version`.

**Runtime fact (user-provided):** a Railway deployment previously failed because
the database lacked `student.version`. This repository contains no Railway log or
dashboard evidence that production has since applied V6/V7, so production
migration status is **TBD**, not CONFIRMED.

## Limits and verification

**CONFIRMED:** provisioning keeps multi-course memberships and their per-Team LEADER/MEMBER roles intact; a leader's project permission remains scoped to that Team through existing authorization services.

**PARTIAL/TBD:** Excel header/schema validation, preview, row-error DTO, production Cognito self-sign-up evidence, and a database invariant directly enforcing `UNIQUE(student_id, course_id)` are not implemented. Product Owner has accepted the business rule: multiple Courses are allowed, but a Student may not be in two Teams of the same Course. The application guard protects write paths that follow it; it does not automatically repair/delete/merge legacy invalid data.

Invitation outbox serves delivery, not Course enrollment. Course roster must not use it as an enrollment source: current membership evidence is `TeamMember -> Team -> Course`. There is no modeled Student–Course relation for a Student without Team, so `studentsWithoutTeam`/`hasTeam=without` remain **PARTIAL** and must be empty. Current baseline `CourseService#getCourseRoster` still reads invitation outbox and fails the DEC-023 contract test; this source drift must not be treated as enrollment behavior.

`GET /api/me/courses/{courseId}/team/members` is a separate STUDENT self-scoped
read API. It resolves the Student from `SagaPrincipal.localProfileId`, confirms the
Course, and reads all TeamMember rows for Student+Course. No membership is 404;
legacy multiple memberships are 409 and are not selected, deleted or merged. The
single valid membership returns its resolved teamId, Team/Project summary and paged
`TeamMemberResponse`; it does not create membership and does not use the invitation
outbox as enrollment. GET uses the existing browser session and needs no CSRF.

Jira label snapshot persistence is separate from import/provisioning: it does not create, delete or change Student, TeamMember, invitation or identity data. Labels remain internal Task classification data; no Task HTTP API or Jira task creation API is introduced.

Tests cover matching/conflicts/status/idempotency, competitive bind, multi-course role preservation, import rollback/dedup, linked/unlinked text+HTML, mocked `JavaMailSender`, missing-config fallback, provider failure, concurrent claims, stale recovery, retry, SENT no-resend and membership preservation. `CourseTeamMembershipGuardIntegrationTest` also covers same-Team idempotency/role preservation, same-Course conflict 409, independent roles in different Courses, HTTP conflict and two independent competing transactions with a fresh final query. `MyCourseTeamMembersIntegrationTest` covers Student self-scope, no membership/legacy data, project nullable, privacy, pagination and OpenAPI. Targeted invitation/import tests pass **37/37**. Full `./mvnw.cmd clean test` at the current working tree runs **116 suites / 753 tests / 1 failure / 0 errors / 0 skipped**; the only failure is the pre-existing Course roster/DEC-023 source drift, outside Gmail delivery.

## Global admin pre-provisioning M7 — 2026-08-09

`POST /api/admin/users/import` độc lập với Course import. ADMIN gửi CSRF-protected
multipart `role=STUDENT` và XLSX một sheet header exact `studentCode,email,fullName`.
`StudentIdentityNormalizer` trim/upper code, trim/lower email. New Student PENDING có
`cognitoSub = null`; không có Course/Team/membership/invitation. First login chỉ bind khi
email và code cùng trỏ record, sau đó PENDING thành ACTIVE.

Toàn bộ workbook parse/validate/preflight trước write: header, required field, formula,
duplicate normalized row, partial identity và cross-profile email đều bị chặn; invalid file
400, local identity conflict 409, không partial success. Không thay parser/semantics Course
import; invitation chỉ do Course/Team flow tạo và không phải enrollment.

## Course import I1 hardened contract — 2026-08-09

`POST /api/v1/courses/{courseId}/import-students` là Course provisioning flow, độc lập với M7 Admin global import. Request là `multipart/form-data` với field `file`; FE gửi browser cookie/session và CSRF, không bearer. Success vẫn là `200` text `Import danh sách sinh viên thành công!`.

Workbook là `.xlsx`, tối đa 1 MiB; sheet đầu tiên có header exact theo thứ tự: `Class`, `RollNumber`, `Email`, `MemberCode`, `FullName`, `Group`, `Leader`. Header có thiếu/thừa/sai case/sai thứ tự bị từ chối. Mọi formula ở header, data và cell extra bị reject, không được evaluate. Tối đa 1.000 data rows; `RollNumber`, `Email`, `FullName` phải non-blank. Không gửi lại cell, row hay workbook trong lỗi.

Email được trim/lower và student code trim/upper. Một duplicate trong file, identity partial/split với local record, hoặc membership Team khác cùng Course đều dừng toàn transaction trước write. Student existing chỉ reuse, không rewrite fullName/status/Cognito subject và không reactivate. Group trống giữ behavior cũ: không TeamMember/invitation; Group không trống dùng `Group {value}`, leader mark `x` là LEADER, giá trị khác là MEMBER. Same Team idempotent giữ role; nhiều Course vẫn hợp lệ với role riêng.

Mã lỗi safe: 400 `MALFORMED_WORKBOOK`, `FILE_TOO_LARGE`, `INVALID_HEADER`, `FORMULA_NOT_ALLOWED`, `INVALID_ROW`, `DUPLICATE_IN_FILE`, `ROW_LIMIT`; 409 `IDENTITY_CONFLICT`, `COURSE_TEAM_MEMBERSHIP_CONFLICT`. Invitation outbox được giữ nguyên và chỉ enqueue sau TeamMember; không có provider/Cognito call mới.
