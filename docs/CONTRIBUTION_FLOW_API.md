# Contribution Flow API

Tài liệu này mô tả đúng các API hiện có trong code để FE tích hợp luồng:

- Peer review
- Xem đánh giá đóng góp
- Xem/đề nghị thay đổi trọng số slice theo course
- Duyệt đơn đổi trọng số

---

## 1) Peer review

### 1.1 Lấy 4 rubric mặc định

`GET /api/v1/peer-review-rubrics/default`

**Role:** ADMIN, LECTURER, STUDENT

**Mục đích:** FE lấy 4 tiêu chí mặc định trước khi render form đánh giá.

```json
{
  "criteria": [
    {
      "rubricId": "11111111-1111-1111-1111-111111111111",
      "criteriaName": "Hoàn thành & Chất lượng",
      "weight": 25,
      "description": "Làm đúng, đủ task được giao; code/chức năng chạy ổn định, ít lỗi."
    },
    {
      "rubricId": "22222222-2222-2222-2222-222222222222",
      "criteriaName": "Tiến độ & Quy trình",
      "weight": 25,
      "description": "Đáp ứng đúng deadline; đẩy/merge code kịp thời, không làm kẹt tiến độ chung."
    },
    {
      "rubricId": "33333333-3333-3333-3333-333333333333",
      "criteriaName": "Giao tiếp & Hỗ trợ",
      "weight": 25,
      "description": "Dễ liên lạc; chủ động phối hợp và sẵn sàng giúp đỡ đồng đội."
    },
    {
      "rubricId": "44444444-4444-4444-4444-444444444444",
      "criteriaName": "Thái độ & Xử lý sự cố",
      "weight": 25,
      "description": "Chịu trách nhiệm với công việc được giao; xử lý sự cố kịp thời và hiệu quả, cởi mở tiếp thu góp ý."
    }
  ]
}
```

**4 rubric mặc định trong DB (tiếng Việt)**
- Hoàn thành & Chất lượng (Làm đúng, đủ task được giao; code/chức năng chạy ổn định, ít lỗi.)
- Tiến độ & Quy trình (Đáp ứng đúng deadline; đẩy/merge code kịp thời, không làm kẹt tiến độ chung.)
- Giao tiếp & Hỗ trợ (Dễ liên lạc; chủ động phối hợp và sẵn sàng giúp đỡ đồng đội.)
- Thái độ & Xử lý sự cố (Chịu trách nhiệm với công việc được giao; xử lý sự cố kịp thời và hiệu quả, cởi mở tiếp thu góp ý.)

### 1.2 Lấy rubric theo team

`GET /api/v1/teams/{teamId}/peer-review-rubric`

**Role:** không gắn role cụ thể, nhưng người gọi phải có quyền đọc team.

**Response**
```json
{
  "teamId": "uuid",
  "subjectId": "uuid",
  "criteria": [
    {
      "rubricId": "11111111-1111-1111-1111-111111111111",
      "criteriaName": "Hoàn thành & Chất lượng",
      "weight": 25,
      "description": "Làm đúng, đủ task được giao; code/chức năng chạy ổn định, ít lỗi."
    },
    {
      "rubricId": "22222222-2222-2222-2222-222222222222",
      "criteriaName": "Tiến độ & Quy trình",
      "weight": 25,
      "description": "Đáp ứng đúng deadline; đẩy/merge code kịp thời, không làm kẹt tiến độ chung."
    },
    {
      "rubricId": "33333333-3333-3333-3333-333333333333",
      "criteriaName": "Giao tiếp & Hỗ trợ",
      "weight": 25,
      "description": "Dễ liên lạc; chủ động phối hợp và sẵn sàng giúp đỡ đồng đội."
    },
    {
      "rubricId": "44444444-4444-4444-4444-444444444444",
      "criteriaName": "Thái độ & Xử lý sự cố",
      "weight": 25,
      "description": "Chịu trách nhiệm với công việc được giao; xử lý sự cố kịp thời và hiệu quả, cởi mở tiếp thu góp ý."
    }
  ]
}
```

### 1.3 Lấy danh sách thành viên có thể đánh giá

`GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates`

**Role:** ADMIN, STUDENT  
**Mục đích:** trả danh sách thành viên để FE render form đánh giá, đã loại reviewer ra khỏi danh sách.

**Response**
```json
{
  "teamId": "uuid",
  "sprintId": "uuid",
  "reviewerId": "uuid",
  "candidates": [
    {
      "studentId": "uuid",
      "fullName": "Nguyen Van A",
      "studentCode": "SE001",
      "alreadyReviewed": false,
      "existingReviewId": null,
      "existingTotalStarRating": null
    }
  ]
}
```

