# Hướng dẫn API Tính % Đóng Góp (Contribution Flow)

Tài liệu này giúp FE team tích hợp các API tính toán và quản lý phần trăm đóng góp của sinh viên trong dự án nhóm.

## Tổng quan luồng

1. **Sinh viên đánh giá peer review** → Lưu data
2. **Giảng viên xem kết quả peer review chi tiết** → Hiển thị 4 tiêu chí + sao
3. **Giảng viên lấy % đóng góp** → Hiển thị phần trăm từng thành viên
4. **Giảng viên yêu cầu thay đổi trọng số slice** → Gửi đơn + admin duyệt
5. **Giảng viên override % đóng góp ngay lập tức** → Áp dụng cho lớp

---

## 1. Lấy Kết Quả Peer Review Chi Tiết (Giảng viên xem)

### Endpoint
```
GET /api/v1/peer-review/team/{teamId}/detail?sprintId={sprintId}
```

### Mục đích
- Giảng viên xem **chi tiết từng tiêu chí** + số sao của peer review mỗi sprint
- Hiển thị 4 tiêu chí: Chất lượng kỹ thuật, Cộng tác, Giao tiếp, Quản lý thời gian

### Request
```bash
curl -X GET \
  'http://localhost:8080/api/v1/peer-review/team/1/detail?sprintId=5' \
  -H 'Authorization: Bearer {token}' \
  -H 'Accept: application/json'
```

### Response (Status 200)
```json
{
  "teamId": 1,
  "teamName": "Nhóm A",
  "sprintId": 5,
  "sprintName": "Sprint 1",
  "evaluations": [
    {
      "reviewedStudentId": 101,
      "reviewedStudentName": "Nguyễn Văn A",
      "studentCode": "SV001",
      "criteria": [
        {
          "id": 1,
          "name": "Chất lượng kỹ thuật",
          "stars": 4,
          "maxStars": 5
        },
        {
          "id": 2,
          "name": "Cộng tác",
          "stars": 5,
          "maxStars": 5
        },
        {
          "id": 3,
          "name": "Giao tiếp",
          "stars": 3,
          "maxStars": 5
        },
        {
          "id": 4,
          "name": "Quản lý thời gian",
          "stars": 4,
          "maxStars": 5
        }
      ],
      "totalStars": 16,
      "maxTotalStars": 20,
      "reviewCount": 3,
      "averageStars": 5.33,
      "notes": "Sinh viên này có tác phong tốt"
    }
  ]
}
```

### Lỗi
- **404**: Team không tồn tại
- **403**: Sinh viên không có quyền xem
- **400**: sprintId không hợp lệ

---

## 2. Lấy % Đóng Góp (Giảng viên xem)

### Endpoint
```
GET /api/v1/teams/{teamId}/contribution-evaluation?sprintId={sprintId}
```

### Mục đích
- Giảng viên xem **% đóng góp** của từng thành viên
- Hiển thị breakdown: Code %, Document %, Design %
- Hiển thị chi tiết: task score, peer review score, evidence count

### Request
```bash
curl -X GET \
  'http://localhost:8080/api/v1/teams/1/contribution-evaluation?sprintId=5' \
  -H 'Authorization: Bearer {token}' \
  -H 'Accept: application/json'
```

