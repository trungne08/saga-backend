package com.saga.be.service;

import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.Team;
import com.saga.be.entity.enums.NotificationType;
import com.saga.be.security.ApplicationRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseMembershipNotificationProducer {

    private final NotificationService notificationService;

    public void notifyMembershipAdded(Student student, Course course, Team team) {
        notificationService.create(
                student.getId(),
                ApplicationRole.STUDENT,
                NotificationType.COURSE_MEMBERSHIP_ADDED,
                "You were added to a course",
                "You were added to course " + safeName(course.getName())
                        + " in team " + safeName(team.getName()) + ".",
                null
        );
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "SAGA" : value.trim();
    }
}
