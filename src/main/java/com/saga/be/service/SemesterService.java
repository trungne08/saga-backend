package com.saga.be.service;

import com.saga.be.dto.request.SemesterRequest;
import com.saga.be.entity.Semester;
import com.saga.be.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;

    public Semester getSemesterById(UUID id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
    }

    @Transactional
    public Semester createSemester(SemesterRequest request) {
        String code = request.getCode().trim();
        if (semesterRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Semester code already exists");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Semester end date must not be before start date");
        }
        Semester semester = Semester.builder()
                .code(code)
                .name(request.getName().trim())
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
