# Heatmap API Usage Guide

## 1. Endpoint

`GET /api/v1/courses/{courseId}/teams/{teamId}/heatmap`

## 2. Query Parameters

- `studentId` *(optional)*: xem heatmap của một sinh viên cụ thể
- `startDate` *(required)*: ngày bắt đầu, định dạng `YYYY-MM-DD`
- `endDate` *(required)*: ngày kết thúc, định dạng `YYYY-MM-DD`

## 3. Example Requests

### 3.1 Xem heatmap toàn team

```http
GET /api/v1/courses/{courseId}/teams/{teamId}/heatmap?startDate=2026-08-01&endDate=2026-08-31
```

### 3.2 Xem heatmap của một sinh viên

```http
GET /api/v1/courses/{courseId}/teams/{teamId}/heatmap?studentId={studentId}&startDate=2026-08-01&endDate=2026-08-31
```

## 4. Response Structure

Response trả về dữ liệu heatmap theo team hoặc theo từng sinh viên.

### 4.1 Main fields

- `courseId`
- `teamId`
- `studentId`
- `startDate`
- `endDate`
- `students`
- `days`

### 4.2 Student row fields

Mỗi phần tử trong `students` gồm:

- `studentId`
- `studentCode`
- `fullName`
- `commits`
- `peerReviews`
- `comments`
- `documents`
- `tasks`
- `totalActivities`
- `totalScore`
- `cells`

### 4.3 Cell fields

Mỗi phần tử trong `cells` gồm:

- `date`
- `commits`
- `peerReviews`
- `comments`
- `documents`
- `tasks`
- `totalActivities`
- `totalScore`

### 4.4 Day summary fields

Mỗi phần tử trong `days` gồm:

- `date`
- `commits`
- `peerReviews`
- `comments`
- `documents`
- `tasks`
- `totalActivities`
- `totalScore`

## 5. Meaning of the Score

Heatmap score dùng để biểu diễn mức độ hoạt động tổng hợp:

- Commit = 3 điểm
- PR review = 2 điểm
- Comment = 1 điểm
- Document = 1 điểm
- Task = 2 điểm

Màu sắc thường được hiểu như sau:

- xanh: hoạt động thấp
- vàng: hoạt động trung bình
- đỏ: hoạt động cao

## 6. Frontend Rendering Guide

1. Gọi API heatmap với `courseId`, `teamId`, `startDate`, `endDate`.
2. Dùng `students` làm trục dọc.
3. Dùng `cells` hoặc `days` để tạo lưới heatmap.
4. Dùng `totalScore` để tô màu ô.
5. Hiển thị tooltip với breakdown activity.

## 7. Common Usage

- xem sinh viên nào hoạt động mạnh
- phát hiện sinh viên im lặng lâu ngày
- so sánh nhịp làm việc giữa các thành viên
- phát hiện team có nguy cơ chậm tiến độ

## 8. Notes

- Nếu `studentId` không truyền, API trả heatmap cho toàn team.
- Nếu `studentId` có truyền, API chỉ trả heatmap của sinh viên đó.
- `startDate` không được sau `endDate`.

## 9. Authorization

ADMIN đọc mọi Team hợp lệ; LECTURER chỉ đọc Team thuộc Course mình phụ trách; STUDENT có exact `TeamMember` role `LEADER` hoặc `MEMBER` được đọc heatmap toàn Team. Nếu truyền `studentId`, target phải thuộc chính Team trong URL; target ngoài Team fail closed. MENTOR và Student Team khác không được cấp quyền.

Frontend dùng `credentials: "include"` với browser session, không Bearer token và không CSRF cho GET.

