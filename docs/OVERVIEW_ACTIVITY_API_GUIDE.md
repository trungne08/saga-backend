# Overview Activity API Usage Guide

## 1. Endpoint

`GET /api/v1/courses/{courseId}/teams/{teamId}/overview`

## 2. Query Parameters

- `startDate` *(required)*: ngày bắt đầu, định dạng `YYYY-MM-DD`
- `endDate` *(required)*: ngày kết thúc, định dạng `YYYY-MM-DD`

## 3. Example Request

```http
GET /api/v1/courses/{courseId}/teams/{teamId}/overview?startDate=2026-08-01&endDate=2026-08-31
```

## 4. Response Structure

Response trả về dữ liệu tổng quan hoạt động của cả team trong khoảng thời gian đã chọn.

### 4.1 Main fields

- `courseId`
- `teamId`
- `startDate`
- `endDate`
- `days`
- `totals`

### 4.2 Day fields

Mỗi phần tử trong `days` gồm:

- `date`
- `commits`
- `peerReviews`
- `comments`
- `documents`
- `tasks`
- `totalActivities`
- `totalScore`

### 4.3 Totals fields

`totals` gồm các trường:

- `commits`
- `peerReviews`
- `comments`
- `documents`
- `tasks`
- `totalActivities`
- `totalScore`

## 5. Meaning of the Score

Score được tính theo cùng rule với heatmap:

- Commit = 3 điểm
- PR review = 2 điểm
- Comment = 1 điểm
- Document = 1 điểm
- Task = 2 điểm

## 6. Frontend Rendering Guide

1. Gọi API với `courseId`, `teamId`, `startDate`, `endDate`.
2. Dùng `days` để vẽ biểu đồ đường, cột, hoặc area chart theo thời gian.
3. Dùng `totalScore` để biểu diễn mức độ hoạt động tổng hợp mỗi ngày.
4. Dùng `totalActivities` nếu muốn hiển thị số lượng hoạt động thực tế.
5. Dùng `totals` để hiển thị thẻ summary ở đầu màn hình.

## 7. Common Usage

- xem team hoạt động mạnh hay yếu theo từng ngày
- phát hiện ngày team bị “đứng”
- so sánh nhịp hoạt động giữa các sprint hoặc mốc thời gian
- hỗ trợ giảng viên hỏi nhanh team đang làm đến đâu

## 8. Notes

- API này tổng hợp dữ liệu của toàn team, không tách theo từng sinh viên.
- Nếu `startDate` lớn hơn `endDate`, API sẽ trả lỗi.
- Dữ liệu chỉ phản ánh các nguồn hoạt động đã được hệ thống ghi nhận.

## 9. Authorization

ADMIN đọc mọi Team hợp lệ; LECTURER chỉ đọc Team thuộc Course mình phụ trách; STUDENT chỉ đọc exact Team có `TeamMember.roleInTeam=LEADER` hoặc `MEMBER`. MENTOR và Student Team khác bị từ chối. Course và Team trong URL phải khớp nhau.

Frontend dùng browser session với `credentials: "include"`, không dùng Bearer token và không gửi CSRF cho GET. Quyền này không mở các Lecturer Analytics route khác.
