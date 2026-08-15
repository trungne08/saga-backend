# Logic tính % đóng góp (Contribution) — SAGA Backend

Tài liệu này mô tả **đúng theo code hiện tại** cách hệ thống tính `finalContributionPercentage` cho từng sinh viên trong team.

**Cập nhật (DEC-088 — Course-default + optional exclusive Team override, supersedes DEC-087's
Course-only model):** Contribution weight config nay có đúng hai mode loại trừ nhau trên
`Course.contributionConfigMode`: **`COURSE`** (mọi Team thuộc Course dùng chung một bộ trọng số
của Course) hoặc **`TEAM`** (mọi Team hiện tại của Course bắt buộc có `ProjectGroupWeightConfig`
riêng hợp lệ — thiếu một Team là "chưa hoàn tất", không bao giờ fallback im lặng về Course).
`PUT /api/projects/{projectId}/group-weights` **đã được hồi sinh** (từng bị DEC-087 xóa). Criteria
universe đổi từ CODE/TEST/DOCUMENT/DESIGN sang **CODE/TEST/DOCUMENT/RESEARCH** — DESIGN không còn
là Contribution criterion active (vẫn là ProjectType catalog value độc lập). Evidence từng gán
DESIGN (task keyword + `DocumentType.DESIGN`) nay gộp thẳng vào `DOCUMENT` — xem section 3.1/3.2.

**Cập nhật (DEC-089 — Task-is-sole-numeric-authority + reserved Contribution markers, foundation
only):** Task giờ có thể được classify TEST/RESEARCH qua exact reserved marker
(`saga:code`/`saga:test`/`saga:document`/`saga:research`) trên `Task.labels`, được kiểm tra **trước**
legacy keyword classifier (marker precedence — xem section 3.0). `TEST_SLICE_CLASSIFICATION` và
`RESEARCH_SLICE_CLASSIFICATION` **không còn `= TBD_PRODUCT_RULE` tuyệt đối** — chúng có evidence
source thật cho path Task-marker (xem section 3.0/5.2), nhưng **vẫn TBD cho provider-sourced
evidence** (Jira/GitHub attachment, commit-via-traceability — chưa implement, block bởi runtime
TBD chưa xác nhận). Đồng thời: khi evidence là commit liên kết Task, **Task là numeric authority
duy nhất** — commit không còn cộng điểm bổ sung (double-count fix, xem section 3.1 đã viết lại).

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

Official FE:

- `GET /api/v1/courses/{courseId}/contribution-slice-weights`
- `PUT /api/v1/courses/{courseId}/contribution-slice-weights` (Lecturer exact instructor; CSRF; no `lecturerId`)

Legacy / backward-compatible / deprecated for new FE:

- `POST /api/v1/courses/{courseId}/contribution-slice-weight-requests`
- `GET /api/v1/courses/contribution-slice-weight-requests`
- `PUT /api/v1/courses/contribution-slice-weight-requests/{requestId}/decision`

Service: `CourseContributionWeightService`. Direct PUT chỉ có hiệu lực khi Course đang ở `COURSE`
mode (xem section 5). Team-scope: `PUT /api/projects/{projectId}/group-weights`
(`ProjectGroupWeightConfigService`, ADMIN hoặc đúng LECTURER phụ trách Course — không mở cho
Student/Leader), `PUT /api/v1/courses/{courseId}/contribution-config-mode` (mode switch, atomic,
audit toàn bộ Team trước khi activate `TEAM`), `GET /api/v1/courses/{courseId}/contribution-team-weights`
(team-menu read).

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

Hệ thống duy trì 4 cụm điểm/metrics per-student: `codeScore`, `testScore`, `documentScore`,
`researchScore`, cộng `adjustedSprintScore` (task score đã nhân hệ số retrospective/peer theo
sprint — **công thức numeric không đổi**, xem 3.3/3.4).

### 3.0 Marker-first classification (`classifyTaskContribution`, DEC-089)

