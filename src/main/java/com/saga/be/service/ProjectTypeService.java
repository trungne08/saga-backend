package com.saga.be.service;

import com.saga.be.dto.request.ProjectTypeCreateRequest;
import com.saga.be.dto.response.ProjectTypeResponse;
import com.saga.be.entity.ProjectType;
import com.saga.be.repository.ProjectTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProjectTypeService {

    private final ProjectTypeRepository projectTypeRepository;

    @Transactional(readOnly = true)
    public List<ProjectTypeResponse> list() {
        return projectTypeRepository.findAllByOrderByNameAsc().stream()
                .map(ProjectTypeResponse::from)
                .toList();
    }

    @Transactional
    public ProjectTypeResponse create(ProjectTypeCreateRequest request) {
        String code = request.code().trim();
        if (projectTypeRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Project type code already exists");
        }
        ProjectType projectType = ProjectType.builder()
                .code(code)
                .name(request.name().trim())
                .description(normalize(request.description()))
                .criteriaConfig(normalize(request.criteriaConfig()))
                .build();
        return ProjectTypeResponse.from(projectTypeRepository.save(projectType));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
