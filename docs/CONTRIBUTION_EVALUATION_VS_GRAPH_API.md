# Evaluation vs Graph — hai API đóng góp cho Frontend

Ngày: 2026-08-16. OpenAPI **150**. Công thức: `docs/CONTRIBUTION_CALCULATION_SPEC.md`.

Hai GET này **cùng một phép tính**, **cùng quyền**, **cùng Team**. Khác nhau ở **hình dạng JSON** — chọn API theo UI, không gọi cả hai rồi cộng điểm.

| | Evaluation | Graph |
|---|---|---|
| Path | `GET /api/v1/teams/{teamId}/contribution-evaluation` | `GET /api/v1/teams/{teamId}/contribution-graph` |
| Dùng khi | Radar, bar so sánh thành viên, line `%` theo sprint, stacked SP, bảng số | Flowchart node/cạnh (tiêu chí → sinh viên → peer → `%`) |
| Shape | `members[]` phẳng + `sprintBreakdowns[]` | `nodes[]` + `edges[]` + `tasks[]` trên cạnh |
| Có list task theo cạnh? | Không | Có (`edges[].tasks[]`) |
| Có số theo từng sprint? | Có | Không (graph là cả dự án) |

Auth giống nhau: session cookie `JSESSIONID`, `credentials: "include"`. GET không CSRF, không Bearer. **LECTURER** đúng Course, **STUDENT LEADER** đúng Team. ADMIN / MEMBER / MENTOR = **403**.

---

## 1. Gọi API nào

```
Cần vẽ gì?
├─ Radar 4 tiêu chí / bar % thành viên / line % sprint / stacked SP
│     → evaluation
└─ Sơ đồ flowchart (bấm cạnh ra task)
      → graph
```

Không dùng graph để vẽ radar. Không dùng evaluation rồi tự bịa `nodes`/`edges` nếu màn hình là flowchart — graph đã gói sẵn.

Không gọi `/api/analytics/*` cho đóng góp. Heatmap / overview / interactions / burndown là **activity**, không phải `%` đóng góp.

---

## 2. Công thức — một nguồn, hai payload

Cả hai API dùng mixer SAGA. **Không** dùng hệ số mockup `CODE × 2.0` / `DESIGN` / `DOCS`.

```
slice = Σ (SP_tiêu_chí × weightRatio)
P     = sao_cá_nhân / sao_team          (0..1, không phải ×1.1)
%     = (slice × P) / Σ(slice × P) × 100
```

Bốn tiêu chí: **CODE / TEST / DOCUMENT / RESEARCH**. Trọng số tổng 100 (ví dụ 40% → `weightRatio = 0.40`).

FE **không nhân** `peerReviewScore` / `peerCoefficient` thêm lên `%` cuối. Backend đã nhân rồi.

---

## 3. Evaluation — số liệu theo người và theo sprint

Response: `{ teamId, projectId, evaluatedAt, members[] }`.

Mỗi member:

| Field | Dùng để |
|---|---|
| `fullName`, `studentCode` | Nhãn |
| `code/test/document/researchContributionPercentage` | **Radar** — share tiêu chí cả dự án, chưa phải `%` cuối |
| `finalContributionPercentage` | **Bar so sánh thành viên** — đã gồm peer |
| `sliceScore` / `sliceContributionPercentage` | Điểm / `%` **trước** peer |
| `peerReviewScore` | `P` cả dự án (0..1), chỉ hiện |
| `sprintBreakdowns[].contributionPercentage` | **Line `%` theo sprint** (đã nhân `P_s`; chưa peer thì `P_s = 1`) |
| `sprintBreakdowns[].code/test/document/researchStoryPoints` | **Stacked bar SP** theo sprint — SP được công nhận, không phải `%` |
| `warnings[]` | `NO_PEER_REVIEW`, `LOW_PEER_REVIEW`, `INSUFFICIENT_EVIDENCE`, `NO_EVIDENCE` |

Không có `nodes` / `edges` / danh sách task.

```ts
fetch(`/api/v1/teams/${teamId}/contribution-evaluation`, {
  credentials: "include",
});
```

---

## 4. Graph — flowchart để vẽ luôn

Response: `{ teamId, projectId, evaluatedAt, formula, weights, nodes[], edges[] }`.

`formula` là chuỗi giải thích, không phải parser. `weights` có cả ratio (`0.40`) và percent (`40`).

### Node

`kind` chỉ hai giá trị:

- `CRITERION` — luôn đủ 4: CODE, TEST, DOCUMENT, RESEARCH. Dùng `id`, `criterion`, `weightRatio`, `weightPercent`.
- `STUDENT` — kể cả `sliceScore = 0`. Dùng `studentId`, `fullName`, `studentCode`, `roleInTeam`, `sliceScore` (base slices), `peerCoefficient` (`P`), `adjustedScore` (`slice × P`), `finalContributionPercentage`, `warnings`.

Nối flowchart:

```
CRITERION  --edge-->  STUDENT  -->  hiện P và % cuối trên chính node STUDENT
```

Không có node `%` riêng. Không có node GHOSTING. Không có Chốt số / Publish.

### Cạnh

Mỗi cạnh = một tiêu chí → một sinh viên khi Σ SP được công nhận **> 0**.

| Field | Dùng để |
|---|---|
| `source` / `target` | `id` node (`criterion:CODE`, `student:<uuid>`) |
| `storyPoints` | Nhãn cạnh, ví dụ `15 SP` |
| `weightedSlice` | `SP × weightRatio` (không phải ×2.0) |
| `tasks[]` | Click cạnh → danh sách task (`externalKey`, `title`, `sprintName`, `storyPoints`) |

Cạnh 0 SP không trả. DOCUMENT/RESEARCH không có file/link thì task đó không vào cạnh.

```ts
fetch(`/api/v1/teams/${teamId}/contribution-graph`, {
  credentials: "include",
});
```

---

## 5. Cùng số, khác chỗ để lấy

Cùng một người, cùng một Team:

| Ý trên UI | Evaluation | Graph |
|---|---|---|
| Slice trước peer | `member.sliceScore` | node STUDENT `sliceScore` |
| Hệ số peer `P` | `member.peerReviewScore` | node STUDENT `peerCoefficient` |
| `%` cuối | `member.finalContributionPercentage` | node STUDENT `finalContributionPercentage` |
| SP CODE cả dự án | cộng `sprintBreakdowns[].codeStoryPoints` | cạnh `criterion = CODE` → `storyPoints` |
| Slice phần CODE | (tự nhân SP × trọng số) | cạnh `weightedSlice` |
| Task tạo ra cạnh đó | không có | `edges[].tasks[]` |
| `%` từng sprint | `sprintBreakdowns[].contributionPercentage` | không có — dùng evaluation |

Hai API không lệch công thức. Nếu số khác nhau → bug, không “làm tròn khác nhau”.

---

## 6. Việc FE không làm

- Không vẽ tiêu chí **DESIGN**. Không nhân CODE × 2.0.
- Không nhân peer lần hai trên `%` cuối.
- Không hiện GHOSTING — chỉ `warnings[]` hiện có.
- Không gọi graph để lấy series theo sprint.
- Không gửi Bearer. Không CSRF trên hai GET này.
- Không mở màn này cho Admin.
- Response không có email, Cognito, reviewer comment — đừng expect các field đó.
