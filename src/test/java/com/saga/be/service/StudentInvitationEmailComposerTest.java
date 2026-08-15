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
        assertTrue(message.body().contains("chưa được kích hoạt hoặc liên kết"));
        assertTrue(message.body().contains("chính xác địa chỉ email đã nhận thư này"));
        assertTrue(message.body().contains("Trong lần đăng nhập đầu tiên"));
        assertTrue(message.body().contains("Đăng ký / Kích hoạt tài khoản SAGA"));
        assertTrue(message.htmlBody().contains(
                "href=\"https://frontend.example.test/login\""
        ));
        assertTrue(message.htmlBody().contains("Group 1"));
        assertSafeContent(message);
    }

    @Test
    void linkedTemplateAsksStudentToSignIn() {
        StudentInvitationMessage message = composer().compose(invitation(
                StudentInvitationType.LINKED_STUDENT
        ), List.of("Nhóm Alpha"));

        assertEquals("student@example.test", message.recipientEmail());
        assertTrue(message.body().contains("Hồ sơ SAGA của bạn đã được liên kết"));
        assertTrue(message.body().contains("Nhóm Alpha"));
        assertTrue(message.body().contains("Đăng nhập SAGA"));
        assertFalse(message.body().contains("Đăng ký / Kích hoạt"));
        assertSafeContent(message);
    }

    @Test
    void htmlTemplateEscapesCourseAndTeamNames() {
        StudentCourseInvitation invitation = invitation(StudentInvitationType.LINKED_STUDENT);
        invitation.getCourse().setName("Course <unsafe>");

        StudentInvitationMessage message = composer().compose(
                invitation,
                List.of("Team <unsafe>")
        );

        assertTrue(message.htmlBody().contains("Course &lt;unsafe&gt;"));
        assertTrue(message.htmlBody().contains("Team &lt;unsafe&gt;"));
        assertFalse(message.htmlBody().contains("Course <unsafe>"));
    }

    private void assertSafeContent(StudentInvitationMessage message) {
        String content = (message.body() + message.htmlBody()).toLowerCase();
        assertFalse(content.contains("password"));
        assertFalse(content.contains("token"));
        assertFalse(content.contains("jsessionid"));
        assertFalse(content.contains("csrf"));
        assertFalse(content.contains("cognitosub"));
        assertFalse(content.contains("google"));
        assertFalse(content.matches(
                ".*[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}.*"
        ));
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