### Response (Status 200)
```json
{
  "teamId": 1,
  "teamName": "Nhóm A",
  "evaluationDate": "2026-08-04T18:08:09Z",
  "members": [
    {
      "studentId": 101,
      "studentName": "Nguyễn Văn A",
      "studentCode": "SV001",
      "email": "a@example.com",
      "role": "Developer",
      "codeContributionScore": 85,
      "codeContributionPercentage": 45.5,
      "documentContributionScore": 20,
      "documentContributionPercentage": 10.7,
      "designContributionScore": 0,
      "designContributionPercentage": 0.0,
      "finalContributionPercentage": 56.2,
      "taskContributionScore": 85,
      "peerReviewScore": 16,
      "evidenceCount": 12,
      "warnings": [],
      "sprintBreakdowns": [
        {
          "sprintId": 5,
          "sprintName": "Sprint 1",
          "taskScore": 85,
          "retrospectiveMultiplier": 1.0,
          "adjustedTaskScore": 85,
          "peerReviewCount": 3,
          "peerReviewAverageScore": 5.33
        }
      ]
    },
    {
      "studentId": 102,
      "studentName": "Trần Thị B",
      "studentCode": "SV002",
      "email": "b@example.com",
      "role": "Documentor",
      "codeContributionScore": 20,
      "codeContributionPercentage": 10.7,
      "documentContributionScore": 60,
      "documentContributionPercentage": 32.1,
      "designContributionScore": 0,
      "designContributionPercentage": 0.0,
      "finalContributionPercentage": 42.8,
      "taskContributionScore": 60,
      "peerReviewScore": 14,
      "evidenceCount": 8,
      "warnings": [],
      "sprintBreakdowns": [
        {
          "sprintId": 5,
          "sprintName": "Sprint 1",
          "taskScore": 60,
          "retrospectiveMultiplier": 1.0,
          "adjustedTaskScore": 60,
          "peerReviewCount": 3,
          "peerReviewAverageScore": 4.67
        }
      ]
    }
  ]
}
```

### Ghi chú
- **codeContributionPercentage**: % đóng góp phần Code
- **documentContributionPercentage**: % đóng góp phần Document
- **designContributionPercentage**: % đóng góp phần Design
- **finalContributionPercentage**: Tổng % đóng góp cuối cùng
- **Tổng % tất cả thành viên = 100%**

### Lỗi
- **404**: Team không tồn tại
- **403**: Không có quyền xem
- **400**: sprintId không hợp lệ

---

## 3. Xem Trọng Số Slices Hiện Tại

### Endpoint
```
GET /api/v1/courses/{courseId}/contribution-weights
```

### Mục đích
- Lấy trọng số hiện tại của từng slice (Code, Document, Design, Testing)
- Dùng để hiển thị thông tin hoặc chuẩn bị gửi yêu cầu thay đổi

### Request
```bash
curl -X GET \
  'http://localhost:8080/api/v1/courses/10/contribution-weights' \
  -H 'Authorization: Bearer {token}' \
  -H 'Accept: application/json'
```

### Response (Status 200)
```json
{
  "courseId": 10,
  "courseCode": "CS101",
  "courseName": "Nhập môn lập trình",
  "codeWeight": 40,
  "documentWeight": 30,
  "designWeight": 20,
  "testingWeight": 10
}
```

### Lỗi
- **404**: Course không tồn tại

---

## 4. Gửi Yêu Cầu Thay Đổi Trọng Số Slices (Giảng viên)

### Endpoint
```
POST /api/v1/courses/{courseId}/contribution-weight-request
```

### Mục đích
- Giảng viên gửi yêu cầu thay đổi trọng số slice của course
- Cần kèm lý do + số liệu mới
- Admin sẽ review và duyệt/từ chối

### Request Body
```json
{
  "reason": "Dự án này nặng về thiết kế giao diện, code ít hơn",
  "proposedCodeWeight": 30,
  "proposedDocumentWeight": 20,
  "proposedDesignWeight": 40,
  "proposedTestingWeight": 10
}
```

### Request
```bash
curl -X POST \
  'http://localhost:8080/api/v1/courses/10/contribution-weight-request' \
  -H 'Authorization: Bearer {token}' \
  -H 'Content-Type: application/json' \
  -d '{
    "reason": "Dự án này nặng về thiết kế giao diện, code ít hơn",
    "proposedCodeWeight": 30,
    "proposedDocumentWeight": 20,
    "proposedDesignWeight": 40,
    "proposedTestingWeight": 10
  }'
```

