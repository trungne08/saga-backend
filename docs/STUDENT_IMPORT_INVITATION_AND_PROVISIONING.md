# Student import, invitation, and first-login provisioning

Status: **PARTIAL** — HEAD là `c351ae9` (`cập nhật docs`); working tree có endpoint Student self-scoped, DTO, reuse service, integration test và sáu Markdown chưa commit. Source/config/test là evidence. No secret is recorded here.

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

The claim transaction commits before the delivery adapter is called, so it does not hold a database lock during provider I/O. Delivery is **at-least-once**, not exactly-once: if a process stops after a provider accepts a message but before `SENT` is persisted, stale recovery can make a later attempt. A production adapter would need provider-side idempotency to offer stronger delivery semantics.

For a linked Student, the message says they were added to Course/Team and asks them to sign in. For an unlinked Student, it asks them to sign in or register with the same imported email; it may mention Google only when the Cognito deployment supports it. Both use the subject `You have been added to Course {courseName}` and contain no password, provider token, Cognito subject, cookie, CSRF value, database id, membership token or invite token.

## Configuration and provider boundary

| Property | Environment variable | Meaning |
|---|---|---|
| `app.student-invitation.login-url` | `STUDENT_INVITATION_LOGIN_URL` | absolute HTTP(S) front-end login URL or backend `/api/auth/login` |
| `app.student-invitation.retry-delay-ms` | `STUDENT_INVITATION_RETRY_DELAY_MS` | retry scheduler interval |
| `app.student-invitation.processing-timeout-ms` | `STUDENT_INVITATION_PROCESSING_TIMEOUT_MS` | age after which a `PROCESSING` claim may be safely recovered; default 300000 ms |

No URL is hard-coded; `/auth/callback` is not used to begin login. The repository does not contain a mail SDK, mail provider configuration or production adapter. Its default adapter marks delivery unavailable safely. Choosing/configuring a production provider is **TBD** and does not alter the transaction contract.

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

Invitation outbox serves delivery, not Course enrollment. Course roster must not use it as an enrollment source: current membership evidence is `TeamMember -> Team -> Course`. There is no modeled Student–Course relation for a Student without Team, so `studentsWithoutTeam`/`hasTeam=without` remain **PARTIAL** and empty; they must not be inferred from invitations.

`GET /api/me/courses/{courseId}/team/members` is a separate STUDENT self-scoped
read API. It resolves the Student from `SagaPrincipal.localProfileId`, confirms the
Course, and reads all TeamMember rows for Student+Course. No membership is 404;
legacy multiple memberships are 409 and are not selected, deleted or merged. The
single valid membership returns its resolved teamId, Team/Project summary and paged
`TeamMemberResponse`; it does not create membership and does not use the invitation
outbox as enrollment. GET uses the existing browser session and needs no CSRF.

Tests cover matching/conflicts/status/idempotency, competitive bind, multi-course role preservation, import rollback/dedup, outbox template/dedup, concurrent claims, stale recovery, retry and delivery failure. `CourseTeamMembershipGuardIntegrationTest` also covers same-Team idempotency/role preservation, same-Course conflict 409, independent roles in different Courses, HTTP conflict and two independent competing transactions with a fresh final query. `MyCourseTeamMembersIntegrationTest` covers Student self-scope, no membership/legacy data, project nullable, privacy, pagination and OpenAPI. The current working-tree `./mvnw.cmd test` result is **55 suites / 257 tests / 0 failures / 0 errors / 0 skipped**.
