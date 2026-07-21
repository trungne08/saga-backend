package com.saga.be.controller;

import com.saga.be.dto.request.SemesterRequest;
import com.saga.be.entity.Semester;
import com.saga.be.service.SemesterService;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
public class SemesterController {

    private final SemesterService semesterService;

    @GetMapping
    public ResponseEntity<List<Semester>> getAllSemesters() {
        return ResponseEntity.ok(semesterService.getAllSemesters());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Semester> getSemesterById(@PathVariable UUID id) {
        return ResponseEntity.ok(semesterService.getSemesterById(id));
    }

    @PostMapping
    public ResponseEntity<Semester> createSemester(@RequestBody SemesterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(semesterService.createSemester(request));
    }

    @GetMapping
    public ResponseEntity<Page<Semester>> getSemesters(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(semesterService.searchSemesters(keyword, pageable));
    }
}