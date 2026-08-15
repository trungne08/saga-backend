package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationStatus;
import com.saga.be.entity.enums.StudentInvitationType;
import com.saga.be.repository.StudentCourseInvitationRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentInvitationOutboxService {

    private final StudentCourseInvitationRepository invitationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StudentInvitationOutboxService(
            StudentCourseInvitationRepository invitationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.invitationRepository = invitationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean enqueueForCourse(Student student, Course course) {
        StudentInvitationType type = hasCognitoSubject(student)
                ? StudentInvitationType.LINKED_STUDENT
                : StudentInvitationType.FIRST_LOGIN_REQUIRED;
        if (invitationRepository.findByStudentIdAndCourseIdAndInvitationType(
                student.getId(),
                course.getId(),
                type
        ).isPresent()) {
            return false;
        }

        StudentCourseInvitation invitation = StudentCourseInvitation.builder()
                .student(student)
                .course(course)
                .invitationType(type)
                .invitationStatus(StudentInvitationStatus.PENDING)
                .attemptCount(0)
                .build();
        try {
            invitation = invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            // The database uniqueness constraint is the concurrency-safe deduplication guard.
            return false;
        }
        eventPublisher.publishEvent(new StudentInvitationQueued(invitation.getId()));
        return true;
    }

    private boolean hasCognitoSubject(Student student) {
        return student.getCognitoSub() != null && !student.getCognitoSub().isBlank();
    }
}