### 1.4 Gửi peer review

`POST /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews`

**Role:** ADMIN, STUDENT

**Request body**
```json
{
  "revieweeId": "uuid",
  "starRating": 18,
  "criteriaRatings": [
    { "rubricId": "uuid", "starRating": 5 },
    { "rubricId": "uuid", "starRating": 4 },
    { "rubricId": "uuid", "starRating": 5 },
    { "rubricId": "uuid", "starRating": 4 }
  ],
  "comment": "Lam viec tot"
}
```

**Response**
```json
{
  "id": "uuid",
  "sprintId": "uuid",
  "sprintName": "Sprint 1",
  "reviewerId": "uuid",
  "reviewerName": "Student A",
  "revieweeId": "uuid",
  "revieweeName": "Student B",
  "starRating": 18,
  "criteriaRatings": [
    { "rubricId": "uuid", "criteriaName": "Communication", "starRating": 5 },
    { "rubricId": "uuid", "criteriaName": "Teamwork", "starRating": 4 },
    { "rubricId": "uuid", "criteriaName": "Quality", "starRating": 5 },
    { "rubricId": "uuid", "criteriaName": "Ownership", "starRating": 4 }
  ],
  "comment": "Lam viec tot",
  "createdAt": "2026-08-04T12:00:00",
  "updatedAt": "2026-08-04T12:00:00"
}
```

**Ghi chú**
- Nếu FE gửi `criteriaRatings` thì phải gửi đủ toàn bộ 4 rubric mặc định.
- Nếu không gửi `criteriaRatings`, FE phải gửi `starRating` tổng.
- `starRating` trong `criteriaRatings` là từ `0` đến `5`.

### 1.5 Xem peer reviews của sprint

`GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews`

**Role:** ADMIN, LECTURER, STUDENT

**Response**
```json
{
  "teamId": "uuid",
  "sprintId": "uuid",
  "sprintName": "Sprint 1",
  "reviews": [
    {
      "id": "uuid",
      "sprintId": "uuid",
      "sprintName": "Sprint 1",
      "reviewerId": "uuid",
      "reviewerName": "Student A",
      "revieweeId": "uuid",
      "revieweeName": "Student B",
      "starRating": 18,
      "criteriaRatings": [
        { "rubricId": "uuid", "criteriaName": "Communication", "starRating": 5 },
        { "rubricId": "uuid", "criteriaName": "Teamwork", "starRating": 4 },
        { "rubricId": "uuid", "criteriaName": "Quality", "starRating": 5 },
        { "rubricId": "uuid", "criteriaName": "Ownership", "starRating": 4 }
      ],
      "comment": "Lam viec tot",
      "createdAt": "2026-08-04T12:00:00",
      "updatedAt": "2026-08-04T12:00:00"
    }
  ]
}
```

---

## 2) Contribution evaluation

### Lấy kết quả % đóng góp của team

`GET /api/v1/teams/{teamId}/contribution-evaluation`

**Role:** ADMIN, LECTURER

**Response**
```json
{
  "teamId": "uuid",
  "projectId": "uuid",
  "evaluatedAt": "2026-08-04T12:00:00",
  "members": [
    {
      "studentId": "uuid",
      "fullName": "Nguyen Van A",
      "studentCode": "SE001",
      "codeContributionScore": 12.5,
      "documentContributionScore": 4.0,
      "designContributionScore": 0.0,
      "codeContributionPercentage": 32.1,
      "documentContributionPercentage": 10.2,
      "designContributionPercentage": 0.0,
      "peerReviewScore": 18.0,
      "taskContributionScore": 16.5,
      "taskContributionPercentage": 42.3,
      "finalContributionPercentage": 42.3,
      "evidenceCount": 7,
      "sprintBreakdowns": [
        {
          "sprintId": "uuid",
          "sprintName": "Sprint 1",
          "taskScore": 8.5,
          "retrospectiveMultiplier": 1.0,
          "adjustedTaskScore": 8.5,
          "peerReviewCount": 3
        }
      ],
      "warnings": []
    }
  ]
}
```

**Ghi chú**
- `finalContributionPercentage` là % cuối cùng để hiển thị cho giảng viên.
- `code/document/design` là breakdown theo slice.
- `taskContributionPercentage` là % contribution phần task sau normalize.
- Response DTO thực tế chỉ gồm các field trong `TeamContributionMemberResponse`.

---

## 3) Slice weight theo course

### 3.1 Xem trọng số hiện tại

`GET /api/v1/courses/{courseId}/contribution-slice-weights`

**Role:** ADMIN, LECTURER

