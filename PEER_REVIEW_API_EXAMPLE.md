# Peer Review API — contract tích hợp Frontend

Tài liệu này mô tả contract hiện hành theo controller, service và DTO tại HEAD
`3eab81d`. Application API dùng Spring Security browser session, không dùng Bearer
token.

## 1. Authentication và CSRF

- Mọi request API bên dưới gửi cookie phiên bằng `credentials: "include"`.
- Các GET không cần CSRF header.
- POST submit phải gửi `X-XSRF-TOKEN`. FE lấy token qua
  `GET /api/auth/csrf` và giữ token trong memory.
- Anonymous nhận `401`; authenticated nhưng không đủ quyền nhận `403`.

```ts
type CsrfTokenResponse = {
  token: string;
  headerName: string;
  parameterName: string;
};

async function sagaGet<T>(path: string): Promise<T> {
  const response = await fetch(path, { credentials: "include" });
  if (!response.ok) throw new Error(`SAGA API ${response.status}`);
  return response.json() as Promise<T>;
}

async function submitPeerReview(
  teamId: string,
  sprintId: string,
  body: PeerReviewRequest,
): Promise<PeerReviewResponse> {
  const csrf = await sagaGet<CsrfTokenResponse>("/api/auth/csrf");
  const response = await fetch(
    `/api/v1/teams/${teamId}/sprints/${sprintId}/peer-reviews`,
    {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      body: JSON.stringify(body),
    },
  );
  if (!response.ok) throw new Error(`SAGA API ${response.status}`);
  return response.json() as Promise<PeerReviewResponse>;
}
```

## 2. Rubric mặc định

```http
GET /api/v1/peer-review-rubrics/default
```

- Controller không gắn role annotation riêng; SecurityConfig yêu cầu authenticated
  session.
- Service ưu tiên danh sách rubric global (`subjectId = null`). Chỉ khi không có
  rubric global và có Subject mới dùng rubric của Subject.
- Response `200 PeerReviewDefaultRubricResponse`:

```json
{
  "criteria": [
    {
      "rubricId": "11111111-1111-1111-1111-111111111111",
      "criteriaName": "Hoàn thành & Chất lượng",
      "weight": 25,
      "description": "Mô tả tiêu chí"
    }
  ]
}
```

`criteria` có thể là danh sách rỗng nếu database không có rubric phù hợp. Việc V13
seed thành công trên fresh/production database hiện là `TBD`; FE không nên hard-code
ID rubric.

## 3. Rubric áp dụng cho Team

```http
GET /api/v1/teams/{teamId}/peer-review-rubric
```

- Controller không gắn role annotation riêng.
- Effective service access: ADMIN được đọc mọi Team; LECTURER phải là instructor
  của Course; STUDENT phải là thành viên Team.
- Team không tồn tại trả `404`; không đủ quyền trả `403`.
- Response `200 PeerReviewRubricResponse`:

```json
{
  "teamId": "uuid",
  "subjectId": "uuid-or-null",
  "criteria": [
    {
      "rubricId": "uuid",
      "criteriaName": "Tên tiêu chí",
      "weight": 25,
      "description": "Mô tả tiêu chí"
    }
  ]
}
```

## 4. Danh sách ứng viên được đánh giá

```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates
```

- Role annotation: `ADMIN`, `STUDENT`.
- Effective service access hiện tại: chỉ `STUDENT` thuộc đúng Team. ADMIN qua
  annotation nhưng vẫn bị service từ chối `403`. Đây là mismatch backend hiện hữu,
  không phải quyền FE được phép dựa vào.
- Reviewer được lấy từ session principal và bị loại khỏi candidates.
- Response `200 PeerReviewCandidatesResponse` dùng đúng các field sau:

```json
{
  "teamId": "uuid",
  "sprintId": "uuid",
  "reviewerId": "uuid",
  "candidates": [
    {
      "studentId": "uuid",
      "fullName": "Nguyễn Văn A",
      "studentCode": "SE001",
      "alreadyReviewed": false,
      "existingReviewId": null,
      "existingTotalStarRating": null
    }
  ]
}
```

## 5. Gửi hoặc cập nhật Peer Review

```http
POST /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
Content-Type: application/json
X-XSRF-TOKEN: {csrf-token}
```

- Role annotation: `ADMIN`, `STUDENT`.
- Effective service access hiện tại: chỉ `STUDENT` thuộc đúng Team; ADMIN bị
  service từ chối `403`.
- Reviewer luôn lấy từ session principal, không lấy từ body.
- Submit là upsert theo bộ `(sprint, reviewer, reviewee)`: gửi lại cùng bộ khóa sẽ
  cập nhật review hiện có.
- Self-review và reviewer/reviewee khác Team bị từ chối `400`.
- Sprint phải thuộc Project của Team; Team chưa có Project trả `400`, Sprint không
  thuộc Project trả `404`.

Request có thể gửi tổng `starRating` từ 0 đến 5 khi không gửi chi tiết, hoặc gửi
`criteriaRatings`. Khi gửi chi tiết phải chấm mỗi rubric đúng một lần:

```json
{
  "revieweeId": "uuid",
  "criteriaRatings": [
    { "rubricId": "uuid-1", "starRating": 5 },
    { "rubricId": "uuid-2", "starRating": 4 },
    { "rubricId": "uuid-3", "starRating": 5 },
    { "rubricId": "uuid-4", "starRating": 4 }
  ],
  "comment": "Phối hợp tốt trong Sprint"
}
```

Response `200 PeerReviewResponse`:

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
    { "rubricId": "uuid-1", "criteriaName": "Tên tiêu chí", "starRating": 5 }
  ],
  "comment": "Phối hợp tốt trong Sprint",
  "createdAt": "2026-08-04T12:00:00",
  "updatedAt": "2026-08-04T12:00:00"
}
```

## 6. Đọc toàn bộ Peer Review của Team/Sprint

```http
GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews
```

- Role annotation: `ADMIN`, `LECTURER`, `STUDENT`.
- Effective service access: ADMIN được đọc mọi Team; LECTURER phải là instructor
  của Course; STUDENT phải thuộc Team.
- Student hiện đọc được danh sách review của toàn Team/Sprint, gồm reviewer,
  reviewee, ratings và comment; backend không giới hạn về “review của chính mình”.
- Không có review trả `reviews: []` với `200`.

```json
{
  "teamId": "uuid",
  "sprintId": "uuid",
  "sprintName": "Sprint 1",
  "reviews": []
}
```

## 7. Lưu ý tích hợp

- Không hard-code số lượng hoặc ID rubric; render theo `criteria` backend trả về.
- Không gửi `reviewerId`; identity luôn đến từ `JSESSIONID`/`SagaPrincipal`.
- Không dựa vào việc ADMIN xuất hiện trong annotation của candidates/submit; service
  hiện vẫn từ chối ADMIN.
- Visibility toàn Team/Sprint là behavior hiện tại và là known backend risk về
  privacy/anonymity, không phải cam kết rằng thiết kế này sẽ được giữ lâu dài.
- Tài liệu này không định nghĩa công thức Contribution. FE chỉ hiển thị kết quả từ
  Contribution API và không tự tính lại từ Peer Review.
