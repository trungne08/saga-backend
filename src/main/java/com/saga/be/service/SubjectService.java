package com.saga.be.service;

import com.saga.be.dto.request.SubjectRequest;
import com.saga.be.entity.Subject;
import com.saga.be.repository.SubjectRepository;
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
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public Subject getSubjectById(UUID id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
    }

    @Transactional
    public Subject createSubject(SubjectRequest request) {
        String subjectCode = request.getSubjectCode().trim();
        if (subjectRepository.existsBySubjectCode(subjectCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subject code already exists");
        }
        Subject subject = Subject.builder()
                .subjectCode(subjectCode)
                .name(request.getName().trim())
                .build();
        return subjectRepository.save(subject);
    }

    public Page<Subject> searchSubjects(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return subjectRepository.findAll(pageable);
        }
        return subjectRepository.findByNameContainingIgnoreCaseOrSubjectCodeContainingIgnoreCase(keyword, keyword, pageable);
    }
}