**Response**
```json
{
  "courseId": "uuid",
  "courseCode": "SAGA101",
  "courseName": "Software Engineering",
  "codeWeight": 33.3333333333,
  "documentWeight": 33.3333333333,
  "designWeight": 33.3333333333
}
```

### 3.2 Gửi yêu cầu đổi trọng số

`POST /api/v1/courses/{courseId}/contribution-slice-weight-requests`

**Role:** LECTURER

**Request body**
```json
{
  "lecturerId": "uuid",
  "reason": "Course nay nhieu design hon code",
  "codeWeight": 30,
  "documentWeight": 20,
  "designWeight": 50
}
```

**Response**
```json
{
  "requestId": "uuid",
  "courseId": "uuid",
  "courseCode": "SAGA101",
  "courseName": "Software Engineering",
  "lecturerId": "uuid",
  "lecturerName": "Dr. A",
  "proposedCodeWeight": 30,
  "proposedDocumentWeight": 20,
  "proposedDesignWeight": 50,
  "reason": "Course nay nhieu design hon code",
  "status": "PENDING",
  "createdAt": "2026-08-04T12:00:00",
  "resolvedAt": null
}
```

### 3.3 Danh sách yêu cầu đổi trọng số

`GET /api/v1/courses/contribution-slice-weight-requests?status=PENDING&courseId={courseId}`

**Role:** ADMIN, LECTURER

### 3.4 Duyệt / từ chối yêu cầu

`PUT /api/v1/courses/contribution-slice-weight-requests/{requestId}/decision`

**Role:** ADMIN

**Request body**
```json
{
  "decision": "APPROVED",
  "note": "Dong y",
  "adminId": "uuid"
}
```

**Response**
```json
{
  "requestId": "uuid",
  "courseId": "uuid",
  "courseCode": "SAGA101",
  "courseName": "Software Engineering",
  "lecturerId": "uuid",
  "lecturerName": "Dr. A",
  "proposedCodeWeight": 30,
  "proposedDocumentWeight": 20,
  "proposedDesignWeight": 50,
  "reason": "Course nay nhieu design hon code",
  "status": "APPROVED",
  "createdAt": "2026-08-04T12:00:00",
  "resolvedAt": "2026-08-04T12:30:00"
}
```

---

## 4) Override đóng góp

### Gửi override cho team

`POST /api/v1/teams/{teamId}/contribution-override`

**Role:** ADMIN, LECTURER

**Request body**
```json
{
  "studentId": "uuid",
  "proposedPercentage": 45,
  "reason": "Ly do override",
  "lecturerId": "uuid"
}
```

**Response**
```json
{
  "requestId": "uuid",
  "studentId": "uuid",
  "proposedPercentage": 45,
  "status": "APPROVED",
  "message": "Contribution override applied successfully"
}
```

---

## 5) Luồng FE đề xuất

1. Student gọi `GET .../peer-reviews/candidates`
2. Student submit `POST .../peer-reviews`
3. Lecturer xem `GET .../peer-reviews`
4. Lecturer xem `GET .../contribution-evaluation`
5. Lecturer nếu cần thì gửi `POST .../contribution-slice-weight-requests`
6. Admin duyệt `PUT .../decision`
7. Lecturer/admin có thể dùng `POST .../contribution-override` khi cần chỉnh tay có lý do
8. FE lấy `GET /api/v1/projects/{projectId}/sprints` hoặc `GET /api/v1/teams/{teamId}/sprints` để chọn `sprintId` trước khi vào luồng contribution/peer review

### Quan trọng về `sprintId`

Các API contribution/peer review vẫn **nhận `sprintId` làm input**:
- `GET/POST /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews...`

FE nên gọi API list sprint trước để lấy danh sách `sprintId`, sau đó mới vào các endpoint bên dưới.

### 5.1 Lấy danh sách sprint theo project/team

`GET /api/v1/projects/{projectId}/sprints`

`GET /api/v1/teams/{teamId}/sprints`

**Role:** ADMIN, LECTURER, STUDENT  
**Response:** `SprintListResponse`

```json
{
  "projectId": "uuid",
  "teamId": "uuid",
  "sprints": [
    {
      "sprintId": "uuid",
      "sprintName": "Sprint 1",
      "externalSprintId": "JIRA-123",
      "startDate": "2026-08-01T00:00:00",
      "endDate": "2026-08-14T23:59:59",
      "goal": "Hoàn thành module contribution"
    }
  ]
}
```

---

## 6) Lưu ý

- File này bám theo code hiện tại, không mô tả endpoint giả định.
- Các response `Page<>` ở API roster/course không nằm trong tài liệu này.
- Nếu FE cần sample request/response chi tiết hơn cho từng DTO, mình sẽ tách tiếp ra theo từng màn hình.
