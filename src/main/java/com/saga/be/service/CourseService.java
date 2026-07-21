package com.saga.be.service;

import com.saga.be.dto.request.CourseRequest;
import com.saga.be.entity.Course;
import com.saga.be.entity.Subject;
import com.saga.be.entity.Class;
import com.saga.be.entity.Semester;
import com.saga.be.entity.Lecturer;
import com.saga.be.repository.CourseRepository;
import com.saga.be.repository.SubjectRepository;
import com.saga.be.repository.ClassRepository;
import com.saga.be.repository.SemesterRepository;
import com.saga.be.repository.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRepository classRepository;
    private final SemesterRepository semesterRepository;
    private final LecturerRepository lecturerRepository;

    public List<Course> getAllCourses() {
        return courseRepository.findAll();
    }

    public Course getCourseById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Course not found"));
    }

    @Transactional
    public Course createCourse(CourseRequest request) {
        if (courseRepository.existsByCourseCode(request.getCourseCode())) {
            throw new RuntimeException("Course code already exists");
        }

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new RuntimeException("Subject not found"));
        Class clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new RuntimeException("Class not found"));
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new RuntimeException("Semester not found"));
        Lecturer instructor = lecturerRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new RuntimeException("Lecturer not found"));

        Course course = Course.builder()
                .courseCode(request.getCourseCode())
                .name(request.getName())
                .subject(subject)
                .clazz(clazz)
                .semester(semester)
                .instructor(instructor)
                .build();

        return courseRepository.save(course);
    }

    public Page<Course> getCoursesWithFilters(UUID subjectId, UUID semesterId, UUID instructorId, Pageable pageable) {
        // Lọc kết hợp Môn học và Học kỳ
        if (subjectId != null && semesterId != null) {
            return courseRepository.findBySubjectIdAndSemesterId(subjectId, semesterId, pageable);
        } 
        // Lọc theo Môn học
        else if (subjectId != null) {
            return courseRepository.findBySubjectId(subjectId, pageable);
        } 
        // Lọc theo Học kỳ
        else if (semesterId != null) {
            return courseRepository.findBySemesterId(semesterId, pageable);
        } 
        // Lọc theo Giảng viên phụ trách
        else if (instructorId != null) {
            return courseRepository.findByInstructorId(instructorId, pageable);
        }
        // Trả về tất cả nếu không có bộ lọc
        return courseRepository.findAll(pageable);
    }
}