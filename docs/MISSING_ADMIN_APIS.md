# Ma trận trạng thái Admin backend — A12 closure, 2026-08-09

Tài liệu này là ma trận trạng thái hiện hành từ controller/service/repository, không phải wishlist.
Các ghi chú milestone cũ được supersede khi mâu thuẫn với bảng này.

| Capability | Status | Endpoint/source thực tế | Giới hạn chốt |
| --- | --- | --- | --- |
| Global users | IMPLEMENTED | `GET /api/admin/users` | Local profile an toàn, phân trang; không Cognito subject. |
| Student/Lecturer status | IMPLEMENTED | `PATCH /api/admin/users/{id}/status` | ADMIN + CSRF; chỉ ACTIVE/INACTIVE/SUSPENDED; Admin target và PENDING bị từ chối; không Cognito/role mutation. |
| Global user import | IMPLEMENTED | `POST /api/admin/users/import` | Chỉ STUDENT/LECTURER, pre-provision local; không import Admin/Course/Team/Cognito. |
| Subject/Class | IMPLEMENTED | `POST/PUT/DELETE /api/v1/subjects`, `/api/v1/classes` | ADMIN mutation; soft-delete retention hiện hữu. |
| Semester | IMPLEMENTED | `POST/PUT/DELETE /api/v1/semesters` | Soft-delete; chặn Course và active-semester reference. |
| Course | IMPLEMENTED | `POST/PUT/DELETE /api/v1/courses` | Soft-delete; chặn Team/Project/invitation/weight config. |
| Active Semester | IMPLEMENTED | `GET/PUT /api/admin/settings/active-semester` | Typed singleton setting, không generic key/value. |
| Global rubric | IMPLEMENTED | `POST/PUT/DELETE /api/admin/peer-review-rubrics` | Chỉ global active; soft-delete, không sửa Subject rubric/history. |
| Course progress | IMPLEMENTED | `GET /api/admin/course-progress-overview` | Current local counts, không final grade/Assessment. |
| Course XLSX export | IMPLEMENTED | `GET /api/admin/reports/courses/{courseId}/export` | Local snapshot, không official grade/Cognito/provider data. |
| Global audit/statistics/health | IMPLEMENTED | `/api/admin/audit-logs`, `/system-stats`, `/integrations/health` | Sanitized/local-only; health không gọi provider. |
| Global teams/projects | IMPLEMENTED | `GET /api/admin/teams`, `/api/admin/projects` | Read-only; không Project DELETE. |
| A11A durable audit identity | PARTIAL | `SystemAuditLog.actorLocalProfileId`, `actorRole` | Chỉ event mới có exact local actor; không backfill Mongo. |
| Per-user audit history | BLOCKED | Không có endpoint | Historical coverage không complete; không tạo `GET /api/admin/users/{id}/audit-logs`. |
| Notification broadcast | BLOCKED | Không có controller/service/repository | `Notification` isolated; thiếu schema evidence, producer/consumer và audience/lifecycle contract. |
| Impersonation, role mutation, password reset | BLOCKED | Không có endpoint/contract | Không temporary token, Bearer hay Cognito Admin API. |
| Manual Course membership | BLOCKED | Không có Admin mutation | Team/Project/retention contract chưa đủ. |
| Generic system settings | BLOCKED | Không có generic endpoint/model | Active Semester là typed setting riêng; không gom rubric/contribution domain config. |

## Browser boundary

Mọi route Admin dùng browser `JSESSIONID` và `credentials: include`. GET không cần CSRF;
POST/PUT/PATCH/DELETE cần cookie `XSRF-TOKEN` cùng header `X-XSRF-TOKEN`. Không có Bearer.
Source/test integration xác nhận contract; browser E2E/deployed smoke là **TBD** trừ khi có evidence runtime riêng.

## Closure

`ADMIN_CORE_BACKEND_STATUS = COMPLETE` cho capability core ở bảng IMPLEMENTED.
`ADMIN_ADVANCED_SUPPORT_STATUS = DOCUMENTED_TBD_OR_BLOCKED`; đây không có nghĩa Admin 100% feature complete.
