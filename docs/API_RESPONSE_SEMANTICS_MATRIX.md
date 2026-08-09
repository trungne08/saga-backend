# Ma trận semantics response API

## Account lifecycle M3B — 2026-08-09

| Method | Path | Success | Failure | Safety |
| --- | --- | --- | --- | --- |
| PATCH | `/api/admin/users/{id}/status` | 200 safe user response | 400 PENDING/Admin target; 404 unknown; 401/403 auth/CSRF | Student/Lecturer only; no cascade/provider |
| Any business API | `/api/**` trừ auth routes | existing success | 403 `ACCOUNT_STATUS_ACCESS_DENIED` khi current DB status không ACTIVE | current local status, không Cognito |
| GET | `/api/auth/me` | 200, current Student/Lecturer status | 401 anonymous | exempt status enforcement |

## AccountStatus M3A audit — 2026-08-09

`PATCH /api/admin/users/{id}/status` chưa được expose, nên không có success/failure contract runtime. Policy transition, target Admin/Lecturer, PENDING access và enforcement session cần được chốt trước khi thêm semantics API.

## Course M2B — 2026-08-09

| Method | Path | Success | Failure | Retention |
| --- | --- | --- | --- | --- |
| PUT | `/api/v1/courses/{id}` | 200 Course | 400 validation; 404 Course/reference inactive-missing; 409 duplicate code; 401/403 auth | không đổi dependency |
| DELETE | `/api/v1/courses/{id}` | 204 | 404 inactive/missing; 409 Team/Project/invitation/weight dependency; 401/403 auth | V20 soft-delete, active reads hide tombstone |

## Semester M2A — 2026-08-09

| Method | Path | Success | Failure | Retention |
| --- | --- | --- | --- | --- |
| PUT | `/api/v1/semesters/{id}` | 200 Semester | 400 validation/date; 404 inactive/missing; 409 duplicate; 401/403 auth | no cascade |
| DELETE | `/api/v1/semesters/{id}` | 204 | 404 inactive/missing; 409 Course dependency; 401/403 auth | V19 soft-delete, active reads hide tombstone |

## Admin Read Foundation — 2026-08-09

| Method | Path | Success / empty | Auth | Provider |
| --- | --- | --- | --- | --- |
| GET | `/api/admin/users` | `200 Page`; empty `[]` | 401 anon, 403 non-ADMIN | none |
| GET | `/api/admin/audit-logs` | `200 Page` newest-first; empty `[]` | 401 anon, 403 non-ADMIN | none |
| GET | `/api/admin/system-stats` | `200`; counts may be zero | 401 anon, 403 non-ADMIN | none |
| GET | `/api/admin/teams` | `200 Page`; empty `[]` | 401 anon, 403 non-ADMIN | none |
| GET | `/api/admin/projects` | `200 Page`; empty `[]` | 401 anon, 403 non-ADMIN | none |

Query enum/page/size không hợp lệ trả `400 INVALID_REQUEST`; size là 1..100. DTO sanitize cognitoSub, token, Authorization, provider raw body, IP, raw audit values, repository URL và không có Project DELETE.

Nguồn audit: OpenAPI sinh từ `/v3/api-docs` lúc chạy `GeneratedOpenApiDocumentationIntegrationTest` ngày 2026-08-07, đối chiếu controller/service/handler hiện hành. Có đúng **96 operations**; mỗi operation sinh ra có một dòng bên dưới.

Quy ước: `OAS` là response được khai báo trực tiếp trong OpenAPI; `TBD` là source/OpenAPI hiện không chứng minh contract chi tiết cho cột đó. `OK` nghĩa là endpoint có trong source và không có false-error đã biết; không suy diễn authorization hay business semantics. Provider code (JIRA_*/GITHUB_*/INTEGRATION_*) giữ nguyên qua `IntegrationException`.