Mỗi Task DONE được route vào đúng MỘT trong bốn `ContributionCriterion` (CODE/TEST/DOCUMENT/RESEARCH)
theo thứ tự ưu tiên sau, KHÔNG BAO GIỜ cộng vào nhiều hơn một:

1. **Reserved marker exact-match** (`ReservedContributionMarkerClassifier`, đọc `Task.labels`):
   `saga:code` -> CODE, `saga:test` -> TEST, `saga:document` -> DOCUMENT, `saga:research` -> RESEARCH.
   Exact string match sau khi trim, case-sensitive, không substring/fuzzy/AI (`saga:test-extra`,
   `SAGA:TEST` đều KHÔNG match). Nếu Task có **>1 marker xung đột** (ví dụ `saga:test` +
   `saga:research`) -> `AMBIGUOUS`: Task đó bị loại khỏi cả 4 criteria hoàn toàn (không tính vào
   `codeScore`/`testScore`/`documentScore`/`researchScore` nào cả) cho tới khi label được sửa —
   không pick-first. Nhãn business khác không phải reserved marker không gây conflict.
2. **Không có reserved marker nào** -> fallback nguyên vẹn vào `classifyTaskSlice` (legacy keyword
   classifier, xem 3.1) — hàm này **không đổi**, vẫn chỉ trả về CODE hoặc DOCUMENT, vẫn pick-first
   trên keyword conflict, vẫn CODE-default cho Task không nhãn.

Lưu ý quan trọng: dù Task bị `AMBIGUOUS` (không vào criterion nào), `adjustedSprintScore`/
`taskContributionPercentage` (section 3.3/3.4, pipeline riêng biệt) vẫn cộng storyPoint của Task đó
như bình thường — công thức numeric task/sprint không bị ảnh hưởng bởi kết quả classification.

`TeamContributionService` và `ContributionCalculationService` mỗi bên có bản `classifyTaskSlice`
riêng, hơi khác nhau về cách ghép text (thứ tự title/description/labels/components) — technical
debt có sẵn trước DEC-089, không được unify (không có test/source nào chứng minh unify là an toàn).

### 3.1 Điểm từ commit — Task là numeric authority duy nhất (DEC-089)

**Không còn per-commit scoring loop.** Trước DEC-089, một commit liên kết Task (qua field
`commit.task`, chưa từng được production upsert path nào ghi — audit xác nhận field này chết trong
thực tế) sẽ CỘNG THÊM điểm vào slice của Task đó, tạo double-count tiềm ẩn nếu Task cũng DONE và
được tính riêng qua 3.3. Product decision: khi evidence có authoritative link tới một Task, **Task
là numeric scoring authority duy nhất** — commit chỉ là supporting/provenance evidence, không mint
điểm riêng, kể cả khi có 1 hay nhiều commit cùng liên kết một Task (`commitCountByStudent` vẫn đếm
đúng số commit cho mục đích evidence/warning, chỉ phần cộng điểm bị xóa).

### 3.2 Điểm từ document

Đếm theo author trong project — cả `DocumentType.DESIGN` lẫn mọi `DocumentType` khác đều cộng vào
**`documentScore`** (`documentAndDesignCount` trong code) — không còn cụm điểm DESIGN riêng.
Standalone, hoàn toàn không phụ thuộc Task — không bị ảnh hưởng bởi DEC-089.

### 3.3 Điểm từ task DONE

Lọc task theo assignee là sinh viên và `status == DONE`.

Với từng task DONE:
- `taskWeight = storyPoint` nếu có, ngược lại `1.0` — **công thức numeric không đổi bởi DEC-089**.
- Cộng `taskWeight` vào đúng MỘT trong bốn criterion theo `classifyTaskContribution` (section 3.0);
  nếu AMBIGUOUS thì không cộng vào criterion nào (nhưng vẫn cộng vào `taskScoreBySprint` bên dưới).
- Gom `taskWeight` theo từng sprint để tính breakdown (luôn thực hiện, không điều kiện theo
  classification).

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

## 5) Slice weights (Code/Test/Document/Research)

### 5.1 Lấy trọng số

