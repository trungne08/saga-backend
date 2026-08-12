# SAGA Graph Design

## 1. Purpose

Tài liệu này mô tả thiết kế cụ thể cho hệ thống graph của SAGA:

- Overview activity graph
- Student interaction graph
- Heatmap hoạt động
- Burndown chart
- Contribution overview
- Member comparison

Mục tiêu là biến dữ liệu học tập, code, review, task và offline activity thành một lớp analytics có thể quan sát, so sánh và cảnh báo sớm.

---

## 2. Graph Architecture

### 2.1 Data source

- `Student`
- `Instructor`
- `Course`
- `Team`
- `Sprint`
- `Task`
- `Issue`
- `PullRequest`
- `Commit`
- `File`
- `Comment`
- `Review`
- `Meeting`
- `Document`
- `Chat`

### 2.2 Core edge types

- `ASSIGNED_TO`
- `AUTHORED`
- `REVIEWED`
- `COMMENTED_ON`
- `MENTIONED`
- `COLLABORATED_WITH`
- `ATTENDED`
- `WROTE`
- `RESOLVES`

### 2.3 Storage options

**Option A: Relational projection**

- Lưu node/edge vào bảng phụ trong MySQL/PostgreSQL.
- Phù hợp khi muốn tận dụng hạ tầng hiện có.

**Option B: Graph database**

- Dùng Neo4j cho traversal, centrality, community detection.
- Backend vẫn giữ DB chính làm source of truth.

**Recommendation:** bắt đầu bằng Option A, sau đó nâng cấp sang Neo4j nếu cần truy vấn graph sâu.

---

## 3. Graph 1 - Overview Activity Graph

### 3.1 Goal

Hiển thị tổng quan hoạt động của lớp/nhóm theo thời gian.

### 3.2 Data source

- Commit
- Issue
- Pull Request
- Comment
- Review

### 3.3 Aggregation

- Group theo ngày/tuần/tháng
- Count số activity theo type

### 3.4 API

`GET /api/analytics/overview?courseId=&teamId=&from=&to=`

### 3.5 Response shape

```json
{
  "labels": ["01/05", "02/05", "03/05"],
  "commits": [10, 12, 8],
  "issues": [4, 6, 3],
  "pullRequests": [2, 1, 4],
  "comments": [7, 9, 5],
  "reviews": [1, 2, 2]
}
```

### 3.6 UI

- Line chart
- Donut chart

### 3.7 Use case

- Xem team nào hoạt động mạnh
- Xem giai đoạn nào có spike activity

---

## 4. Graph 2 - Student Interaction Graph

### 4.1 Goal

Biểu diễn mạng tương tác giữa sinh viên.

### 4.2 Nodes

- Student

### 4.3 Edges

- `REVIEWED`
- `COMMENTED_ON`
- `MENTIONED`
- `ASSIGNED_TO`
- `COLLABORATED_WITH`

### 4.4 Edge weight

Weight = số lần tương tác giữa 2 student.

### 4.5 API

`GET /api/analytics/interactions?courseId=&teamId=&from=&to=`

### 4.6 Response shape

```json
{
  "nodes": [
    { "id": "s1", "name": "Minh", "team": "Team Alpha", "role": "Member", "degree": 12 }
  ],
  "edges": [
    { "source": "s1", "target": "s2", "type": "REVIEWED", "weight": 3 }
  ]
}
```

### 4.7 UI

- Force-directed graph

### 4.8 Use case

- Tìm người trung tâm
- Tìm người cô lập
- Xem nhóm tương tác mạnh

---

## 5. Graph 3 - Heatmap Activity

### 5.1 Goal

Hiển thị mức độ hoạt động theo ngày của từng sinh viên hoặc từng nhóm.
Heatmap dùng để nhìn nhanh:

- ngày nào hoạt động tăng mạnh
- ai đang hoạt động đều
- ai đang im lặng quá lâu
- sprint nào có dấu hiệu lệch tiến độ

### 5.2 Data source

- Commit
- PR
- Comment
- Review
- Issue activity
- Task status change
- Mention activity

### 5.3 Aggregation

Group theo:

- `student + date`
- `team + date`
- `activityType + date` nếu cần filter sâu

Value có thể là:

- số lượng activity
- trọng số activity
- điểm activity tổng hợp

### 5.3.1 Suggested scoring

- Commit = 3 điểm
- PR review = 2 điểm
- Comment = 1 điểm
- Issue update = 1.5 điểm
- Task status change = 1 điểm

Điểm tổng của một ô heatmap:

`score = Σ(activityWeight)`

### 5.4 API

`GET /api/analytics/heatmap?courseId=&teamId=&from=&to=&type=`

### 5.4.1 Suggested query params