### Response (Status 201)
```json
{
  "requestId": 123,
  "courseId": 10,
  "courseCode": "CS101",
  "courseName": "Nhập môn lập trình",
  "requestedBy": {
    "userId": 201,
    "fullName": "TS. Nguyễn Văn Giảng Viên",
    "email": "lecturer@example.com"
  },
  "reason": "Dự án này nặng về thiết kế giao diện, code ít hơn",
  "currentCodeWeight": 40,
  "currentDocumentWeight": 30,
  "currentDesignWeight": 20,
  "currentTestingWeight": 10,
  "proposedCodeWeight": 30,
  "proposedDocumentWeight": 20,
  "proposedDesignWeight": 40,
  "proposedTestingWeight": 10,
  "status": "PENDING",
  "createdAt": "2026-08-04T18:08:09Z",
  "updatedAt": "2026-08-04T18:08:09Z"
}
```

### Lỗi
- **400**: Tổng trọng số không bằng 100%
- **400**: Course không tồn tại
- **403**: Không phải giảng viên
- **409**: Đã có yêu cầu pending cho course này

---

## 5. Xem Danh Sách Yêu Cầu Thay Đổi Trọng Số (Admin/Giảng viên)

### Endpoint
```
GET /api/v1/courses/{courseId}/contribution-weight-requests?status={status}
```

### Mục đích
- Admin xem danh sách yêu cầu chờ duyệt (PENDING)
- Giảng viên xem lịch sử yêu cầu của mình

### Request
```bash
# Admin xem tất cả yêu cầu chờ duyệt
curl -X GET \
  'http://localhost:8080/api/v1/courses/10/contribution-weight-requests?status=PENDING' \
  -H 'Authorization: Bearer {admin-token}' \
  -H 'Accept: application/json'
```

### Response (Status 200)
```json
{
  "courseId": 10,
  "courseCode": "CS101",
  "courseName": "Nhập môn lập trình",
  "requests": [
    {
      "requestId": 123,
      "requestedBy": {
        "userId": 201,
        "fullName": "TS. Nguyễn Văn Giảng Viên"
      },
      "reason": "Dự án này nặng về thiết kế giao diện, code ít hơn",
      "status": "PENDING",
      "proposedCodeWeight": 30,
      "proposedDocumentWeight": 20,
      "proposedDesignWeight": 40,
      "proposedTestingWeight": 10,
      "createdAt": "2026-08-04T18:08:09Z"
    }
  ]
}
```

---

## 6. Duyệt/Từ Chối Yêu Cầu Thay Đổi Trọng Số (Admin)

### Endpoint
```
PUT /api/v1/contribution-weight-requests/{requestId}/decision
```

### Mục đích
- Admin duyệt hoặc từ chối yêu cầu
- Nếu duyệt: trọng số course sẽ cập nhật ngay lập tức

### Request Body - Duyệt
```json
{
  "decision": "APPROVED",
  "feedbackMessage": "Nhận xét hợp lý, trọng số đã cập nhật"
}
```

### Request Body - Từ Chối
```json
{
  "decision": "REJECTED",
  "feedbackMessage": "Cần giải thích thêm chi tiết"
}
```

### Request
```bash
curl -X PUT \
  'http://localhost:8080/api/v1/contribution-weight-requests/123/decision' \
  -H 'Authorization: Bearer {admin-token}' \
  -H 'Content-Type: application/json' \
  -d '{
    "decision": "APPROVED",
    "feedbackMessage": "Nhận xét hợp lý, trọng số đã cập nhật"
  }'
```

### Response (Status 200)
```json
{
  "requestId": 123,
  "courseId": 10,
  "status": "APPROVED",
  "decision": "APPROVED",
  "feedbackMessage": "Nhận xét hợp lý, trọng số đã cập nhật",
  "decidedBy": {
    "userId": 999,
    "fullName": "Admin"
  },
  "decidedAt": "2026-08-04T18:15:00Z",
  "proposedCodeWeight": 30,
  "proposedDocumentWeight": 20,
  "proposedDesignWeight": 40,
  "proposedTestingWeight": 10
}
```

### Lỗi
- **400**: Yêu cầu không ở trạng thái PENDING
- **403**: Không phải admin
- **404**: Yêu cầu không tồn tại

