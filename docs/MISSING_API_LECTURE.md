# Lecturer Analytics API còn thiếu

> **2026-08-15 — HISTORICAL vs IMPLEMENTED:** nội dung gốc bên dưới mô tả milestone Lecturer Analytics 2026-08-05 và các gap thời điểm đó. Trên merged `main` hiện hành, Lecturer Course Dashboard đã **IMPLEMENTED** các route sau (ADMIN/LECTURER, GET, session `JSESSIONID`, không CSRF, không Bearer):
>
> - `GET /api/v1/courses/{courseId}/dashboard/teams-progress`
> - `GET /api/v1/courses/{courseId}/dashboard/contribution-summary`
> - `GET /api/v1/courses/{courseId}/dashboard/trends`
> - `GET /api/v1/courses/{courseId}/dashboard/at-risk-summary`
>
> Early warning vẫn chỉ deterministic `OVERDUE_TASK`. **Không** implement GHOSTING / TOXIC_COMMUNICATION / TECHNICAL_DEBT trừ khi source sau này thêm.
>
> **STUDENT progress supersession (DEC-083):** historical text dưới đây và Lecturer Dashboard vẫn **STUDENT forbidden**. Exception hiện hành **chỉ** cho existing `GET /api/v1/courses/{courseId}/students/{studentId}/progress`:
>
> - MEMBER: self 200; teammate 403
> - LEADER: self + exact same Team 200 (DEC-085: target extra Course membership không 409); cross-Team/cross-Course 403
> - MENTOR / no membership: 403
>
> Graph routes overview/heatmap/interactions/burndown đã được mở STUDENT LEADER/MEMBER từ DEC-080; không rewrite lịch sử đó. Không viết STUDENT có Lecturer Dashboard access. OpenAPI baseline hiện hành = **150**. File này không phải FE wishlist authority; ưu tiên `SAGA_SYSTEM_CONTEXT_FOR_AI.md` / `FRONTEND_API_INTEGRATION.md`.

Trạng thái tại `0156a5e` cộng working tree milestone Lecturer Analytics ngày 2026-08-05.
File yêu cầu này không tồn tại ở checkpoint ban đầu; nội dung dưới đây ghi lại contract đã triển khai từ yêu cầu milestone.

Verification: targeted Lecturer Analytics **21 tests pass**, Team roster security
**13 tests pass**; regression GitHub/Jira/Contribution **20 tests pass**; full Maven **77 suites / 339 tests /
0 failures / 0 errors / 0 skipped**.

## Nhóm 1 — Team và Student analytics

- **CONFIRMED:** Team detail, Student progress và recent activities đã có read API.
- **PARTIAL:** activities chỉ hợp nhất Commit và Document có timestamp; repository không lưu Jira status-transition history.

## Nhóm 2 — Contribution Flow / Peer Review / Slice Weights

**Ngoài phạm vi milestone này.** Không sửa controller, service, entity, test, migration hoặc tài liệu của nhóm 2.
Student Contribution Detail chỉ là adapter read-only tới aggregate hiện hữu sau khi kiểm Course ownership.

## Nhóm 3 — Early warnings

- **PARTIAL:** chỉ có signal deterministic `OVERDUE_TASK` từ `Task.dueDate` và trạng thái khác `DONE`.
- **TBD:** `NO_COMMIT_7_DAYS` và `NO_RECENT_ACTIVITY` chờ decision rõ về timestamp/cutoff; không có NLP, AI score hoặc prediction.

## Nhóm 4 — Interaction graph

- **PARTIAL:** edge có hướng chỉ được tạo từ Peer Review record thật và chỉ trả cho ADMIN/Lecturer sở hữu Course.
- Không coi hai Student cùng Team là interaction và không dựng Jira/PR comment giả.

## Nhóm 5 — Activity heatmap

- **PARTIAL:** aggregate Commit theo ngày UTC, trả đầy đủ row zero trong inclusive date range.
- Không có `level` vì chưa có threshold được chấp nhận; chưa gộp Task/Document.

## Nhóm 6 — Sprint velocity

- **PARTIAL:** `currentPlannedPoints` là tổng story point hiện tại, không phải snapshot commitment đầu Sprint.
- Task null story point bị loại khỏi point totals và được đếm riêng bằng `tasksWithoutStoryPoints`.
