package com.saga.be.service;

import com.saga.be.dto.request.SubjectRequest;
import com.saga.be.entity.Subject;
import com.saga.be.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    public Subject getSubjectById(UUID id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found"));
    }

    @Transactional
    public Subject createSubject(SubjectRequest request) {
        if (subjectRepository.existsBySubjectCode(request.getSubjectCode())) {
            throw new RuntimeException("Subject code already exists");
        }
        Subject subject = Subject.builder()
                .subjectCode(request.getSubjectCode())
                .name(request.getName())
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