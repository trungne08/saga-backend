# Burndown API Usage Guide

## 1. Endpoint

`GET /api/v1/courses/{courseId}/teams/{teamId}/sprints/{sprintId}/burndown`

## 2. Mục đích

API này trả về biểu đồ burndown của một sprint trong một team. Nó giúp giảng viên hoặc frontend theo dõi:

- tiến độ thực tế còn lại theo từng ngày
- đường ideal remaining theo thời gian
- số task đã hoàn thành đến từng ngày
- tổng scope của sprint

## 3. Request

### 3.1 Path parameters

- `courseId`: UUID của khóa học
- `teamId`: UUID của team
- `sprintId`: UUID của sprint

### 3.2 Example request

```http
GET /api/v1/courses/11111111-2222-3333-4444-555555666666/teams/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee/sprints/12345678-90ab-cdef-1234-567890abcdef/burndown
```

## 4. Response structure

Response trả về một object `BurndownChart`:

```json
{
  "courseId": "11111111-2222-3333-4444-555555666666",
  "teamId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "sprintId": "12345678-90ab-cdef-1234-567890abcdef",
  "sprintName": "Sprint 3",
  "startDate": "2026-08-01",
  "endDate": "2026-08-07",
  "totalScope": 10,
  "points": [
    {
      "date": "2026-08-01",
      "idealRemaining": 10,
      "actualRemaining": 10,
      "doneCount": 0
    },
    {
      "date": "2026-08-02",
      "idealRemaining": 8,
      "actualRemaining": 9,
      "doneCount": 1
    },
    {
      "date": "2026-08-03",
      "idealRemaining": 6,
      "actualRemaining": 7,
      "doneCount": 2
    }
  ]
}
```

### 4.1 Các trường chính

- `courseId`: mã khóa học
- `teamId`: mã team
- `sprintId`: mã sprint
- `sprintName`: tên sprint
- `startDate`: ngày bắt đầu sprint
- `endDate`: ngày kết thúc sprint
- `totalScope`: tổng số task trong sprint
- `points`: danh sách điểm dữ liệu burndown theo từng ngày

### 4.2 Mỗi phần tử trong `points`

Mỗi item trong `points` gồm:

- `date`: ngày cần đánh giá
- `idealRemaining`: số task còn lại theo đường lý tưởng
- `actualRemaining`: số task còn lại thực tế tính đến cuối ngày đó
- `doneCount`: số task đã hoàn thành tính đến ngày đó

## 5. Cách tính

API sử dụng task trong sprint đó và tính theo từng ngày từ `startDate` đến `endDate`.

- `idealRemaining`: dựa trên đường giảm tuyến tính từ `totalScope` xuống `0`
- `actualRemaining`: số task còn đang mở tính đến cuối ngày
- `doneCount`: số task đã hoàn thành tại thời điểm đó

Theo logic hiện tại:

- task được coi là đang mở nếu nó chưa hoàn thành trước thời điểm kết thúc ngày
- task được coi là done nếu trạng thái đã là `DONE` hoặc đã được đánh dấu hoàn thành trước đó

## 6. Frontend rendering guide

### 6.1 Vẽ biểu đồ 2 đường

- trục X: ngày trong sprint
- trục Y: số task còn lại
- đường 1: `idealRemaining`
- đường 2: `actualRemaining`

### 6.2 Ý nghĩa quan sát

- nếu đường `actualRemaining` nằm cao hơn `idealRemaining`: team đang chậm tiến độ
- nếu đường `actualRemaining` gần bằng hoặc thấp hơn `idealRemaining`: tiến độ tốt
- khoảng cách giữa hai đường phản ánh độ lệch giữa kế hoạch và thực tế

### 6.3 Hiển thị thêm summary

Có thể hiển thị các card:

- tổng scope: `totalScope`
- task completed hôm nay: `doneCount` của ngày hiện tại
- tiến độ cuối sprint: `totalScope - actualRemaining` trên `totalScope`

## 7. Ví dụ sử dụng trên frontend

```javascript
const response = await fetch(
  `/api/v1/courses/${courseId}/teams/${teamId}/sprints/${sprintId}/burndown`
);

const data = await response.json();

const chartData = data.points.map((point) => ({
  date: point.date,
  ideal: point.idealRemaining,
  actual: point.actualRemaining,
  done: point.doneCount,
}));
```

## 8. Common usage

- kiểm tra sprint có bị trễ tiến độ không
- xem team đang hoàn thành việc theo kế hoạch hay không
- hỗ trợ giảng viên hỏi nhanh: “Sprint này tiến độ còn mấy task chưa xong?”
- so sánh ideal line và actual line để đánh giá hiệu suất team

## 9. Notes

- API này chỉ lấy dữ liệu cho một sprint cụ thể.
- Nếu team chưa có `Project`, API sẽ trả lỗi.
- Nếu sprint không tồn tại hoặc không thuộc project của team, API sẽ trả `404`.
- Nếu `startDate` / `endDate` trong sprint thiếu hoặc không hợp lệ, API sẽ trả `400`.

## 10. Mẫu response đầy đủ (giả định)

```json
{
  "courseId": "11111111-2222-3333-4444-555555666666",
  "teamId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "sprintId": "12345678-90ab-cdef-1234-567890abcdef",
  "sprintName": "Sprint 3",
  "startDate": "2026-08-01",
  "endDate": "2026-08-07",
  "totalScope": 8,
  "points": [
    { "date": "2026-08-01", "idealRemaining": 8, "actualRemaining": 8, "doneCount": 0 },
    { "date": "2026-08-02", "idealRemaining": 7, "actualRemaining": 7, "doneCount": 1 },
    { "date": "2026-08-03", "idealRemaining": 6, "actualRemaining": 6, "doneCount": 2 },
    { "date": "2026-08-04", "idealRemaining": 5, "actualRemaining": 5, "doneCount": 3 },
    { "date": "2026-08-05", "idealRemaining": 4, "actualRemaining": 4, "doneCount": 4 },
    { "date": "2026-08-06", "idealRemaining": 2, "actualRemaining": 3, "doneCount": 5 },
    { "date": "2026-08-07", "idealRemaining": 0, "actualRemaining": 1, "doneCount": 7 }
  ]
}
```

## 11. Kết luận

Burndown API là công cụ quan trọng để theo dõi tiến độ sprint của team. Nó phù hợp cho dashboard giảng viên, báo cáo tiến độ nhóm, và cảnh báo sớm khi team đang chậm so với kế hoạch.