`sliceWeights = ContributionSliceWeightResolver.resolve(team)`

Authority (CONFIRMED_SOURCE, **mode-aware, fail-closed**): resolver đọc `team.course.contributionConfigMode`.

- `COURSE` mode: luôn `ContributionSliceWeights.fromCourse(team.course)`. Mọi Team thuộc cùng Course
  dùng chung đúng một bộ trọng số.
- `TEAM` mode: tra `ProjectGroupWeightConfig` theo đúng `projectId` của Team, xác nhận đúng Team sở
  hữu row đó, rồi `normalizeConfigured(codeWeight, testWeight, documentWeight, researchWeight)`.
  Thiếu override hoặc override thuộc Team khác -> ném `IntegrationException(TEAM_WEIGHT_CONFIG_INCOMPLETE)`,
  **không bao giờ** fallback về Course weights. Team mới tạo sau khi TEAM mode đã active cũng phải
  có override riêng — Contribution không tính được cho Team đó cho tới khi có override.

- Nếu course null hoặc weights null (chỉ áp dụng khi resolve thất bại trước khi biết mode) ->
  fallback mặc định 25/25/25/25 (equal quarters).
- `normalizeConfigured(...)` luôn chuẩn hóa để tổng = 100 (Course scale) hoặc 1.0 (Team/Project scale).

Lecturer `PUT /api/v1/courses/{courseId}/contribution-slice-weights` ghi bốn cột Course, chỉ có
hiệu lực khi Course đang ở `COURSE` mode. `PUT /api/projects/{projectId}/group-weights` ghi
`ProjectGroupWeightConfig`, có hiệu lực khi Course ở `TEAM` mode (ghi được bất kỳ lúc nào như
"draft", chỉ được resolver đọc sau khi mode chuyển sang `TEAM`). Không đổi công thức evaluate bên dưới.

### 5.2 Vô hiệu hóa slice không có dữ liệu

`normalizeForActiveSlices(totalCode > 0, testActive, totalDocument > 0, researchActive)`

Nghĩa là:
- Slice nào total = 0 (hoặc `testActive`/`researchActive` = false) sẽ bị set về 0 trước khi
  normalize lại.
- Tránh chia ngân sách vào slice không có evidence.
- **(DEC-089) `testActive`/`researchActive` không còn hardcode `false`** — nay là `totalTest > 0.0`
  / `totalResearch > 0.0`, giống hệt cách `codeActive`/`documentActive` đã hoạt động từ trước. Nếu
  có ít nhất một Task DONE mang `saga:test`/`saga:research` trong Team/Project, slice đó active và
  nhận đúng tỷ trọng cấu hình; nếu không có Task nào mang marker đó, slice vẫn bị coi là không
  evidence và ngân sách phân bổ lại cho các slice active khác — cơ chế fallback không đổi, chỉ có
  input (`totalTest`/`totalResearch`) là giờ phản ánh evidence thật thay vì hardcode.
  `TEST_SLICE_CLASSIFICATION`/`RESEARCH_SLICE_CLASSIFICATION` không còn `= TBD_PRODUCT_RULE` tuyệt
  đối cho path Task-marker; vẫn TBD cho provider-sourced evidence (attachment, commit-via-traceability).

---

## 6) Công thức tính contribution

Ký hiệu:
- `C%`: % contribution trong slice code của sinh viên
- `T%`: % contribution trong slice test
- `D%`: % contribution trong slice document
- `R%`: % contribution trong slice research
- `Wc, Wt, Wd, Wr`: trọng số slice code/test/document/research (tổng 100 hoặc 1.0 tuỳ scope — xem section 5)
- `P`: peerCoefficient

### 6.1 Contribution theo từng slice

Với mỗi slice:
- nếu total slice > 0:
  - `sliceContribution% = studentSliceScore / totalSliceScore * 100`
- ngược lại = `0`

### 6.2 Raw contribution

`rawContribution = (C% * Wc + T% * Wt + D% * Wd + R% * Wr) / 100`

