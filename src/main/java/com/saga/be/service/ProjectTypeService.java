package com.saga.be.service;

import com.saga.be.dto.response.ProjectTypeResponse;
import com.saga.be.repository.ProjectTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
