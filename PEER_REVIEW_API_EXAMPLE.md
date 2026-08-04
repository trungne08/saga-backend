# Peer Review API Examples

## 📌 Sinh viên Đánh Giá Peer Review

### 1. Lấy danh sách thành viên có thể đánh giá (exclude self)
```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates
Authorization: Bearer {student_token}
```

**Response:**
```json
{
  "teamId": "uuid-team-123",
  "sprintId": "uuid-sprint-456",
  "reviewerId": "uuid-student-alice",
  "candidates": [
    {
      "studentId": "uuid-student-bob",
      "fullName": "Bob Smith",
      "studentCode": "SV002",
      "hasReviewed": false,
      "existingReviewId": null,
      "existingStarRating": null
    },
    {
      "studentId": "uuid-student-charlie",
      "fullName": "Charlie Brown",
      "studentCode": "SV003",
      "hasReviewed": true,
      "existingReviewId": "uuid-review-789",
      "existingStarRating": 15
    }
  ]
}
```

### 2. Lấy rubric peer review (4 tiêu chí)
```http
GET /api/v1/teams/{teamId}/peer-reviews/rubric
Authorization: Bearer {student_token}
```

**Response:**
```json
{
  "teamId": "uuid-team-123",
  "criteria": [
    {
      "rubricId": "uuid-rubric-1",
      "criteriaName": "Code Quality",
      "maxScore": 5,
      "order": 0
    },
    {
      "rubricId": "uuid-rubric-2",
      "criteriaName": "Documentation",
      "maxScore": 5,
      "order": 1
    },
    {
      "rubricId": "uuid-rubric-3",
      "criteriaName": "Team Collaboration",
      "maxScore": 5,
      "order": 2
    },
    {
      "rubricId": "uuid-rubric-4",
      "criteriaName": "Initiative & Responsibility",
      "maxScore": 5,
      "order": 3
    }
  ]
}
```

### 3. Đánh giá 1 thành viên
```http
POST /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
Authorization: Bearer {student_token}
Content-Type: application/json

{
  "revieweeId": "uuid-student-bob",
  "criteriaRatings": [
    {
      "rubricId": "uuid-rubric-1",
      "starRating": 5  // Code Quality: 5 sao
    },
    {
      "rubricId": "uuid-rubric-2",
      "starRating": 4  // Documentation: 4 sao
    },
    {
      "rubricId": "uuid-rubric-3",
      "starRating": 5  // Collaboration: 5 sao
    },
    {
      "rubricId": "uuid-rubric-4",
      "starRating": 3  // Initiative: 3 sao
    }
  ],
  "comment": "Bob did great work on the backend!"
}
```

**Response:**
```json
{
  "id": "uuid-review-new",
  "sprintId": "uuid-sprint-456",
  "sprintName": "Sprint 1",
  "reviewerId": "uuid-student-alice",
  "reviewerName": "Alice Johnson",
  "revieweeId": "uuid-student-bob",
  "revieweeName": "Bob Smith",
  "starRating": 17,  // 5+4+5+3 = 17 sao (tổng)
  "criteriaRatings": [
    {
      "rubricId": "uuid-rubric-1",
      "criteriaName": "Code Quality",
      "starRating": 5
    },
    {
      "rubricId": "uuid-rubric-2",
      "criteriaName": "Documentation",
      "starRating": 4
    },
    {
      "rubricId": "uuid-rubric-3",
      "criteriaName": "Team Collaboration",
      "starRating": 5
    },
    {
      "rubricId": "uuid-rubric-4",
      "criteriaName": "Initiative & Responsibility",
      "starRating": 3
    }
  ],
  "comment": "Bob did great work on the backend!",
  "createdAt": "2026-08-04T10:30:00+07:00",
  "updatedAt": "2026-08-04T10:30:00+07:00"
}
```

---

## 👨‍🏫 Giảng viên Xem Kết Quả Peer Review

### 1. Xem tất cả peer reviews của 1 sprint (có chi tiết 4 tiêu chí)
```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
Authorization: Bearer {lecturer_token}
```

