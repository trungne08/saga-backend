# Logic tính % đóng góp (Contribution) — SAGA Backend

Tài liệu này mô tả **đúng theo code hiện tại** cách hệ thống tính `finalContributionPercentage` cho từng sinh viên trong team.

---

## 1) Điểm vào (entry points)

### API chính để xem kết quả đóng góp team
- `GET /api/v1/teams/{teamId}/contribution-evaluation`
- Controller: `TeamContributionController#getContributionEvaluation`
- Service thực thi chính: `TeamContributionService#evaluate`
- Quyền đọc: ADMIN mọi Team; LECTURER chỉ Course mình phụ trách; STUDENT chỉ exact
  `RoleInTeam.LEADER` của chính Team. MEMBER/MENTOR/cross-Team Leader bị từ chối.

### API override % đóng góp theo từng sinh viên
- `POST /api/v1/teams/{teamId}/contribution-override`
- Controller: `TeamContributionController#requestContributionOverride`
- Service: `TeamContributionService#requestContributionOverride`

### API quản lý trọng số slice theo course
- `GET /api/v1/courses/{courseId}/contribution-slice-weights`
- `POST /api/v1/courses/{courseId}/contribution-slice-weight-requests`
- `GET /api/v1/courses/contribution-slice-weight-requests`
- `PUT /api/v1/courses/contribution-slice-weight-requests/{requestId}/decision`
- Service: `CourseContributionWeightService`

---

## 2) Nguồn dữ liệu dùng để tính

Trong `TeamContributionService#evaluate(teamId)`:

1. Lấy `Team`, `Project`, danh sách `TeamMember`.
2. Nếu team chưa gắn project hoặc không có member -> trả `members = []`.
3. Lấy dữ liệu thô:
   - `Task` theo project.
   - `Document` theo project.
   - `PeerReview` theo danh sách reviewee thuộc team + project.
   - `PolicyOverrideRequest` đã `APPROVED` cho loại `TEAM_CONTRIBUTION_OVERRIDE` trong cùng class.

---

## 3) Cách tính điểm thô theo từng sinh viên

Hệ thống duy trì 4 cụm điểm/metrics per-student:

- `codeScore`
- `documentScore`
- `designScore`
- `adjustedSprintScore` (task score đã nhân hệ số retrospective/peer theo sprint)

### 3.1 Điểm từ commit

Lấy commit có task (`findByAuthorIdAndProjectIdAndTaskIsNotNull`).

- Mỗi commit đóng góp một trọng số = `storyPoint` của task liên kết; nếu null => `1.0`.
- Task được phân loại slice qua `classifyTaskSlice(task)`:
  - Nếu title/description/labels/components chứa keyword design -> `DESIGN`
  - Nếu chứa keyword document -> `DOCUMENT`
  - Còn lại -> `CODE`

Trọng số commit được cộng vào slice tương ứng.

### 3.2 Điểm từ document

Đếm theo author trong project:

- `DocumentType != DESIGN` -> cộng vào `documentScore`
- `DocumentType == DESIGN` -> cộng vào `designScore`

### 3.3 Điểm từ task DONE

Lọc task theo assignee là sinh viên và `status == DONE`.

Với từng task DONE:
- `taskWeight = storyPoint` nếu có, ngược lại `1.0`.
- Cộng `taskWeight` vào slice tương ứng theo `classifyTaskSlice`.
- Gom `taskWeight` theo từng sprint để tính breakdown.

### 3.4 Hệ số retrospective theo sprint

Được tính khi build sprint breakdown:

- `retrospectiveMultiplier = studentSprintPeerScore / totalSprintPeerScore`
- Nếu tổng peer score của sprint `<= 0` thì multiplier = `1.0`

Sau đó:
- `adjustedTaskScore (per sprint) = taskScore * retrospectiveMultiplier`
- `adjustedSprintScore` của sinh viên = tổng `adjustedTaskScore` của mọi sprint có dữ liệu.

---

## 4) Peer review ảnh hưởng vào % đóng góp như thế nào

### 4.1 Nguồn `starRating` peer review

Trong `PeerReviewService#submit`:

- Nếu request gửi `criteriaRatings`:
  - Bắt buộc rate đủ rubric, không trùng.
  - `peerReview.starRating = tổng star của tất cả criteria`.
- Nếu không gửi `criteriaRatings`:
  - Bắt buộc có `starRating` tổng.

=> Luồng contribution chỉ dùng `peerReview.starRating` đã lưu.

### 4.2 Peer coefficient toàn project

Trong `evaluate`:

- `peerScoreByStudent = tổng starRating của các review mà sinh viên là reviewee`
- `totalPeerScore = tổng peerScoreByStudent toàn team`
- `peerCoefficient(student)`:
  - Nếu `totalPeerScore > 0`: `peerScoreByStudent / totalPeerScore`
  - Nếu `totalPeerScore == 0`: `1.0`

---

## 5) Slice weights (Code/Document/Design)

### 5.1 Lấy trọng số

