package com.saga.be.service;

import com.saga.be.dto.request.SemesterRequest;
import com.saga.be.entity.Semester;
import com.saga.be.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;

    public List<Semester> getAllSemesters() {
        return semesterRepository.findAll();
    }

    public Semester getSemesterById(UUID id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Semester not found"));
    }

    @Transactional
    public Semester createSemester(SemesterRequest request) {
        if (semesterRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("Semester code already exists");
        }
        Semester semester = Semester.builder()
                .code(request.getCode())
                .name(request.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();
        return semesterRepository.save(semester);
    }

    public Page<Semester> searchSemesters(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return semesterRepository.findAll(pageable);
        }
        return semesterRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(keyword, keyword, pageable);
    }
}