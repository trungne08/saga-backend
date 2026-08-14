package com.saga.be.controller;

import com.saga.be.dto.request.ProjectTypeCreateRequest;
import com.saga.be.dto.response.ProjectTypeResponse;
import com.saga.be.service.ProjectTypeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Loại dự án", description = "Danh mục loại dự án dùng khi tạo dự án.")
@RequestMapping("/api/project-types")
@RequiredArgsConstructor
public class ProjectTypeController {

    private final ProjectTypeService projectTypeService;

    @GetMapping
    public List<ProjectTypeResponse> list() {
        return projectTypeService.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProjectTypeResponse> create(@Valid @RequestBody ProjectTypeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectTypeService.create(request));
    }
}
