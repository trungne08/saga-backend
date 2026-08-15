package com.saga.be.service;

import com.saga.be.dto.request.SemesterRequest;
import com.saga.be.entity.Semester;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.ActiveSemesterSettingRepository;
import com.saga.be.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final ActiveSemesterSettingRepository activeSemesterSettingRepository;

    public Semester getSemesterById(UUID id) {
        return requireActiveSemester(id);
    }

    private Semester requireActiveSemester(UUID id) {
        return semesterRepository.findByIdAndDeletedAtIsNull(id)
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

    @Transactional
    public Semester updateSemester(UUID id, SemesterRequest request) {
        Semester semester = requireActiveSemester(id);
        String code = request.getCode().trim();
        if (semesterRepository.existsByCodeAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Semester code already exists");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Semester end date must not be before start date");
        }
        semester.setCode(code);
        semester.setName(request.getName().trim());
        semester.setStartDate(request.getStartDate());
        semester.setEndDate(request.getEndDate());
        return semesterRepository.save(semester);
    }

    @Transactional
    public void softDeleteSemester(UUID id) {
        Semester semester = requireActiveSemester(id);
        if (courseRepository.existsBySemesterId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Semester is currently used by a course");
        }
        if (activeSemesterSettingRepository.existsBySemesterId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Semester is currently selected as the active semester");
        }
        semester.setDeletedAt(LocalDateTime.now());
        semesterRepository.save(semester);
    }

    public Page<Semester> searchSemesters(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return semesterRepository.findAllByDeletedAtIsNull(pageable);
        }
        return semesterRepository.searchActive(keyword.trim(), pageable);
    }
}
