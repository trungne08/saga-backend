# Lecturer Analytics API còn thiếu

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
