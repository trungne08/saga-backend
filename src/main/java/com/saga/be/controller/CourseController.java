package com.saga.be.controller;

import com.saga.be.dto.request.CourseRequest;
import com.saga.be.dto.response.CourseStudentRosterResponse;
import com.saga.be.dto.response.LecturerOptionResponse;
import com.saga.be.entity.Course;
import com.saga.be.service.CourseService;
import com.saga.be.service.ExcelImportService;
import com.saga.be.security.SagaPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Validated
public class CourseController {

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
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        return ResponseEntity.ok(courseService.getLecturersForCourseAssignment(keyword, pageable));
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

    @PostMapping(value = "/{courseId}/import-students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<String> importStudents(
            @AuthenticationPrincipal SagaPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam("file") MultipartFile file) {
        
        // Kiểm tra định dạng file
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".xlsx")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Vui lòng tải lên file Excel (.xlsx) hợp lệ");
        }

        excelImportService.importStudentsToCourse(principal, courseId, file);
        return ResponseEntity.ok("Import danh sách sinh viên thành công!");
    }
}
