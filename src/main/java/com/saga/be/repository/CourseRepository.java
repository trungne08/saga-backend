package com.saga.be.repository;

import com.saga.be.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    boolean existsByCourseCode(String courseCode);

    boolean existsByCourseCodeAndIdNot(String courseCode, UUID id);

    boolean existsBySubjectId(UUID subjectId);

    boolean existsByClazzId(UUID classId);

    boolean existsBySemesterId(UUID semesterId);

    Optional<Course> findByIdAndDeletedAtIsNull(UUID id);

    Page<Course> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Course> findByCourseCode(String courseCode);
}