---

## 7. Override % Đóng Góp Ngay Lập Tức (Giảng viên/Admin)

### Endpoint
```
POST /api/v1/teams/{teamId}/contribution-override
```

### Mục đích
- Giảng viên/Admin áp dụng override % đóng góp cho cả lớp **ngay lập tức**
- Không cần admin duyệt
- Dùng để xử lý các trường hợp đặc biệt (team member vắng, bệnh, v.v.)

### Request Body
```json
{
  "reason": "Thành viên SV001 bệnh 2 tuần, cần giảm %",
  "overrideType": "TEAM_CONTRIBUTION_OVERRIDE",
  "adjustments": [
    {
      "studentId": 101,
      "adjustmentPercentage": -15,
      "note": "Vắng 2 tuần"
    },
    {
      "studentId": 102,
      "adjustmentPercentage": 5,
      "note": "Hỗ trợ thêm"
    }
  ]
}
```

### Request
```bash
curl -X POST \
  'http://localhost:8080/api/v1/teams/1/contribution-override' \
  -H 'Authorization: Bearer {lecturer-token}' \
  -H 'Content-Type: application/json' \
  -d '{
    "reason": "Thành viên SV001 bệnh 2 tuần, cần giảm %",
    "overrideType": "TEAM_CONTRIBUTION_OVERRIDE",
    "adjustments": [
      {
        "studentId": 101,
        "adjustmentPercentage": -15,
        "note": "Vắng 2 tuần"
      },
      {
        "studentId": 102,
        "adjustmentPercentage": 5,
        "note": "Hỗ trợ thêm"
      }
    ]
  }'
```

### Response (Status 201)
```json
{
  "overrideId": 456,
  "teamId": 1,
  "reason": "Thành viên SV001 bệnh 2 tuần, cần giảm %",
  "overrideType": "TEAM_CONTRIBUTION_OVERRIDE",
  "appliedBy": {
    "userId": 201,
    "fullName": "TS. Nguyễn Văn Giảng Viên"
  },
  "adjustments": [
    {
      "studentId": 101,
      "adjustmentPercentage": -15,
      "note": "Vắng 2 tuần",
      "appliedAt": "2026-08-04T18:08:09Z"
    },
    {
      "studentId": 102,
      "adjustmentPercentage": 5,
      "note": "Hỗ trợ thêm",
      "appliedAt": "2026-08-04T18:08:09Z"
    }
  ],
  "status": "APPLIED",
  "createdAt": "2026-08-04T18:08:09Z"
}
```

### Lỗi
- **400**: Tổng adjustment không hợp lệ
- **403**: Không có quyền (chỉ LECTURER + ADMIN)
- **404**: Team không tồn tại

---

## Quy Trình Tích Hợp FE

### 1. Sinh viên Đánh Giá Peer Review
*(Sử dụng API từ PEER_REVIEW_API_EXAMPLE.md)*
- FE gọi API peer review
- Lưu 4 tiêu chí × số sao
- Submit

### 2. Giảng viên Xem Kết Quả (Trang Chi Tiết Peer Review)
```
GET /api/v1/peer-review/team/{teamId}/detail?sprintId={sprintId}
```
- Hiển thị 4 tiêu chí + số sao cho mỗi thành viên
- Hiển thị tổng sao và số lần được đánh giá

### 3. Giảng viên Xem % Đóng Góp (Trang Bảng Điểm)
```
GET /api/v1/teams/{teamId}/contribution-evaluation?sprintId={sprintId}
```
- Hiển thị bảng: Tên - Code % - Doc % - Design % - Tổng %
- Hiển thị chi tiết: task score, peer review score, warnings

### 4. Giảng viên Yêu Cầu Thay Đổi Trọng Số (Nếu Cần)
```
POST /api/v1/courses/{courseId}/contribution-weight-request
GET /api/v1/courses/{courseId}/contribution-weight-requests?status=PENDING
```
- Gửi yêu cầu + lý do
- Chờ admin duyệt