**Response:**
```json
{
  "teamId": "uuid-team-123",
  "sprintId": "uuid-sprint-456",
  "sprintName": "Sprint 1",
  "reviews": [
    {
      "id": "uuid-review-1",
      "sprintId": "uuid-sprint-456",
      "sprintName": "Sprint 1",
      "reviewerId": "uuid-student-alice",
      "reviewerName": "Alice Johnson",
      "revieweeId": "uuid-student-bob",
      "revieweeName": "Bob Smith",
      "starRating": 17,
      "criteriaRatings": [
        {
          "rubricId": "uuid-rubric-1",
          "criteriaName": "Code Quality",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-2",
          "criteriaName": "Documentation",
          "starRating": 4
        },
        {
          "rubricId": "uuid-rubric-3",
          "criteriaName": "Team Collaboration",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-4",
          "criteriaName": "Initiative & Responsibility",
          "starRating": 3
        }
      ],
      "comment": "Bob did great work on the backend!",
      "createdAt": "2026-08-04T10:30:00+07:00",
      "updatedAt": "2026-08-04T10:30:00+07:00"
    },
    {
      "id": "uuid-review-2",
      "sprintId": "uuid-sprint-456",
      "sprintName": "Sprint 1",
      "reviewerId": "uuid-student-bob",
      "reviewerName": "Bob Smith",
      "revieweeId": "uuid-student-alice",
      "revieweeName": "Alice Johnson",
      "starRating": 19,
      "criteriaRatings": [
        {
          "rubricId": "uuid-rubric-1",
          "criteriaName": "Code Quality",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-2",
          "criteriaName": "Documentation",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-3",
          "criteriaName": "Team Collaboration",
          "starRating": 5
        },
        {
          "rubricId": "uuid-rubric-4",
          "criteriaName": "Initiative & Responsibility",
          "starRating": 4
        }
      ],
      "comment": "Alice is a great leader!",
      "createdAt": "2026-08-04T10:40:00+07:00",
      "updatedAt": "2026-08-04T10:40:00+07:00"
    }
  ]
}
```

---

## 📊 Tính Toán Contribution Dựa Trên Peer Review

### Công thức:
```
peerScore(student) = sum(starRating) từ tất cả reviews nhận về
peerCoefficient = peerScore(student) / totalPeerScore(team)

finalContribution(student) = 
  (code% * weight_code + doc% * weight_doc + design% * weight_design) 
  * peerCoefficient
  * (1 + override%)
```

### Ví dụ cho 4 thành viên:
```
Team: Alice, Bob, Charlie, Dave
Slice weights: Code 40%, Doc 30%, Design 30%

Sprint 1 peer review scores:
- Alice: 5 + 4 + 5 + 5 + 5 + 4 = 28 (từ Bob, Charlie, Dave)
- Bob: 5 + 4 + 5 + 3 = 17 (từ Alice) + 5 + 5 + 5 + 4 = 19 (từ Charlie) = 36
- Charlie: 4 + 4 + 4 + 4 = 16 (từ Alice) + 4 + 4 + 4 + 4 = 16 (từ Bob) = 32
- Dave: 3 + 3 + 3 + 3 = 12 (từ Alice) + 3 + 3 + 3 + 3 = 12 (từ Bob) = 24
Total: 28 + 36 + 32 + 24 = 120

peerCoefficient:
- Alice: 28 / 120 = 0.233
- Bob: 36 / 120 = 0.300
- Charlie: 32 / 120 = 0.267
- Dave: 24 / 120 = 0.200

Code contribution (only code & Bob):
- Alice: 10 points → 50% (of code total)
- Bob: 20 points → 100% (of code total)
Total code: 30 points

Doc contribution (only Charlie):
- Charlie: 15 points → 100% (of doc total)
Total doc: 15 points

Design contribution (only Dave):
- Dave: 8 points → 100% (of design total)
Total design: 8 points

Raw contribution (before peer adjustment):
- Alice: 50% * 0.4 = 20%
- Bob: 100% * 0.4 = 40%
- Charlie: 100% * 0.3 = 30%
- Dave: 100% * 0.3 = 30%

Adjusted (apply peer coefficient):
- Alice: 20% * 0.233 = 4.66%
- Bob: 40% * 0.300 = 12.00%
- Charlie: 30% * 0.267 = 8.01%
- Dave: 30% * 0.200 = 6.00%
Total: 30.67% (đã normalize để = 100%)

Final (normalize to 100%):
- Alice: 4.66 / 30.67 * 100 = 15.18%
- Bob: 12.00 / 30.67 * 100 = 39.10%
- Charlie: 8.01 / 30.67 * 100 = 26.10%
- Dave: 6.00 / 30.67 * 100 = 19.55%
```

---

## 🔐 Access Control

| Role | Can do |
|------|--------|
| **Student** | Submit peer review, view candidates, view their own reviews |
| **Lecturer** | View all peer reviews of their course's teams (with criteria detail) |
| **Admin** | View all peer reviews (with criteria detail) |

---

## ✅ Chi tiết tiêu chí khi giảng viên xem

**Giảng viên sẽ thấy trong response:**
- ✅ Tiêu chí 1: "Code Quality" - 5 sao
- ✅ Tiêu chí 2: "Documentation" - 4 sao  
- ✅ Tiêu chí 3: "Team Collaboration" - 5 sao
- ✅ Tiêu chí 4: "Initiative & Responsibility" - 3 sao
- ✅ **Tổng sao: 17** (5+4+5+3)

FE có thể hiển thị từng tiêu chí riêng hoặc tóm tắt dạng: "17 ⭐ (Code: 5, Doc: 4, Collab: 5, Init: 3)"