- `courseId`: lọc theo course
- `teamId`: lọc theo team
- `from`: ngày bắt đầu
- `to`: ngày kết thúc
- `type`: loại activity (`ALL`, `COMMIT`, `PR`, `COMMENT`, `REVIEW`, `ISSUE`, `TASK`)
- `granularity`: `DAY` hoặc `WEEK`

### 5.5 Response shape

```json
{
  "students": ["An", "Linh", "Minh"],
  "dates": ["01/05", "02/05", "03/05"],
  "values": [
    [3, 5, 2],
    [1, 2, 6],
    [4, 4, 1]
  ],
  "legend": {
    "min": 0,
    "max": 6,
    "unit": "activityScore"
  }
}
```

### 5.6 UI

- Heatmap matrix
- Color scale from low to high
- Tooltip on hover: student, date, score, activity breakdown
- Filters: team, time range, activity type
- Sort students by total score or alphabetical order

### 5.7 Use case

- Phát hiện ngày quá im lặng
- Phát hiện ngày overload
- Nhận biết member nào chỉ hoạt động dồn vào cuối sprint
- So sánh nhịp làm việc giữa các team

### 5.8 Implementation idea

1. Query raw events from commit/review/comment/task tables.
2. Normalize mọi event về `studentId`, `teamId`, `eventDate`, `eventType`.
3. Aggregate theo ngày và tính score.
4. Build matrix `students x dates`.
5. Return JSON cho frontend render heatmap.

### 5.9 Data rules

- Một event chỉ được tính một lần.
- Event không map được sang student thì không đưa vào heatmap cá nhân.
- Event ngoài khoảng thời gian lọc thì bỏ qua.
- Nếu một student không có event trong range thì vẫn giữ row với toàn 0.

### 5.10 Output interpretation

- Màu đỏ: activity cao
- Màu vàng: activity trung bình
- Màu xanh: activity thấp

---

## 6. Graph 4 - Burndown Chart

### 6.1 Goal

Theo dõi tiến độ sprint.

### 6.2 Data source

- Sprint
- Task
- Task status

### 6.3 Rule

- `remaining = total task chưa done`
- cập nhật theo từng ngày trong sprint

### 6.4 API

`GET /api/analytics/burndown?teamId=&sprintId=`

### 6.5 Response shape

```json
{
  "dates": ["01/05", "02/05", "03/05"],
  "idealRemaining": [100, 80, 60],
  "actualRemaining": [100, 92, 78],
  "doneCount": [0, 8, 22]
}
```

### 6.6 UI

- Line chart with 2 lines: ideal vs actual

### 6.7 Use case

- Xem sprint có trễ hay không
- Dự báo backlog có kịp hoàn thành không

---

## 7. Graph 5 - Contribution Overview

### 7.1 Goal

Hiển thị đóng góp cá nhân theo nhiều chiều.

### 7.2 Reuse from current code

- [TeamContributionController](../src/main/java/com/saga/be/controller/TeamContributionController.java)
- `TeamContributionService`

### 7.3 Metrics

- Commits
- Pull Requests
- PR Reviews
- Issue Activity
- Comments
- Task Completion %

### 7.4 API

`GET /api/v1/teams/{teamId}/contribution-evaluation`

### 7.5 Response extension

- `totalScore`
- `breakdown`
- `sprintBreakdown`

### 7.6 UI

- Radar chart

### 7.7 Use case

- Xem ai đóng góp nhiều
- Xem đóng góp theo loại activity

---

## 8. Graph 6 - Member Comparison

### 8.1 Goal

So sánh đóng góp giữa các thành viên trong team.

### 8.2 Data source

- Same as contribution overview

### 8.3 API

`GET /api/analytics/team-comparison?teamId=&from=&to=`

### 8.4 Response shape

```json
{
  "members": ["An", "Linh", "Minh"],
  "scores": [82, 76, 68],
  "breakdowns": [
    { "commits": 20, "reviews": 5, "comments": 10, "tasks": 8 },
    { "commits": 18, "reviews": 7, "comments": 8, "tasks": 6 }
  ]
}
```

### 8.5 UI

- Bar chart
- Stacked bar chart

### 8.6 Use case

- Xếp hạng thành viên
- So sánh theo từng loại đóng góp

---

## 9. Suggested Implementation Order

### Phase 1

- Contribution overview
- Member comparison
- Burndown chart

### Phase 2

- Heatmap
- Overview activity graph
- Student interaction graph

### Phase 3

- Optional Neo4j graph traversal

---

## 10. Recommended Stack

- **Backend:** Spring Boot
- **Database:** MySQL/PostgreSQL
- **Graph projection:** relational edge tables or Neo4j
- **Chart library:** ECharts or D3

---

## 10. Final Recommendation

Nên bắt đầu từ mô hình **relational projection + analytics API** vì:

- dễ tích hợp với SAGA hiện tại
- tận dụng DB hiện có
- dễ test
- đủ cho report và demo

Sau khi ổn định, nếu cần graph traversal sâu hơn thì mới chuyển sang Neo4j.
