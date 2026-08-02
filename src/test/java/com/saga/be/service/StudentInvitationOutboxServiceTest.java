package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationType;
import com.saga.be.repository.StudentCourseInvitationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StudentInvitationOutboxServiceTest {

    @Mock
    private StudentCourseInvitationRepository invitationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void queuesFirstLoginInvitationForUnlinkedStudent() {
        Student student = student(null);
        Course course = course();
        when(invitationRepository.findByStudentIdAndCourseIdAndInvitationType(
                student.getId(), course.getId(), StudentInvitationType.FIRST_LOGIN_REQUIRED
        )).thenReturn(Optional.empty());
        when(invitationRepository.saveAndFlush(any(StudentCourseInvitation.class)))
                .thenAnswer(invocation -> {
                    StudentCourseInvitation invitation = invocation.getArgument(0);
                    invitation.setId(UUID.randomUUID());
                    return invitation;
                });

        service().enqueueForCourse(student, course);

        ArgumentCaptor<StudentCourseInvitation> captor = ArgumentCaptor.forClass(
                StudentCourseInvitation.class
        );
        verify(invitationRepository).saveAndFlush(captor.capture());
        assertEquals(StudentInvitationType.FIRST_LOGIN_REQUIRED,
                captor.getValue().getInvitationType());
        verify(eventPublisher).publishEvent(any(StudentInvitationQueued.class));
    }

    @Test
    void queuesLinkedTemplateForStudentAlreadyBoundToCognito() {
        Student student = student("linked-subject");
        Course course = course();
        when(invitationRepository.findByStudentIdAndCourseIdAndInvitationType(
                student.getId(), course.getId(), StudentInvitationType.LINKED_STUDENT
        )).thenReturn(Optional.empty());
        when(invitationRepository.saveAndFlush(any(StudentCourseInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service().enqueueForCourse(student, course);

        ArgumentCaptor<StudentCourseInvitation> captor = ArgumentCaptor.forClass(
                StudentCourseInvitation.class
        );
        verify(invitationRepository).saveAndFlush(captor.capture());
        assertEquals(StudentInvitationType.LINKED_STUDENT, captor.getValue().getInvitationType());
    }

    @Test
    void doesNotQueueDuplicateInvitation() {
        Student student = student(null);
        Course course = course();
        when(invitationRepository.findByStudentIdAndCourseIdAndInvitationType(
                student.getId(), course.getId(), StudentInvitationType.FIRST_LOGIN_REQUIRED
        )).thenReturn(Optional.of(StudentCourseInvitation.builder().build()));

        service().enqueueForCourse(student, course);

        verify(invitationRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private StudentInvitationOutboxService service() {
        return new StudentInvitationOutboxService(invitationRepository, eventPublisher);
    }

    private Student student(String cognitoSub) {
        Student student = Student.builder().cognitoSub(cognitoSub).build();
        student.setId(UUID.randomUUID());
        return student;
    }

    private Course course() {
        Course course = Course.builder().build();
        course.setId(UUID.randomUUID());
        return course;
    }
}
