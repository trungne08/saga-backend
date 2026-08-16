# Contribution Flow API

## Authentication và CSRF

- Application API dùng Spring Security browser session qua cookie `JSESSIONID`.
- Frontend phải gửi `credentials: "include"`; không gửi Bearer token.
- GET không cần CSRF header.
- POST/PUT trong tài liệu này phải gửi `X-XSRF-TOKEN`, lấy từ
  `GET /api/auth/csrf` hoặc cookie `XSRF-TOKEN` theo contract chung.
- Role annotation trên controller và effective authorization trong service được
  ghi tách riêng vì hai lớp hiện không luôn đồng nhất.

```ts
const csrf = await fetch("/api/auth/csrf", {
  credentials: "include",
}).then((response) => response.json());

await fetch(path, {
  method: "POST",
  credentials: "include",
  headers: {
    "Content-Type": "application/json",
    [csrf.headerName]: csrf.token,
  },
  body: JSON.stringify(body),
});
```

Tài liệu này mô tả đúng các API hiện có trong code để FE tích hợp luồng:

- Peer review
- Xem đánh giá đóng góp
- Lecturer sửa trực tiếp trọng số slice Course (`PUT .../contribution-slice-weights`, `COURSE` mode)
- Lecturer sửa trọng số slice theo Team/Project (`PUT /api/projects/{projectId}/group-weights`, `TEAM` mode)
- Chuyển mode Course-wide Contribution config (`PUT .../contribution-config-mode`)
- Team-menu read (`GET .../contribution-team-weights`)
- Individual contribution override (`POST .../contribution-override`) — unchanged

---

## 1) Peer review

### 1.1 Lấy 4 rubric mặc định

`GET /api/v1/peer-review-rubrics/default`

**Controller role annotation:** không có annotation riêng.

**Effective service access:** mọi authenticated session. Service ưu tiên rubric
global (`subjectId = null`); chỉ khi không có global rubric và có Subject mới dùng
rubric của Subject.

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

**Migration seed dự kiến:** V13 khai báo 4 rubric global tiếng Việt. Trạng thái
seed trên fresh/production database là **TBD**, không được coi bốn ID dưới đây là
runtime guarantee hoặc hard-code ở FE.

**4 rubric được khai báo trong V13 (tiếng Việt)**
- Hoàn thành & Chất lượng (Làm đúng, đủ task được giao; code/chức năng chạy ổn định, ít lỗi.)
- Tiến độ & Quy trình (Đáp ứng đúng deadline; đẩy/merge code kịp thời, không làm kẹt tiến độ chung.)
- Giao tiếp & Hỗ trợ (Dễ liên lạc; chủ động phối hợp và sẵn sàng giúp đỡ đồng đội.)
- Thái độ & Xử lý sự cố (Chịu trách nhiệm với công việc được giao; xử lý sự cố kịp thời và hiệu quả, cởi mở tiếp thu góp ý.)

### 1.2 Lấy rubric theo team

`GET /api/v1/teams/{teamId}/peer-review-rubric`

**Controller role annotation:** không có annotation riêng.

**Effective service access:** ADMIN đọc mọi Team; LECTURER phải là instructor của
Course; STUDENT phải là thành viên Team.

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

**Controller role annotation:** ADMIN, STUDENT.
**Effective service access:** service hiện chỉ chấp nhận STUDENT thuộc đúng Team;
ADMIN qua annotation vẫn bị từ chối `403`. Đây là mismatch backend hiện hữu.
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

**Controller role annotation:** ADMIN, STUDENT.

**Effective service access:** service hiện chỉ chấp nhận STUDENT thuộc đúng Team;
ADMIN qua annotation vẫn bị từ chối `403`.

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
- Reviewer lấy từ session principal. Submit là upsert theo bộ
  `(sprint, reviewer, reviewee)`.
- Self-review và reviewer/reviewee khác Team bị từ chối `400`.

### 1.5 Xem peer reviews của sprint

`GET /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews`

**Controller role annotation:** ADMIN, LECTURER, STUDENT.

**Effective service access:** ADMIN đọc mọi Team; LECTURER phải là instructor của
Course; STUDENT phải thuộc Team. Student hiện đọc được toàn bộ danh sách review của
Team/Sprint, không chỉ review của chính mình.

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

**Controller role annotation:** LECTURER, STUDENT.