`sliceWeights = ContributionSliceWeights.fromCourse(team.course)`

- Nếu course null hoặc weights null -> fallback mặc định 1/3 - 1/3 - 1/3.
- `normalizeConfigured(...)` luôn chuẩn hóa để tổng = 100.

### 5.2 Vô hiệu hóa slice không có dữ liệu

`normalizeForActiveSlices(totalCode > 0, totalDocument > 0, totalDesign > 0)`

Nghĩa là:
- Slice nào total = 0 sẽ bị set về 0 trước khi normalize lại.
- Tránh chia ngân sách vào slice không có evidence.

---

## 6) Công thức tính contribution

Ký hiệu:
- `C%`: % contribution trong slice code của sinh viên
- `D%`: % contribution trong slice document
- `G%`: % contribution trong slice design
- `Wc, Wd, Wg`: trọng số slice code/document/design (tổng 100)
- `P`: peerCoefficient

### 6.1 Contribution theo từng slice

Với mỗi slice:
- nếu total slice > 0:
  - `sliceContribution% = studentSliceScore / totalSliceScore * 100`
- ngược lại = `0`

### 6.2 Raw contribution

`rawContribution = (C% * Wc + D% * Wd + G% * Wg) / 100`

### 6.3 Adjusted contribution trước override

`baseAdjustedContribution = rawContribution * P`

---

## 7) Override và chuẩn hóa về tổng 100%

Hệ thống merge `baseAdjustedContribution` với override đã duyệt bằng `normalizeContributionsWithOverrides`.

### 7.1 Nếu tất cả member đều bị override

- Nếu tổng override <= 0:
  - chia đều 100.
- Ngược lại:
  - chuẩn hóa theo tỷ lệ override để tổng = 100.

### 7.2 Nếu chỉ override một phần member

1. Tính tổng override (`totalOverride`).
2. Nếu `totalOverride > 100`:
   - scale toàn bộ override theo tỷ lệ để tổng override = 100.
   - nhóm không override nhận 0.
3. Nếu `totalOverride <= 100`:
   - giữ nguyên giá trị override.
   - phần ngân sách còn lại `remainingBudget = 100 - totalOverride`.
   - phân bổ cho nhóm không override theo tỷ lệ `baseAdjustedContribution`.
   - nếu tổng base của nhóm này = 0 thì chia đều.

=> `finalContributionPercentage` luôn được normalize về tổng 100% toàn team.

---

## 8) Warning rules

Sau khi có `finalContributionPercentage`, hệ thống tạo warning:

1. `NO_PEER_REVIEW`  
   - peerReviewCount = 0 và final >= 50
2. `LOW_PEER_REVIEW`  
   - peerCoefficient <= 0.6 và final >= 40
3. `INSUFFICIENT_EVIDENCE`  
   - evidenceCount <= 1 và final >= 60
4. `NO_EVIDENCE`  
   - commit = 0, document = 0, design = 0, peerReview = 0

`evidenceCount` tăng khi có dữ liệu ở các nhóm:
- commit
- task done
- document/design
- peer review

---

## 9) Quy tắc validation cho thay đổi slice weight theo course

Trong `CourseContributionWeightService#validateRequestedWeights`:

- Bắt buộc có đủ `codeWeight`, `documentWeight`, `designWeight`
- Mỗi trọng số phải `>= 0`
- Tổng phải xấp xỉ `100` với tolerance `0.01`

Nếu admin duyệt request:
- giá trị được normalize lần nữa qua `ContributionSliceWeights.normalizeConfigured`
- sau đó ghi vào `course.code_contribution_weight`, `document_contribution_weight`, `design_contribution_weight`.

---

## 10) Các lưu ý kỹ thuật quan trọng

1. `taskContributionPercentage` trong response là tỉ lệ từ `adjustedSprintScore` (task theo sprint đã nhân retrospective multiplier), không phải trực tiếp từ raw slice.
2. `peerReviewScore` field hiện trả `peerCoefficient` (0..1), không phải tổng sao thô.
3. Nếu team không có project hoặc rỗng member thì API trả danh sách members rỗng.
4. Override team-level hiện được lưu dạng `APPROVED` ngay khi tạo request trong `requestContributionOverride`.
5. Tất cả phép tính cuối cùng vẫn đưa về budget 100%.

---

## 11) Mapping file mã nguồn (để audit nhanh)

- Main evaluate flow:  
  `src/main/java/com/saga/be/service/TeamContributionService.java`
- Slice weight normalization:  
  `src/main/java/com/saga/be/service/contribution/ContributionSliceWeights.java`
- Course slice-weight request/approval:  
  `src/main/java/com/saga/be/service/CourseContributionWeightService.java`
- Peer review submit (cách tạo starRating):  
  `src/main/java/com/saga/be/service/PeerReviewService.java`
- Endpoints:  
  `src/main/java/com/saga/be/controller/TeamContributionController.java`  
  `src/main/java/com/saga/be/controller/CourseContributionWeightController.java`

