package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.saga.be.config.StudentInvitationProperties;
import com.saga.be.entity.Course;
import com.saga.be.entity.Student;
import com.saga.be.entity.StudentCourseInvitation;
import com.saga.be.entity.enums.StudentInvitationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudentInvitationEmailComposerTest {

    @Test
    void unlinkedTemplateUsesConfiguredLoginUrlAndContainsNoCredentials() {
        StudentInvitationMessage message = composer().compose(invitation(
                StudentInvitationType.FIRST_LOGIN_REQUIRED
        ), List.of("Group 1"));

        assertEquals("https://frontend.example.test/login", message.loginUri().toString());
        assertTrue(message.body().contains("register with this same email address"));
        assertFalse(message.body().toLowerCase().contains("password"));
        assertFalse(message.body().toLowerCase().contains("token"));
        assertFalse(message.body().contains("JSESSIONID"));
        assertFalse(message.body().contains("CSRF"));
    }

    @Test
    void linkedTemplateAsksStudentToSignIn() {
        StudentInvitationMessage message = composer().compose(invitation(
                StudentInvitationType.LINKED_STUDENT
        ), List.of());

        assertTrue(message.body().contains("Please sign in to SAGA"));
    }

    private StudentInvitationEmailComposer composer() {
        StudentInvitationProperties properties = new StudentInvitationProperties();
        properties.setLoginUrl("https://frontend.example.test/login");
        return new StudentInvitationEmailComposer(properties);
    }

    private StudentCourseInvitation invitation(StudentInvitationType type) {
        Course course = Course.builder().name("Secure Course").build();
        Student student = Student.builder().email("student@example.test").build();
        return StudentCourseInvitation.builder()
                .course(course)
                .student(student)
                .invitationType(type)
                .build();
    }
}