**Effective service authorization:** LECTURER chỉ Team thuộc Course
mình phụ trách; STUDENT chỉ khi `SagaPrincipal.localProfileId` có exact `TeamMember` role
`LEADER` của chính `teamId` đang yêu cầu. ADMIN, MEMBER, MENTOR, Student không membership và
Leader Team khác nhận `403`; anonymous `401`; Team không tồn tại `404`.

`LEADER` là `RoleInTeam`, không phải application role. FE không gửi actor ID và không tự
quyết định quyền bằng UI.

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
      "codeContributionPercentage": 32.1,
      "documentContributionPercentage": 10.2,
      "testContributionScore": 0.0,
      "testContributionPercentage": 0.0,
      "researchContributionScore": 0.0,
      "researchContributionPercentage": 0.0,
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
          "peerReviewCount": 3,
          "contributionPercentage": 56.14
        }
      ],
      "warnings": []
    }
  ]
}
```

**Ghi chú**
- Đây là **current aggregate** tính từ dữ liệu hiện tại, không phải historical
  committed snapshot theo thời điểm bắt đầu Sprint.
- `finalContributionPercentage` = `(Σ slice × P cả dự án) / Σ adjust team × 100` (DEC-092), rồi override/normalize về tổng 100. Slice = Σ SP cùng tiêu chí × trọng số. Client không nhân lại `peerReviewScore`.
- `sprintBreakdowns[].contributionPercentage` là % đã chốt của sprint đó.
- `code/document` là breakdown theo slice thực tế có evidence (evidence từng gán DESIGN nay gộp vào `document`).
- **(Cập nhật DEC-089)** `testContributionScore`/`testContributionPercentage` và
  `researchContributionScore`/`researchContributionPercentage` **không còn hardcode `0`** — nếu một
  Task DONE trong Team/Project mang nhãn Jira chính xác `saga:test` hoặc `saga:research` (exact
  match, không substring/fuzzy), giá trị này phản ánh evidence thật. Nếu không có Task nào mang
  marker đó, giữ nguyên `0` như trước. Đây vẫn chỉ là **foundation Task-marker path** — evidence từ
  Jira/GitHub attachment hoặc commit-via-traceability chưa được implement (block bởi runtime TBD),
  nên FE không nên coi 4-tiêu-chí scoring là hoàn tất toàn diện, chỉ path Task-marker là thật.
  Xem `CONTRIBUTION_PERCENTAGE_LOGIC.md` section 3.0.
- `taskContributionPercentage` là % contribution phần task sau normalize.
- Response DTO thực tế chỉ gồm các field trong `TeamContributionMemberResponse`.
- Privacy audit: response không có email, Cognito subject, provider credential, raw Peer Review
  comment/reviewer identity, token, internal secret hoặc raw Jira/GitHub payload.

---

## 2b) Contribution flowchart (DEC-096)

`GET /api/v1/teams/{teamId}/contribution-graph`

Cùng quyền với evaluation. Query `sprintId` tùy chọn: không có = flowchart cả Project; có = đúng Sprint thuộc Project của Team (404 nếu không). Payload node/edge dùng công thức SAGA (không hệ số mockup). `tasks[]` trên cạnh để drill-down. Không GHOSTING, không publish.

---

## 3) Slice weight theo course / team (DEC-088, supersedes DEC-087's Course-only model)

Criteria universe = **Code/Test/Document/Research** (DESIGN retired khỏi Contribution, vẫn là
ProjectType catalog value độc lập). Course weights dùng thang **0..100**, tổng 100 ± 0.01.
Team/Project override dùng thang **0..1**, tổng đúng `1.0`.

**Authority = mode-aware trên `Course.contributionConfigMode`, không còn "Course-only".** Mỗi
Course có đúng một mode active:

- **`COURSE`** (default): mọi Team thuộc Course dùng chung đúng một bộ bốn trọng số Course.
- **`TEAM`**: **mọi** Team hiện tại của Course bắt buộc có `ProjectGroupWeightConfig` riêng hợp lệ.
  Thiếu một Team = "chưa hoàn tất" — Contribution của Team đó **không tính được**
  (`TEAM_WEIGHT_CONFIG_INCOMPLETE`), tuyệt đối **không** fallback về Course weights.

**Không có mode hỗn hợp** ("Team override nếu có, không thì Course") — bị cấm tường minh. FE không
được tự suy diễn/giả lập hành vi này ở client.

`PUT /api/projects/{projectId}/group-weights` **đã được hồi sinh** (từng bị xóa ở milestone
Course-only trước đó) — xem section 3.4.

**`testWeight`/`researchWeight` hiện chưa ảnh hưởng kết quả tính điểm**
(`TEST_SLICE_CLASSIFICATION`/`RESEARCH_SLICE_CLASSIFICATION = TBD_PRODUCT_RULE` — xem
`CONTRIBUTION_PERCENTAGE_LOGIC.md`). Giá trị vẫn được lưu/đọc lại đúng qua API, nhưng `evaluate`
luôn coi hai slice này là không có evidence nên tự phân bổ lại ngân sách đó cho Code/Document.

**Existing Course và Team weight rows giữ nguyên giá trị Code/Document cũ** sau các migration V35/V36
— không bị reset. Các cột mới (`testWeight`/`researchWeight`/`contributionConfigMode`) mặc định
`0`/`0`/`COURSE` cho row đã tồn tại trước đó.

### 3.1 Xem trọng số hiện tại (Course)

`GET /api/v1/courses/{courseId}/contribution-slice-weights`

**Controller role annotation:** ADMIN, LECTURER.

**Effective service authorization:** ADMIN đọc mọi Course; LECTURER chỉ exact instructor của Course.
Actor từ `SagaPrincipal.localProfileId`. Session `JSESSIONID`, `credentials: include`, GET không CSRF, không Bearer.

**Response**
```json
{
  "courseId": "uuid",
  "courseCode": "SAGA101",
  "courseName": "Software Engineering",
  "codeWeight": 25.0,
  "testWeight": 25.0,
  "documentWeight": 25.0,
  "researchWeight": 25.0
}
```

### 3.2 Official new FE mutation — Lecturer direct update

`PUT /api/v1/courses/{courseId}/contribution-slice-weights`

**Controller role annotation:** LECTURER only.

**Effective service authorization:** exact Course instructor. Other Course / STUDENT / ADMIN direct PUT → 403.
Không gửi `lecturerId` / `adminId`. Actor từ principal. CSRF required. Không Bearer.

**Request body**
```json
{
  "codeWeight": 30,
  "testWeight": 10,
  "documentWeight": 20,
  "researchWeight": 40
}
```

Bốn field đều bắt buộc, mỗi field `>= 0`, tổng phải xấp xỉ `100` (tolerance `0.01`). FE không được
gửi thiếu `testWeight`/`researchWeight` hoặc tự bịa giá trị nếu Lecturer không nhập.

**Response:** cùng shape `CourseContributionSliceWeightResponse` như GET (kèm `testWeight`/`researchWeight`).
Một lần PUT chỉ có hiệu lực khi Course đang ở `COURSE` mode — khi đó áp dụng ngay cho **tất cả
Team** thuộc Course đó. Khi Course ở `TEAM` mode, PUT này vẫn ghi được (giữ vai trò "Course default
dự phòng" cho lần quay lại COURSE mode sau này) nhưng **không** ảnh hưởng Contribution của bất kỳ
Team nào cho tới khi mode chuyển về `COURSE`.

### 3.4 Team/Project override (TEAM mode)

`PUT /api/projects/{projectId}/group-weights`

**Controller:** không có `@PreAuthorize` role annotation — mọi authenticated request tới đây, service
tự kiểm tra.

**Effective service authorization:** ADMIN, hoặc đúng LECTURER phụ trách Course sở hữu Team của
Project đó. **Không** mở cho Student/Leader dù các route Team khác cho phép Leader quản lý Team —
route này cố ý dùng authorization hẹp hơn theo quyết định sản phẩm.

**Request body** (0..1 scale, không phải 0..100):
```json
{
  "groupId": "uuid",
  "codeWeight": 0.5,
  "testWeight": 0.2,
  "documentWeight": 0.2,
  "researchWeight": 0.1,
  "note": "optional"
}
```

`groupId` phải đúng Team sở hữu `projectId` trong path, ngược lại `400 GROUP_PROJECT_MISMATCH`. Mỗi
field `>= 0` và `<= 1`; tổng phải đúng bằng `1.0` (không có tolerance). Ghi được bất kỳ lúc nào (kể
cả khi Course đang ở `COURSE` mode, coi như "draft") nhưng chỉ được `ContributionSliceWeightResolver`
đọc sau khi Course chuyển sang `TEAM` mode.

**Response:** `ProjectGroupWeightConfigResponse` — `{projectId, groupId, codeWeight, testWeight, documentWeight, researchWeight, note, updatedAt, updatedByProfileId}`.

### 3.5 Chuyển mode COURSE ↔ TEAM

`PUT /api/v1/courses/{courseId}/contribution-config-mode`

**Effective service authorization:** LECTURER exact Course instructor only. CSRF required.

**Request body**
```json
{ "mode": "TEAM" }
```

Chuyển sang `TEAM`: backend audit **toàn bộ** Team hiện tại (chưa xoá) của Course — nếu **bất kỳ**
Team nào thiếu `ProjectGroupWeightConfig` hợp lệ, request bị từ chối nguyên khối với `409
TEAM_MODE_CONFIGURATION_INCOMPLETE` và mode giữ nguyên `COURSE` (không có trạng thái kích hoạt một
phần). FE nên hướng dẫn Lecturer cấu hình đủ mọi Team (section 3.4) trước khi gọi endpoint này.

Chuyển về `COURSE`: luôn thành công (Course weights luôn có giá trị hợp lệ nhờ default tầng
application). `ProjectGroupWeightConfig` của các Team **không bị xoá** — trở thành historical/inactive,
tái sử dụng được nếu Course quay lại `TEAM` mode sau này. Team tạo mới sau khi `TEAM` mode đã active
không tự động thừa hưởng Course weights — vẫn cần override riêng trước khi Contribution tính được.

**Response:** `ContributionConfigModeResponse` — `{courseId, mode}`.

### 3.6 Team-menu read

`GET /api/v1/courses/{courseId}/contribution-team-weights`

**Controller role annotation:** ADMIN, LECTURER.

Trả về mode hiện tại của Course cùng effective weights + nguồn (`COURSE` hoặc override) cho từng
Team — dùng để dựng màn hình "trọng số hiện tại theo Team" mà không cần FE tự suy diễn resolver
logic ở client.

---

Giảng viên sửa trọng số trực tiếp qua `PUT .../contribution-slice-weights` (COURSE mode) hoặc
`PUT /api/projects/{projectId}/group-weights` (TEAM mode). Không còn luồng gửi đơn / Admin duyệt /
lấy danh sách đơn. Các route sau **đã gỡ**, FE không được gọi:

- `POST /api/v1/courses/{courseId}/contribution-slice-weight-requests`
- `GET /api/v1/courses/contribution-slice-weight-requests`
- `PUT /api/v1/courses/contribution-slice-weight-requests/{requestId}/decision`

---

## 4) Override đóng góp

### Gửi override cho team

`POST /api/v1/teams/{teamId}/contribution-override`

**Controller role annotation:** ADMIN, LECTURER.

**Effective service authorization:** ADMIN được phép; LECTURER phải là instructor
của Course chứa Team. Với LECTURER, actor lấy từ principal; với ADMIN, optional
`lecturerId` lấy từ body.

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
4. Lecturer đúng Course hoặc Student LEADER đúng Team xem `GET .../contribution-evaluation`
4b. Cùng caller vẽ flowchart từ `GET .../contribution-graph` (công thức SAGA, không hệ số mockup)
5. Lecturer đúng Course gọi `PUT .../contribution-slice-weights` (CSRF) khi Course đang ở `COURSE` mode
6. Nếu muốn dùng `TEAM` mode: Lecturer gọi `PUT /api/projects/{projectId}/group-weights` (CSRF) cho **từng** Team hiện tại trước, rồi gọi `PUT .../contribution-config-mode` `{"mode":"TEAM"}` để activate — bị từ chối `409` nếu còn Team thiếu override
7. FE dùng `GET .../contribution-team-weights` để hiển thị mode hiện tại + effective weight từng Team, không tự suy diễn ở client
8. Lecturer/admin có thể dùng `POST .../contribution-override` khi cần chỉnh tay có lý do (individual override, không đổi)
9. FE lấy `GET /api/v1/projects/{projectId}/sprints` hoặc `GET /api/v1/teams/{teamId}/sprints` để chọn `sprintId` trước khi vào luồng contribution/peer review

### Quan trọng về `sprintId`

Các API contribution/peer review vẫn **nhận `sprintId` làm input**:
- `GET/POST /api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews...`

FE nên gọi API list sprint trước để lấy danh sách `sprintId`, sau đó mới vào các endpoint bên dưới.

### 5.1 Lấy danh sách sprint theo project/team

`GET /api/v1/projects/{projectId}/sprints`

`GET /api/v1/teams/{teamId}/sprints`

**Role:** ADMIN, LECTURER, STUDENT  
**Response:** `SprintListResponse`

**Effective service access:**

- Route theo Project: ADMIN xem mọi Project; LECTURER phải là instructor của
  Course; STUDENT phải có Team membership trong cùng Course với Project.
- Route theo Team: ADMIN xem mọi Team; LECTURER phải là instructor của Course;
  STUDENT phải thuộc đúng Team.
- Không có pagination. Sprint sort theo `startDate` tăng dần.
- Team chưa có Project trả `200`, `projectId: null`, `state: PROJECT_NOT_CREATED`, `sprints: []`; không có Sprint trả `state: EMPTY`, `sprints: []` với `200`; có Sprint trả `state: READY`.
- `teamId` trong route theo Project có thể `null`.

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

## 6) Known backend risks / integration caution

Các điểm dưới đây là behavior/risk hiện tại, chưa được mô tả như đã khắc phục:

- **PARTIAL — Peer Review authorization:** candidates và submit có annotation
  ADMIN/STUDENT nhưng service chỉ chấp nhận STUDENT thuộc Team.
- **PARTIAL — Student visibility:** Student thuộc Team hiện đọc được toàn bộ review
  của Team/Sprint, gồm identity và comment; anonymity/privacy policy chưa được
  repository chứng minh.
- **RESOLVED — Contribution evaluation ownership:** evaluation đã bind principal, scope Lecturer
  theo Course instructor và Student theo exact Team LEADER. Current slice-weight GET vẫn là risk
  riêng, không thay đổi trong milestone này.
- **PARTIAL — Actor binding:** slice-weight request lấy `lecturerId`, decision lấy
  `adminId`, và một nhánh contribution override lấy `lecturerId` từ request body.
  FE không được khai thác hoặc coi đây là authorization contract ổn định.
- **TBD — Default rubric database:** V10 tạo
  `rubric_template.subject_id NOT NULL`, trong khi V13 insert global rubric với
  `subject_id NULL`. Test profile dùng Hibernate `create-drop` và tắt Flyway, vì vậy
  test pass không chứng minh V13 seed chạy thành công trên fresh/production DB.
- **TBD — Production Flyway:** repository không chứa Flyway history hoặc runtime log
  production. FE phải xử lý `criteria: []` và không hard-code rubric IDs.

## 7) Lưu ý

- File này bám theo code hiện tại, không mô tả endpoint giả định.
- Các response `Page<>` ở API roster/course không nằm trong tài liệu này.
- Nếu FE cần sample request/response chi tiết hơn cho từng DTO, mình sẽ tách tiếp ra theo từng màn hình.

## Cập nhật 2026-08-09 — trạng thái rubric database

- Trước V22, runtime/baselined production được báo cáo có V10/V13 `SUCCESS`,
  `rubric_template` có 0 row và `subject_id` `NOT NULL`. Các ví dụ bốn rubric/UUID
  phía trên vì thế chỉ là SQL/sample lịch sử, không phải runtime guarantee.
- V22 chỉ làm nullable `subject_id` cho **EXISTING_BASELINED_DB_UPGRADE**; không seed
  default/global rubric. FE tiếp tục phải xử lý `criteria: []` và không hard-code ID.
- **REPLAY_FROM_EXTERNAL_V1_BASELINE** cần baseline legacy và decision riêng trước
  V13; **TRUE_EMPTY_DATABASE_BOOTSTRAP** là `BLOCKED_EXISTING_BASELINE_GAP`.
- Không có thay đổi authorization, visibility, submit hoặc contribution trong update này.
- **Runtime verification 2026-08-09:** V22 `SUCCESS`; `subject_id` hiện nullable
  `char(36)` và rubric row count vẫn 0. Không seed global/default rubric; FE tiếp tục
  xử lý `criteria: []` và không hard-code ID.

**Scope rollback 2026-08-10:** code/API/behavior M4B về rubric Peer Review đã được
gỡ. V23 vẫn giữ nguyên vì đã chạy production, nhưng application không dùng
`rubric_template.deleted_at`; không seed, không thêm resolver active-only và không có
Admin rubric CRUD.
