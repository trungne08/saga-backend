# Student import, invitation, and first-login provisioning

Status: **PARTIAL** — implemented in the working tree on `main`, audited from HEAD `d400162`; not committed or pushed. Source/config/test are the evidence. No secret is recorded here.

## Import and access flow

`POST /api/v1/courses/{courseId}/import-students` still uses browser `JSESSIONID` plus CSRF.

1. ADMIN may import any Course; LECTURER must be that Course's instructor; STUDENT is forbidden.
2. Excel values are normalized: email is trimmed/lowercased and student code is trimmed/uppercased.
3. Import reuses a Student only when normalized email and code resolve to the same record. A partial or split match fails the transaction safely.
4. Import creates/reuses Team and TeamMember. The Student starts `PENDING` when newly created.
5. Once a TeamMember exists, import enqueues a course invitation in the same transaction. Rollback creates neither membership nor invitation.

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

`student_course_invitation` (V6) is an outbox record with a unique key of `student_id + course_id + invitation_type`, state `PENDING/PROCESSING/SENT/FAILED`, attempt count, claim timestamp (`processing_started_at`), timestamps, failure code and optimistic version.

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

## Limits and verification

**CONFIRMED:** provisioning keeps multi-course memberships and their per-Team LEADER/MEMBER roles intact; a leader's project permission remains scoped to that Team through existing authorization services.

**PARTIAL/TBD:** Excel header/schema validation, preview, row-error DTO, production Cognito self-sign-up evidence, and a database unique constraint for `team_member(team_id, student_id)` are not implemented. The business rule for multiple Teams in one Course is unchanged and remains TBD.

Tests cover matching/conflicts/status/idempotency, competitive bind, multi-course role preservation, import rollback/dedup, outbox template/dedup, concurrent claims, stale recovery, retry and delivery failure. The final full-suite count is recorded in the repository state documents after the complete Maven run.
