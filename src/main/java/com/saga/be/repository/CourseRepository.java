package com.saga.be.repository;

import com.saga.be.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {
    boolean existsByCourseCode(String courseCode);

    // Tìm kiếm khóa học theo Môn học có phân trang
    Page<Course> findBySubjectId(UUID subjectId, Pageable pageable);

    Page<Course> findBySemesterId(UUID semesterId, Pageable pageable);

    Page<Course> findByInstructorId(UUID instructorId, Pageable pageable);

    Page<Course> findBySubjectIdAndSemesterId(UUID subjectId, UUID semesterId, Pageable pageable);
}