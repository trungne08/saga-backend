# FE Guide - Luồng thêm sinh viên vào course

## 1) Tổng quan luồng

Luồng hiện tại tách thành 4 bước:

1. **Admin tải template mẫu thêm sinh viên vào course** (template trống 5 cột).
2. **Admin import danh sách sinh viên vào course** bằng template 5 cột (không có cột phân nhóm).
3. **Giảng viên tải template phân nhóm** đã có sẵn danh sách sinh viên thuộc course.
4. **Giảng viên import lại template đã điền Group/Leader** để tạo Team membership.

> Ghi chú: Hệ thống luôn chuẩn hóa identity theo `StudentCode` + `Email` để chống trùng/đụng dữ liệu.

---

## 2) API chi tiết

## 2.1 Admin tải template mẫu thêm sinh viên vào course

- **Method**: `GET`
- **Path**: `/api/v1/courses/{courseId}/admin-students-template`
- **Role**: `ADMIN`
- **Response**:
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - file đính kèm: `course-admin-student-template-<courseCode>.xlsx`
  - file chỉ có header 5 cột, chưa có dữ liệu sinh viên

### Header trong template admin

1. `Class`
2. `StudentCode`
3. `Email`
4. `MemberCode`
5. `FullName`

---

## 2.2 Admin import danh sách sinh viên vào course (không phân nhóm)

- **Method**: `POST`
- **Path**: `/api/v1/courses/{courseId}/admin-import-students-template`
- **Role**: `ADMIN`
- **Content-Type**: `multipart/form-data`
- **Form-data**:
  - `file`: `.xlsx`

### Template bắt buộc (đúng thứ tự, đúng tên cột)

1. `Class`
2. `StudentCode`
3. `Email`
4. `MemberCode`
5. `FullName`

> Không được có cột `Group`/`Leader` trong file admin template.

### Success response (200)

```json
{
  "operation": "ADMIN_TEMPLATE_IMPORT",
  "message": "Import danh sách sinh viên vào course thành công!",
  "totalRows": 40,
  "createdStudents": 30,
  "reusedStudents": 10,
  "invitationsQueued": 35,
  "teamsCreated": 0,
  "membershipsCreated": 0,
  "groupingApplied": false
}
```

---

## 2.3 Tải template phân nhóm sinh viên cho giảng viên

- **Method**: `GET`
- **Path**: `/api/v1/courses/{courseId}/students-grouping-template`
- **Role**: `ADMIN` hoặc `LECTURER` (đúng quyền course)
- **Response**:
  - `Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  - file đính kèm: `course-student-template-<courseCode>.xlsx`

### Cột trong template giảng viên

1. `Class`
2. `StudentCode`
3. `Email`
4. `MemberCode`
5. `FullName`
6. `Group` (để trống để chưa phân nhóm)
7. `Leader` (`x` nếu là leader, để trống nếu member)

---

## 2.4 Import phân nhóm vào course

- **Method**: `POST`
- **Path**: `/api/v1/courses/{courseId}/import-students`
- **Role**: `ADMIN` hoặc `LECTURER` (đúng quyền course)
- **Content-Type**: `multipart/form-data`
- **Form-data**:
  - `file`: `.xlsx` (template 7 cột bên trên)

### Success response (200)

```json
{
  "operation": "COURSE_GROUPING_IMPORT",
  "message": "Import danh sách sinh viên và phân nhóm thành công!",
  "totalRows": 40,
  "createdStudents": 0,
  "reusedStudents": 40,
  "invitationsQueued": 2,
  "teamsCreated": 8,
  "membershipsCreated": 40,
  "groupingApplied": true
}
```

---

## 3) Error response chuẩn

Tất cả lỗi trả về theo format:

```json
{
  "timestamp": "2026-08-10T12:00:00Z",
  "status": 400,
  "error": "INVALID_HEADER",
  "message": "The workbook header does not match the Course import schema",
  "path": "/api/v1/courses/{courseId}/import-students"
}
```

Các `error` FE cần xử lý chính:

- `INVALID_HEADER`: sai header template.
- `MALFORMED_WORKBOOK`: file không đọc được/không đúng xlsx.
- `FILE_TOO_LARGE`: file vượt giới hạn.
- `ROW_LIMIT`: số dòng vượt giới hạn hỗ trợ.
- `DUPLICATE_IN_FILE`: trùng identity trong cùng file.
- `INVALID_ROW`: thiếu dữ liệu bắt buộc / có dữ liệu cột thừa.
- `FORMULA_NOT_ALLOWED`: có ô công thức.
- `IDENTITY_CONFLICT`: StudentCode/Email xung đột profile đang có.
- `COURSE_TEAM_MEMBERSHIP_CONFLICT`: sinh viên đã thuộc team khác trong course.
- `ACCESS_DENIED`: không đúng quyền.
- `RESOURCE_NOT_FOUND`: course không tồn tại.

---

## 4) Lưu ý tích hợp FE

- Nên hiển thị chi tiết các field thống kê (`createdStudents`, `reusedStudents`, `membershipsCreated`, `teamsCreated`) sau mỗi lần import.
- Khi import admin thành công, FE có thể gợi ý ngay nút “Tải template phân nhóm” cho giảng viên.
- Khi import phân nhóm thành công, FE nên refresh danh sách roster/team để hiển thị trạng thái mới nhất.
