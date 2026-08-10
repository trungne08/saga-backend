package com.saga.be.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saga.be.entity.enums.StudentInvitationType;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class GmailSmtpStudentInvitationDeliveryAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage mimeMessage;
    private GmailSmtpStudentInvitationDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        adapter = new GmailSmtpStudentInvitationDeliveryAdapter(
                mailSender,
                "sender@example.test"
        );
    }

    @Test
    void sendsToRecipientFromConfiguredUsernameWithTextAndHtml() throws Exception {
        StudentInvitationMessage message = message();

        adapter.deliver(message);

        verify(mailSender).send(mimeMessage);
        assertEquals("sender@example.test", mimeMessage.getFrom()[0].toString());
        assertEquals("student@example.test", mimeMessage.getAllRecipients()[0].toString());
        assertEquals("Course invitation", mimeMessage.getSubject());
        String content = content(mimeMessage);
        assertTrue(content.contains("Plain invitation body"));
        assertTrue(content.contains("Đăng nhập SAGA"));
    }

    @Test
    void propagatesSanitizedProviderFailure() {
        doThrow(new MailAuthenticationException("sensitive provider detail"))
                .when(mailSender).send(mimeMessage);

        StudentInvitationDeliveryException exception = assertThrows(
                StudentInvitationDeliveryException.class,
                () -> adapter.deliver(message())
        );

        assertEquals("AUTHENTICATION", exception.getCategory());
        assertEquals("MailAuthenticationException", exception.getProviderExceptionClass());
        assertFalse(exception.getMessage().contains("sensitive provider detail"));
    }

    private StudentInvitationMessage message() {
        return new StudentInvitationMessage(
                "student@example.test",
                "Course invitation",
                "Plain invitation body",
                "<html><body><a href=\"https://frontend.example.test/login\">"
                        + "Đăng nhập SAGA</a></body></html>",
                StudentInvitationType.LINKED_STUDENT,
                "Course",
                List.of(),
                URI.create("https://frontend.example.test/login"),
                2
        );
    }

    private String content(Part part) throws Exception {
        Object value = part.getContent();
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Multipart multipart) {
            StringBuilder content = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                content.append(content(multipart.getBodyPart(index)));
            }
            return content.toString();
        }
        return "";
    }
}
