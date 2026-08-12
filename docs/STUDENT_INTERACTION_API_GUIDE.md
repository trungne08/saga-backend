# Student Interaction API Usage Guide

## 1. Endpoint

`GET /api/v1/courses/{courseId}/teams/{teamId}/students/{studentId}/interactions`

## 2. Path Parameters

- `courseId` *(required)*: ID của course
- `teamId` *(required)*: ID của team
- `studentId` *(required)*: ID của sinh viên trung tâm

## 3. Example Request

```http
GET /api/v1/courses/{courseId}/teams/{teamId}/students/{studentId}/interactions
```

## 4. Response Structure

Response trả về graph tương tác xoay quanh một sinh viên trong team.

### 4.1 Main fields

- `courseId`
- `teamId`
- `studentId`
- `nodes`
- `edges`

### 4.2 Node fields

Mỗi phần tử trong `nodes` gồm:

- `studentId`
- `studentCode`
- `fullName`
- `degree`

### 4.3 Edge fields

Mỗi phần tử trong `edges` gồm:

- `fromStudentId`
- `toStudentId`
- `sourceType`
- `sourceCount`
- `directed`

## 5. Meaning of Interaction Types

- `REVIEWED`: sinh viên review bài của sinh viên khác
- `COMMENTED_ON`: sinh viên comment reply lên comment của sinh viên khác
- `ASSIGNED_TO`: sinh viên gán task cho sinh viên khác
- `COLLABORATED_WITH`: sinh viên có commit liên quan đến task của sinh viên khác

## 6. Frontend Rendering Guide

1. Gọi API với `courseId`, `teamId`, `studentId`.
2. Dùng `nodes` làm danh sách đỉnh trong graph.
3. Dùng `edges` để vẽ cạnh có hướng.
4. Dùng `degree` để xác định node trung tâm hoặc node ít tương tác.
5. Dùng `sourceType` để tô màu cạnh theo loại tương tác.

## 7. Common Usage

- xem một sinh viên đang tương tác với ai
- phát hiện sinh viên cô lập trong team
- xem mối liên kết review / comment / task / commit quanh một thành viên
- hỗ trợ giảng viên hỏi nhanh team có đang phối hợp tốt không

## 8. Notes

- Graph này chỉ tập trung vào một sinh viên làm trung tâm.
- Các cạnh được gom theo loại tương tác và số lần xảy ra.
- Nếu sinh viên không thuộc team, API sẽ trả lỗi.
