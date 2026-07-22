package com.saga.be.service;

import com.saga.be.dto.request.ClassRequest;
import com.saga.be.entity.Class;
import com.saga.be.repository.ClassRepository;
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
public class ClassService {

    private final ClassRepository classRepository;

    public Class getClassById(UUID id) {
        return classRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    @Transactional
    public Class createClass(ClassRequest request) {
        String classCode = request.getClassCode().trim();
        if (classRepository.existsByClassCode(classCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Class code already exists");
        }
        Class clazz = Class.builder()
                .classCode(classCode)
                .name(request.getName().trim())
                .build();
        return classRepository.save(clazz);
    }

    public Page<Class> searchClasses(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return classRepository.findAll(pageable);
        }
        return classRepository.findByNameContainingIgnoreCaseOrClassCodeContainingIgnoreCase(keyword, keyword, pageable);
    }
}
