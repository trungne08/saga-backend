package com.saga.be.repository;

import com.saga.be.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    boolean existsByCourseCode(String courseCode);

    boolean existsBySubjectId(UUID subjectId);

    boolean existsByClazzId(UUID classId);

    Optional<Course> findByCourseCode(String courseCode);
}
