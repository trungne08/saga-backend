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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final SubjectRepository subjectRepository;
    private final ClassRepository classRepository;
    private final SemesterRepository semesterRepository;
    private final LecturerRepository lecturerRepository;

    public Course getCourseById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    @Transactional
    public Course createCourse(CourseRequest request) {
        String courseCode = request.getCourseCode().trim();
        if (courseRepository.existsByCourseCode(courseCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Course code already exists");
        }

        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        Class clazz = classRepository.findById(request.getClassId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
        Lecturer instructor = lecturerRepository.findById(request.getInstructorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lecturer not found"));

        Course course = Course.builder()
                .courseCode(courseCode)
                .name(request.getName().trim())
                .subject(subject)
                .clazz(clazz)
                .semester(semester)
                .instructor(instructor)
                .build();

        return courseRepository.save(course);
    }

    public Page<Course> getCoursesWithFilters(UUID subjectId, UUID semesterId, UUID instructorId, Pageable pageable) {
        Specification<Course> specification = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (subjectId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("subject").get("id"), subjectId));
        }
        if (semesterId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("semester").get("id"), semesterId));
        }
        if (instructorId != null) {
            specification = specification.and((root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("instructor").get("id"), instructorId));
        }

        return courseRepository.findAll(specification, pageable);
    }
}