### 5. Admin Duyệt Yêu Cầu
```
PUT /api/v1/contribution-weight-requests/{requestId}/decision
```
- Duyệt/từ chối
- Trọng số cập nhật ngay lập tức nếu duyệt

### 6. Giảng viên Override % (Xử Lý Exception)
```
POST /api/v1/teams/{teamId}/contribution-override
```
- Áp dụng ngay lập tức, không cần duyệt
- Dùng cho trường hợp đặc biệt

---

## Công Thức Tính Toán % Đóng Góp

### Dữ Liệu Input
- **Task scores**: Điểm từ task assignments (evidence từ Jira/backend)
- **Peer review scores**: Trung bình score của 4 tiêu chí từ peer review (0-20)
- **Slice weights**: Trọng số Code/Doc/Design (tổng = 100%)

### Bước 1: Tính Score Cho Mỗi Slice
Mỗi thành viên nhận score từ:
- Commit code → **Code slice**
- Document tasks → **Document slice**
- Design tasks → **Design slice**

Score = Task evidence + Peer review contribution

### Bước 2: Chuẩn Hóa Score Thành Percentage
```
code_percentage = (member_code_score / total_code_score) × code_weight
doc_percentage = (member_doc_score / total_doc_score) × doc_weight
design_percentage = (member_design_score / total_design_score) × design_weight

final_percentage = code_percentage + doc_percentage + design_percentage
```

### Bước 3: Áp Dụng Override (Nếu Có)
```
final_percentage = final_percentage + adjustments_from_override
```

### Ví Dụ
**Team 4 người: A (3 code), B (1 code + 3 doc), C (1 design), D (inactive)**

Weights: Code=40%, Doc=30%, Design=20%

| Người | Code Score | Doc Score | Design Score | Code % | Doc % | Design % | Tổng % |
|-------|-----------|-----------|--------------|--------|-------|----------|--------|
| A     | 60        | 0         | 0            | 30.0   | 0.0   | 0.0      | 30.0   |
| B     | 20        | 60        | 0            | 10.0   | 18.0  | 0.0      | 28.0   |
| C     | 0         | 0         | 40           | 0.0    | 0.0   | 20.0     | 20.0   |
| D     | 0         | 0         | 0            | 0.0    | 0.0   | 0.0      | 0.0    |
| **Tổng** | 80 | 60 | 40 | **40.0** | **18.0** | **20.0** | **78.0** |

*(Tổng = 78% là do D không làm gì, nên phần % của D bị "mất")*

---

## Validation & Security

### Quyền Truy Cập
- **GET contribution-evaluation**: LECTURER, ADMIN (xem team của mình)
- **POST contribution-override**: LECTURER, ADMIN
- **POST contribution-weight-request**: LECTURER (của course)
- **PUT contribution-weight-request decision**: ADMIN only

### Validation Rules
1. Tổng trọng số phải = 100%
2. Sinh viên không thể đánh giá chính mình (được xử lý trong peer review API)
3. Override adjustment tổng cộng không được làm thay đổi quá 50% điểm của thành viên

---

## Lỗi Thường Gặp

| Lỗi | Nguyên nhân | Cách sửa |
|-----|-----------|---------|
| 403 Forbidden | Không có quyền xem/sửa | Kiểm tra token, role, team assignment |
| 404 Not Found | Team/Course không tồn tại | Kiểm tra ID trong URL |
| 400 Bad Request | Dữ liệu không hợp lệ | Kiểm tra request body, tổng % = 100%? |
| 409 Conflict | Yêu cầu đã tồn tại (PENDING) | Chờ admin duyệt hoặc từ chối yêu cầu cũ |

---

## Tham Khảo Thêm

- [Peer Review API Documentation](./FRONTEND_API_INTEGRATION.md#peer-review-endpoints)
- [Team & Course API](./FRONTEND_API_INTEGRATION.md)
- [Contribution Calculation Logic](../src/main/java/com/saga/be/service/contribution/ContributionCalculationService.java)
