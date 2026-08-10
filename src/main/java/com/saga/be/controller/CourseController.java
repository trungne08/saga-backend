package com.saga.be.controller;

import com.saga.be.dto.request.CourseRequest;
import com.saga.be.dto.response.CourseStudentRosterResponse;
import com.saga.be.dto.response.CourseStudentBasicInfoResponse;
import com.saga.be.dto.response.CourseStudentImportResponse;
import com.saga.be.dto.response.LecturerOptionResponse;
import com.saga.be.entity.Course;
import com.saga.be.service.CourseService;
import com.saga.be.service.ExcelImportService;
import com.saga.be.service.ExcelImportService.CourseStudentImportSummary;
import com.saga.be.service.ExcelImportService.ExportedCourseStudentTemplate;
import com.saga.be.security.SagaPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Khóa học", description = "Quản lý khóa học và thành viên khóa học.")
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Validated
public class CourseController {
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CourseService courseService;
    private final ExcelImportService excelImportService;

    @GetMapping("/{id}")
    public ResponseEntity<Course> getCourseById(@PathVariable UUID id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> createCourse(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Course> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody CourseRequest request
    ) {
        return ResponseEntity.ok(courseService.updateCourse(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCourse(@PathVariable UUID id) {
        courseService.softDeleteCourse(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<Course>> getCourses(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID semesterId,
            @RequestParam(required = false) UUID instructorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(courseService.getCoursesWithFilters(subjectId, semesterId, instructorId, pageable));
    }

    @GetMapping("/instructors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<LecturerOptionResponse>> getLecturersForCourseAssignment(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "fullName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(courseService.getLecturersForCourseAssignment(
                keyword,
                sortBy,
                sortDirection,
                pageable
        ));
    }

    @GetMapping("/{courseId}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<CourseStudentRosterResponse> getCourseStudents(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "all") String hasTeam,
            @RequestParam(defaultValue = "studentCode") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(courseService.getCourseRoster(
                principal,
                courseId,
                keyword,
                hasTeam,
                sortBy,
                sortDirection,
                pageable
        ));
    }

    @GetMapping("/{courseId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<CourseStudentBasicInfoResponse> getCourseStudent(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @PathVariable UUID studentId
    ) {
        return ResponseEntity.ok(courseService.getCourseStudentBasicInfo(
                principal,
                courseId,
                studentId
        ));
    }

    @PostMapping(value = "/{courseId}/import-students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @Operation(summary = "Import phân nhóm sinh viên vào course",
            description = "Nhập file XLSX template để tạo/cập nhật Team membership của sinh viên trong course.")
    public ResponseEntity<CourseStudentImportResponse> importStudents(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file) {
        CourseStudentImportSummary summary = excelImportService.importStudentsToCourse(principal, courseId, file);
        return ResponseEntity.ok(CourseStudentImportResponse.from(
                "COURSE_GROUPING_IMPORT",
                "Import danh sách sinh viên và phân nhóm thành công!",
                summary
        ));
    }

    @PostMapping(value = "/{courseId}/admin-import-students-template", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin import danh sách sinh viên vào course",
            description = "Nhập file XLSX template 5 cột (Class, RollNumber, Email, MemberCode, FullName) để gắn danh sách sinh viên vào course.")
    public ResponseEntity<CourseStudentImportResponse> importStudentsByAdminTemplate(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file
    ) {
        CourseStudentImportSummary summary = excelImportService.importStudentsToCourseByAdminTemplate(
                principal,
                courseId,
                file
        );
        return ResponseEntity.ok(CourseStudentImportResponse.from(
                "ADMIN_TEMPLATE_IMPORT",
                "Import danh sách sinh viên vào course thành công!",
                summary
        ));
    }

    @GetMapping("/{courseId}/students-grouping-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @Operation(summary = "Tải template phân nhóm sinh viên",
            description = "Tải file XLSX gồm danh sách sinh viên đã thuộc course để giảng viên điền Group/Leader rồi import lại.")
    public ResponseEntity<byte[]> downloadStudentsGroupingTemplate(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId
    ) {
        ExportedCourseStudentTemplate template = excelImportService.exportCourseStudentTemplate(principal, courseId);
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(template.filename(), StandardCharsets.UTF_8).build().toString())
                .body(template.bytes());
    }
}