Lưu ý (DEC-089): `T%`/`R%` không còn hardcode 0 — nếu có ít nhất một Task DONE mang
`saga:test`/`saga:research` trong Team/Project, `T%`/`R%` phản ánh tỷ trọng thật của sinh viên đó
trong slice, và `Wt`/`Wr` nhận đúng phần trăm cấu hình (thay vì bị `normalizeForActiveSlices` set
về 0). Nếu không có Task nào mang marker đó, `totalTest`/`totalResearch = 0` nên `T%`/`R% = 0` và
`Wt`/`Wr = 0` như trước — cơ chế fallback identical, chỉ input thay đổi.

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
   - commit = 0, document = 0, peerReview = 0

`evidenceCount` tăng khi có dữ liệu ở các nhóm:
- commit
- task done
- document (bao gồm evidence từng gán DESIGN, nay gộp vào document)
- peer review

---

## 9) Quy tắc validation cho thay đổi slice weight theo course/team

Official Course direct update (`CourseContributionWeightService#updateCurrentWeights` / `validateDirectWeights`):

- Body `{codeWeight, testWeight, documentWeight, researchWeight}` — không `lecturerId`
- Bắt buộc đủ bốn field, mỗi field `>= 0`
- Tổng phải xấp xỉ `100` với tolerance `0.01`
- Actor = `SagaPrincipal.localProfileId`; LECTURER exact Course instructor
- Ghi trực tiếp Course `code_contribution_weight` / `test_contribution_weight` /
  `document_contribution_weight` / `research_contribution_weight`

Team/Project override (`ProjectGroupWeightConfigService#update`):

- Body `{groupId, codeWeight, testWeight, documentWeight, researchWeight, note?}` — 0..1 scale
- `groupId` phải đúng Team sở hữu `projectId`, ngược lại `GROUP_PROJECT_MISMATCH`
- Mỗi field `>= 0` và `<= 1`; tổng phải đúng bằng `1.0` (không tolerance)
- Actor: ADMIN hoặc đúng LECTURER phụ trách Course sở hữu Team — không mở cho Student/Leader

Mode switch (`CourseContributionWeightService#switchConfigMode`):

- Chuyển sang `TEAM` chỉ khi audit xác nhận **mọi** Team hiện tại của Course đã có
  `ProjectGroupWeightConfig` hợp lệ — thiếu một Team -> 409 `TEAM_MODE_CONFIGURATION_INCOMPLETE`,
  mode giữ nguyên `COURSE` (atomic, không có trạng thái partial)
- Chuyển về `COURSE` không xoá `ProjectGroupWeightConfig` của Team nào — giữ historical/inactive

Legacy request path (`validateRequestedWeights`) **không mở rộng với `testWeight`/`researchWeight`**
(deprecated, không còn consumer FE mới) — vẫn chỉ nhận `{codeWeight, documentWeight}` cộng giá trị
`design` cũ (nếu có). Nếu admin duyệt request: `code`/`document` ghi trực tiếp, giá trị `design` cũ
ghi verbatim vào cột inactive `design_contribution_weight` (giữ lại, không discard), `test` ép về 0
— an toàn vì `ContributionSliceWeights.fromCourse` luôn renormalize theo tổng active slice.

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
- Slice weight source resolution (mode-aware, fail-closed COURSE/TEAM):
  `src/main/java/com/saga/be/service/contribution/ContributionSliceWeightResolver.java`
- Course slice-weight GET/PUT, mode switch, team-menu read, legacy request/approval:
  `src/main/java/com/saga/be/service/CourseContributionWeightService.java`
- Team/Project weight override:
  `src/main/java/com/saga/be/service/ProjectGroupWeightConfigService.java`
- Peer review submit (cách tạo starRating):
  `src/main/java/com/saga/be/service/PeerReviewService.java`
- Endpoints:
  `src/main/java/com/saga/be/controller/TeamContributionController.java`
  `src/main/java/com/saga/be/controller/CourseContributionWeightController.java`
  `src/main/java/com/saga/be/controller/ProjectGroupWeightConfigController.java`