**Update 2026-08-09 — Task Create:** `POST /api/v1/projects/{projectId}/tasks` nhận business `type`/`priority` cho normal flow; `issueTypeId`/`priorityId` là advanced optional override. Local metadata validation trả `400 JIRA_ISSUE_TYPE_INVALID` hoặc `400 JIRA_PRIORITY_INVALID`; auto-resolution zero/multiple candidate fail closed bằng `409 JIRA_*_RESOLUTION_NOT_FOUND` hoặc `409 JIRA_*_RESOLUTION_AMBIGUOUS`. Provider 404 thực sự vẫn giữ `409 JIRA_RESOURCE_NOT_FOUND`.

| Method | Path | Controller | Current success | Empty behavior | Parent missing | Missing prerequisite | 400 | 401 | 403 | 404 | 409 | provider/5xx | Current code/state | Classification | Recommendation |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| GET | `/api/v1/subjects/{id}` | SubjectController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getSubjectById; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/subjects/{id}` | SubjectController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | updateSubject; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/v1/subjects/{id}` | SubjectController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | deleteSubject; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/projects/{projectId}/tasks/{taskId}` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getTask; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | updateTask; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/v1/projects/{projectId}/tasks/{taskId}` | Generated: delete | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | delete; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/sprint` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | sprint; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/estimation` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | estimate; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/projects/{projectId}/tasks/{taskId}/assignee` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | assignee; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Generated: detail | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | detail; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Generated: update | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | update; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/v1/projects/{projectId}/sprints/{sprintId}` | Generated: delete_1 | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | delete_1; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/courses/contribution-slice-weight-requests/{requestId}/decision` | CourseContributionWeightController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | decideWeightChangeRequest; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/classes/{id}` | ClassController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getClassById; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/v1/classes/{id}` | ClassController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | updateClass; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/v1/classes/{id}` | ClassController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | deleteClass; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}` | Generated: get | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | get; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PUT | `/api/projects/{projectId}` | Generated: update_1 | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | update_1; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/webhooks/jira` | WebhookController | OAS: 202 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | jira; OAS: 202 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/webhooks/github` | WebhookController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | github; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews` | PeerReviewController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getSprintReviews; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews` | PeerReviewController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | submit; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/teams/{teamId}/contribution-override` | TeamContributionController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | requestContributionOverride; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/subjects` | SubjectController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getSubjects; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/subjects` | SubjectController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | createSubject; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/semesters` | SemesterController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getSemesters; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/semesters` | SemesterController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | createSemester; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/projects/{projectId}/tasks` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getTasks; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/projects/{projectId}/tasks` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | createTask; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/projects/{projectId}/tasks/{taskId}/transitions` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | transitions; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/projects/{projectId}/tasks/{taskId}/transitions` | ProjectTaskReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | transition; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/projects/{projectId}/sprints` | ProjectSprintController | OAS: 200, 401, 403, 404 | TBD | TBD | TBD | TBD | OAS | OAS | OAS | TBD | TBD | getProjectSprints; OAS: 200, 401, 403, 404 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/projects/{projectId}/sprints` | Generated: create | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | create; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/projects/{projectId}/sprints/{sprintId}/start` | ProjectSprintController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | start; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/projects/{projectId}/sprints/{sprintId}/close` | ProjectSprintController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | close; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCourses; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/courses` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | createCourse; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/courses/{courseId}/import-students` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | importStudents; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/courses/{courseId}/contribution-slice-weight-requests` | CourseContributionWeightController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | requestWeightChange; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/classes` | ClassController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getClasses; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/v1/classes` | ClassController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | createClass; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/teams/{teamId}/projects` | Generated: create_1 | OAS: 201 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | create_1; OAS: 201 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/projects/{projectId}/sync` | ProjectIntegrationController | OAS: 202 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | sync; OAS: 202 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/projects/{projectId}/jira/link` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | jiraLink; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/projects/{projectId}/github/repositories` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubRepositories; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/projects/{projectId}/github/repositories/{repositoryId}/connect` | ProjectIntegrationController | OAS: 202 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubRepositoryReconnect; OAS: 202 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| POST | `/api/integrations/callback-results/{resultId}/consume` | IntegrationCallbackResultController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | consume; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| PATCH | `/api/integrations/identity-mappings/{mappingId}` | IdentityMappingReviewController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | review; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/privacy` | PrivacyPolicyController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getPrivacyPolicy; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/teams/{teamId}/sprints` | ProjectSprintController | OAS: 200, 400, 401, 403, 404 | TBD | TBD | TBD | OAS | OAS | OAS | OAS | TBD | TBD | PROJECT_NOT_CREATED/EMPTY/READY; TEAM_NOT_FOUND; authorization trước project=null | OK (false-error fixed) | Giữ regression Team Sprint. |
| GET | `/api/v1/teams/{teamId}/sprints/{sprintId}/peer-reviews/candidates` | PeerReviewController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCandidates; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/teams/{teamId}/peer-review-rubric` | PeerReviewRubricController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getRubric; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/teams/{teamId}/contribution-evaluation` | TeamContributionController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getContributionEvaluation; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/semesters/{id}` | SemesterController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getSemesterById; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/peer-review-rubrics/default` | PeerReviewDefaultRubricController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getDefaultRubric; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{id}` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCourseById; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/sprints/velocity` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | velocity; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/members` | TeamRosterController | OAS: 200, 401, 403, 404 | TBD | TBD | TBD | TBD | OAS | OAS | OAS | TBD | TBD | getMembers; OAS: 200, 401, 403, 404 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/interactions` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | interactions; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/heatmap` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | heatmap; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/teams/{teamId}/detail` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | teamDetail; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/students` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCourseStudents; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/students/{studentId}` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCourseStudent; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/students/{studentId}/progress` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | progress; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/students/{studentId}/contribution-detail` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | contributionDetail; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/students/{studentId}/activities` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | activities; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/early-warnings` | LecturerAnalyticsController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | earlyWarnings; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/{courseId}/contribution-slice-weights` | CourseContributionWeightController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getCurrentWeights; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/instructors` | CourseController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | getLecturersForCourseAssignment; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/v1/courses/contribution-slice-weight-requests` | CourseContributionWeightController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | listWeightChangeRequests; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/sync-status` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | syncStatus; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/sync-history` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | syncHistory; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/jira/connect` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | jiraConnect; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/integrations` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | integrations; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/github/setup` | Generated: githubSetup | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubSetup; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/github/repositories/{repositoryId}/commits` | ProjectGitHubReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | commits; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/github/repositories/{repositoryId}/branches` | ProjectGitHubReadController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | branches; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/github/install` | ProjectIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubInstall; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/github/callback` | Generated: githubCallback | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubCallback; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/projects/{projectId}/dashboard-stats` | ProjectDetailController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | dashboardStats; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/me/integrations` | PersonalIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | connections; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/me/integrations/jira/connect` | PersonalIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | connectJira; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/me/integrations/github/connect` | PersonalIntegrationController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | connectGitHub; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/me/integrations/github/callback` | Generated: githubCallback_1 | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubCallback_1; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/me/courses/{courseId}/team/members` | MyCourseTeamController | OAS: 200, 401, 403, 404, 409 | TBD | TBD | TBD | TBD | OAS | OAS | OAS | OAS | TBD | getMyCourseTeamMembers; OAS: 200, 401, 403, 404, 409 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/integrations/jira/callback` | JiraIntegrationCallbackController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | callback; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/integrations/identity-mappings` | IdentityMappingReviewController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | mappings; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/integrations/github/setup` | Generated: githubSetup_1 | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubSetup_1; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/integrations/github/project/callback` | Generated: githubCallback_2 | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubCallback_2; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/auth/me` | AuthController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | me; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/auth/login` | AuthController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | login; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| GET | `/api/auth/csrf` | AuthController | OAS: 200 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | csrf; OAS: 200 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/projects/{projectId}/jira` | ProjectIntegrationController | OAS: 204 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | jiraDisconnect; OAS: 204 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/projects/{projectId}/github/repositories/{repositoryId}` | ProjectIntegrationController | OAS: 204 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | githubRepositoryDisconnect; OAS: 204 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/me/integrations/jira` | Generated: disconnectJira | OAS: 204 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | disconnectJira; OAS: 204 | OK | Bổ sung response metadata chi tiết theo task riêng. |
| DELETE | `/api/me/integrations/github` | PersonalIntegrationController | OAS: 204 | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD | TBD (provider) | disconnectGitHub; OAS: 204 | OK | Bổ sung response metadata chi tiết theo task riêng. |

## Missing APIs

- **MISSING_API:** `DELETE /api/projects/{projectId}` không có trong generated OpenAPI/controller. Source baseline đã ghi đây là thiếu có chủ đích cho đến khi có dependency-guard/retention design; không triển khai trong milestone này.

## Kết luận audit

- 96/96 operation được liệt kê. Phần lớn operation hiện chỉ khai báo success trong OpenAPI nên các semantics failure/empty chi tiết vẫn **TBD** cho task riêng.
- `GET /api/v1/teams/{teamId}/sprints` là false-error đã xác nhận duy nhất được sửa trong milestone này: Team được phép truy cập nhưng chưa có Project trả 200 state `PROJECT_NOT_CREATED`, không còn 404.
- `GET /api/v1/projects/{projectId}/sprints` và `GET /api/v1/teams/{teamId}/sprints` trả thêm additive `sprints[i].state`, lấy nguyên `String` từ canonical local Sprint (`future` / `active` / `closed` khi Jira cung cấp). Top-level list `state` giữ semantics `PROJECT_NOT_CREATED` / `EMPTY` / `READY`; list read không gọi Jira provider.
- Không có API mới, migration, entity hay repository query được tạo từ audit này.

## Admin global rubric M4B

| Method | Route | Success | Failure controlled | Ghi chú |
|---|---|---|---|---|
| POST | `/api/admin/peer-review-rubrics` | 201 | 400 DTO, 401 anonymous, 403 role/CSRF, 409 khi đã có 4 active | ADMIN; chỉ tạo global |
| PUT | `/api/admin/peer-review-rubrics/{id}` | 200 | 400 DTO/subject-specific, 401/403, 404 missing/tombstone | không đổi subject/deletedAt |
| DELETE | `/api/admin/peer-review-rubrics/{id}` | 204 | 401/403, 404 missing/tombstone/repeated | soft-delete, không cascade |

## Admin Course progress overview M5

| Method | Route | Success | Failure controlled | Ghi chú |
|---|---|---|---|---|
| GET | `/api/admin/course-progress-overview` | 200 Page | 400 pagination sai, 401 anonymous, 403 Lecturer/Student | DB local-only; Course tombstone bị loại |

## Admin Course report export M6

| Method | Route | Success | Failure controlled | Ghi chú |
|---|---|---|---|---|
| GET | `/api/admin/reports/courses/{courseId}/export` | 200 XLSX attachment | 401 anonymous, 403 Lecturer/Student, 404 missing/tombstone | no-store, local-only, không phải grade |

## Admin global user import M7

| Method | Route | Success | Failure controlled | Ghi chú |
|---|---|---|---|---|
| POST | `/api/admin/users/import` | 200 `AdminUserImportResponse` | 400 file/header/required/formula/duplicate/role sai; 401 anonymous; 403 non-ADMIN hoặc CSRF; 409 cross-profile/partial identity | multipart `role=STUDENT|LECTURER`, `file`; summary không identity row; không side effect Course/Team/invitation/Cognito |

## Admin active Semester setting M8A

| Method | Route | Success | Failure controlled | Ghi chú |
|---|---|---|---|---|
| GET | `/api/admin/settings/active-semester` | 200 `ActiveSemesterSettingResponse` | 401 anonymous, 403 non-ADMIN | ADMIN-only current explicit setting; unset trả Semester fields null |
| PUT | `/api/admin/settings/active-semester` | 200 `ActiveSemesterSettingResponse` | 400 body thiếu/sai UUID, 401 anonymous, 403 non-ADMIN/CSRF, 404 missing/tombstoned Semester | chỉ `semesterId`; repeat deterministic; không mutate Course/Semester |
