package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.repository.CourseRepository;
import com.saga.be.security.ApplicationRole;
import com.saga.be.security.SagaPrincipal;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Authorizes academic student imports for one concrete course. */
@Service
@RequiredArgsConstructor
public class CourseImportAuthorizationService {

    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public Course requireImportAccess(SagaPrincipal principal, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Course not found"
                ));

        if (principal != null
                && principal.applicationRole() == ApplicationRole.ADMIN) {
            return course;
        }

        if (principal != null
                && principal.applicationRole() == ApplicationRole.LECTURER
                && course.getInstructor() != null
                && Objects.equals(
                        principal.localProfileId(),
                        course.getInstructor().getId()
                )) {
            return course;
        }

        throw new AccessDeniedException(
                "Only the assigned Lecturer or an Admin may import students"
        );
    }
}
