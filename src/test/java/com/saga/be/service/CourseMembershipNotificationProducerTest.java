package com.saga.be.service;

import static org.mockito.Mockito.verify;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.ApplicationRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseMembershipNotificationProducerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private CourseMembershipNotificationProducer producer;

    @Test
    void newGroupedCourseMembershipCreatesStudentOwnedNotification() {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        Course course = new Course();
        course.setName("Backend SAGA");
        Team team = new Team();
        team.setName("Team Alpha");

        producer.notifyMembershipAdded(student, course, team);

        verify(notificationService).create(
                student.getId(), ApplicationRole.STUDENT,
                NotificationType.COURSE_MEMBERSHIP_ADDED,
                "You were added to a course",
                "You were added to course Backend SAGA in team Team Alpha.",
                null
        );
    }
}
