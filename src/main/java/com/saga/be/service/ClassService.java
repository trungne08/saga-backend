package com.saga.be.service;

import com.saga.be.dto.request.ClassRequest;
import com.saga.be.entity.Class;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;

    public Class getClassById(UUID id) {
        return requireActiveClass(id);
    }

    private Class requireActiveClass(UUID id) {
        return classRepository.findByIdAndDeletedAtIsNull(id)
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

    @Transactional
    public Class updateClass(UUID id, ClassRequest request) {
        Class clazz = requireActiveClass(id);
        String classCode = request.getClassCode().trim();
        if (classRepository.existsByClassCodeAndIdNot(classCode, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Class code already exists");
        }
        clazz.setClassCode(classCode);
        clazz.setName(request.getName().trim());
        return classRepository.save(clazz);
    }

    @Transactional
    public void softDeleteClass(UUID id) {
        Class clazz = requireActiveClass(id);
        if (courseRepository.existsByClazzId(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Class is currently used by a course"
            );
        }
        clazz.setDeletedAt(LocalDateTime.now());
        classRepository.save(clazz);
    }

    public Page<Class> searchClasses(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return classRepository.findAllByDeletedAtIsNull(pageable);
        }
        return classRepository.searchActive(keyword.trim(), pageable);
    }
}
