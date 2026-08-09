# Missing Admin APIs

## Account lifecycle M3B — hoàn thành 2026-08-09

`PATCH /api/admin/users/{id}/status` đã có. API chỉ hỗ trợ Student/Lecturer, không thêm status Admin, không nhận PENDING và không gọi Cognito. V21 backfill Lecturer ACTIVE; request-time local DB enforcement áp dụng cho business API. API không còn missing.

## AccountStatus M3A — policy chưa đủ, 2026-08-09

`PATCH /api/admin/users/{id}/status` là **MISSING CÓ CHỦ ĐÍCH**. Chỉ Student có `AccountStatus`; source không chứng minh transition Admin được phép, self-target/last-admin policy hay request-time enforcement. Không thêm endpoint, schema Admin/Lecturer, Cognito Admin API hoặc arbitrary status mutation cho đến khi có business decision.

## Course M2B — hoàn thành 2026-08-09

`PUT /api/v1/courses/{id}` và `DELETE /api/v1/courses/{id}` đã có, ADMIN-only và CSRF-protected. DELETE là soft-delete V20, chặn 409 khi có Team, Project, StudentCourseInvitation hoặc TaskWeightConfig; không hard-delete/cascade. Course Update/Delete không còn là missing API.

## Semester M2A — hoàn thành 2026-08-09

`PUT /api/v1/semesters/{id}` và `DELETE /api/v1/semesters/{id}` đã có, ADMIN-only và CSRF-protected. DELETE là soft-delete V19, chặn 409 khi có Course reference; không có hard-delete/cascade. Semester Update/Delete không còn là missing API.

## Trạng thái sau Admin Read Foundation — 2026-08-09

Đã có năm read API Admin: `/api/admin/users`, `/audit-logs`, `/system-stats`,
`/teams`, `/projects`. Chúng read-only, local-store-only và Admin-only.

Không có Admin mutation, `DELETE /api/projects/{projectId}`, thay đổi account-status
policy, entity/schema/migration trong milestone này. Subject và Class CRUD đã tồn tại
ở source hiện hành nên không reimplement. Các API Admin mutation/retention policy là
**TBD**, cần thiết kế authorization, dependency guard và retention riêng.

## Rubric Admin M4-R2 — không thêm CRUD, 2026-08-09

Không tạo `POST`, `PUT` hoặc `DELETE /api/admin/peer-review-rubrics`. V22 chỉ repair
nullable schema cho **EXISTING_BASELINED_DB_UPGRADE**, không seed rubric và không thay
Peer Review. **REPLAY_FROM_EXTERNAL_V1_BASELINE** cần baseline/compatibility decision;
**TRUE_EMPTY_DATABASE_BOOTSTRAP** là `BLOCKED_EXISTING_BASELINE_GAP`. Admin Rubric CRUD
vẫn là missing có chủ đích cho đến khi policy cấu trúc, retention và migration replay
được chốt.

Runtime verification 2026-08-09 xác nhận V22 `SUCCESS`, rubric `subject_id` nullable
và 0 row. Duplicate FK vẫn tồn tại nhưng không chặn repair; không cleanup FK, seed
rubric hoặc mở Admin CRUD.

## Rubric Admin M4B — CRUD global active, 2026-08-09

Thay thế trạng thái M4-R2 phía trên cho phạm vi M4B: đã có `POST`, `PUT`,
`DELETE /api/admin/peer-review-rubrics` dành riêng cho ADMIN session + CSRF. Chỉ
rubric global active (`subject_id NULL`, `deleted_at NULL`) nằm trong scope; không
có API batch hay CRUD rubric theo Subject.

DELETE là soft-delete và giữ history; active global tối đa 4, có thể là 0.
Không suy diễn ràng buộc 100% hay uniqueness. V23 production migration đã được
CONFIRMED runtime thành công.

## Admin Course progress overview M5 — hoàn thành 2026-08-09

`GET /api/admin/course-progress-overview` đã cung cấp overview read-only phân trang
theo Course active; nhận `keyword`, `semesterId`, `lecturerId`. Contract chỉ công bố
local current counts Team, Student distinct, Project, Sprint active/non-deleted theo
state và PeerReview. Không có Assessment status/finalization, grade, completion
percentage hay Contribution calculation toàn hệ thống.

## Admin Course report export M6 — hoàn thành 2026-08-09

`GET /api/admin/reports/courses/{courseId}/export` đã có cho ADMIN session. Endpoint
trả attachment XLSX local-only, gồm Course, Team Members, Sprints, Tasks và raw
Peer Reviews không comment. Không có Assessment/final grade/Contribution sheet, không
gọi provider và không export email, Cognito subject hay credential.
